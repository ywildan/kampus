package id.personal.campus.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.os.SystemClock
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import id.personal.campus.R
import id.personal.campus.data.SettingsEntity
import id.personal.campus.reminder.ScheduleIntents
import id.personal.campus.reminder.refreshScheduleAsync
import id.personal.campus.schedule.Occurrence
import id.personal.campus.schedule.ScheduleMode
import id.personal.campus.schedule.ScheduleSnapshot
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class CampusWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        refreshScheduleAsync(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        refreshScheduleAsync(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        refreshScheduleAsync(context)
    }

    override fun onDisabled(context: Context) {
        // The coordinator sees no remaining IDs and cancels the one boundary alarm.
        refreshScheduleAsync(context)
    }

    companion object {
        /** Returns whether any widgets exist; only ScheduleCoordinator owns alarm reconciliation. */
        internal fun updateAll(context: Context, state: ScheduleSnapshot, settings: SettingsEntity): Boolean {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, CampusWidgetProvider::class.java))
            for (id in ids) {
                val options = manager.getAppWidgetOptions(id)
                val minWidth = options.positiveInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
                val maxWidth = options.positiveInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, minWidth)
                val minHeight = options.positiveInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)
                val maxHeight = options.positiveInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, minHeight)
                // These two native RemoteViews configurations work on API 26 too. Portrait uses
                // minWidth/maxHeight; landscape uses maxWidth/minHeight, not a guessed cell count.
                val portrait = WidgetRenderer.render(context, state, settings, minWidth, maxHeight)
                val landscape = WidgetRenderer.render(context, state, settings, maxWidth, minHeight)
                manager.updateAppWidget(id, RemoteViews(landscape, portrait))
            }
            return ids.isNotEmpty()
        }

        private fun Bundle.positiveInt(key: String, fallback: Int): Int = getInt(key, fallback)
            .takeIf { it > 0 } ?: fallback
    }
}

class WidgetBoundaryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ScheduleIntents.WIDGET_BOUNDARY_ACTION) refreshScheduleAsync(context)
    }
}

/**
 * What the widget's Chronometer should count towards. The launcher ticks it every second, so the
 * widget only recomputes this when it actually repaints (boundaries, midnight, edits, boot).
 */
internal data class WidgetCountdown(
    val target: ZonedDateTime,
    val remainingMillis: Long,
    val active: Boolean,
) {
    val label: String get() = if (active) "SISA" else "MULAI DALAM"
}

/** Upcoming classes count down to their start, a class in progress to its end. */
internal fun widgetCountdown(
    state: ScheduleSnapshot,
    nowMillis: Long = System.currentTimeMillis(),
): WidgetCountdown? {
    val primary = state.primary ?: return null
    val active = state.active.isNotEmpty()
    val target = if (active) primary.end else primary.start
    val remaining = target.toInstant().toEpochMilli() - nowMillis
    if (remaining !in 1..COUNTDOWN_MAX_MILLIS) return null
    return WidgetCountdown(target, remaining, active)
}

/** A day is as far as a second-by-second timer still reads as "berapa lama lagi". */
private const val COUNTDOWN_MAX_MILLIS = 24L * 60 * 60 * 1000

/** Presentation only: occurrences, overlap ordering and semester boundaries come from the resolver. */
private object WidgetRenderer {
    private val locale = Locale.forLanguageTag("id-ID")
    private val dateFormat = DateTimeFormatter.ofPattern("EEEE, d MMM yyyy", locale)
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm", locale)
    private val regular = Typeface.create("sans-serif", Typeface.NORMAL)
    private val medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val bold = Typeface.create("sans-serif", Typeface.BOLD)
    private val monoBold = Typeface.create("monospace", Typeface.BOLD)

    /** Small caption above the room code; kept in sync with campus_widget.xml. */
    private const val ROOM_LABEL = "RUANGAN"

    // Badge padding mirrors the 8dp/5dp padding in campus_widget.xml so measurement matches reality.
    private const val BADGE_PAD_H = 8f
    private const val BADGE_PAD_V = 5f
    private const val COLUMN_GAP = 8f
    private const val ROOM_LABEL_SIZE = 8.5f
    private const val NEXT_ROOM_LABEL_SIZE = 8f
    private const val NEXT_TIME_SIZE = 16f
    private const val ROW_COURSE_SIZE = 12f
    // Mirrors the 8.5sp label, 12sp clock and 6dp gap in campus_widget.xml.
    private const val COUNTDOWN_LABEL_SIZE = 8.5f
    private const val COUNTDOWN_SIZE = 12f
    private const val COUNTDOWN_GAP = 6f

    /** Divider margins (8dp + 6dp) plus the 1dp rule that separates one row from the next. */
    private const val ROW_TOP_SPACE = 15f

    /** One remaining class of the day; measured once here, then handed to RemoteViews as is. */
    private data class Row(
        val occurrence: Occurrence,
        val time: String,
        val course: String,
        val room: String,
        val timeSize: Float,
        val roomSize: Float,
        val height: Float,
    )

    fun render(
        context: Context,
        state: ScheduleSnapshot,
        settings: SettingsEntity,
        widthDp: Int,
        heightDp: Int,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.campus_widget)
        val primary = state.primary
        val preview = state.mode == ScheduleMode.PREVIEW
        val status = when (state.mode) {
            ScheduleMode.PREVIEW -> "PRATINJAU"
            ScheduleMode.EMPTY -> "JADWAL KOSONG"
            ScheduleMode.FINISHED -> "SEMESTER SELESAI"
            ScheduleMode.SCHEDULED -> when {
                state.active.isNotEmpty() -> "BERLANGSUNG" +
                    if (state.active.size > 1) " +${state.active.size - 1} bentrok" else ""
                primary != null -> "BERIKUTNYA"
                else -> "TIDAK ADA KULIAH BERIKUTNYA"
            }
        }
        val heading = if (primary != null) "$status · ${primary.start.format(dateFormat)}" else status
        val time = primary?.let(::timeRange) ?: when (state.mode) {
            ScheduleMode.FINISHED -> "Semester selesai"
            ScheduleMode.EMPTY -> "Belum ada jadwal"
            else -> "Tidak ada kuliah"
        }
        val room = primary?.course?.room ?: when (state.mode) {
            ScheduleMode.FINISHED -> "Atur semester baru"
            ScheduleMode.EMPTY -> "Tambah kuliah"
            else -> "dalam semester ini"
        }
        val course = primary?.course?.name ?: "Ketuk untuk membuka pengaturan"
        val metadata = primary?.course?.let {
            listOfNotNull(
                it.section.takeIf(String::isNotBlank)?.let { section -> "Kelas $section" },
                it.roomNote.takeIf(String::isNotBlank),
            ).joinToString(" · ")
        }.orEmpty()
        val hint = if (preview) "Atur semester untuk mengaktifkan jadwal" else ""
        // In a gap primary IS the first upcoming occurrence: do not repeat it as the next lesson.
        // Every other class of that same day gets its own row, so a three-class Wednesday keeps
        // its last lesson instead of being cut off after the first one.
        val secondary = primary?.let { main ->
            state.upcoming.filter { it.key != main.key && it.start.toLocalDate() == main.start.toLocalDate() }
        }.orEmpty()
        val dataTime = "Data ${state.now.format(timeFormat)}"
        val zone = zoneLabel(settings, state)
        val countdown = widgetCountdown(state)

        // Measure the real font-scaled text, not just width or a hard-coded 4×2/4×3 threshold.
        // Compact mode sacrifices optional details before shrinking the prominent time and room.
        val padding = if (heightDp < 160) 6 else 10
        val metrics = context.resources.displayMetrics
        val contentWidth = (widthDp - padding * 2).coerceAtLeast(1)
        val budget = (heightDp - padding * 2).coerceAtLeast(1).toFloat()
        val measure = TextMeasure(context, contentWidth)

        val hasBadge = primary != null
        var smallSize = 11f
        var courseSize = 14f
        var footerSize = 10f
        var timeSize = if (hasBadge) 22f else 20f
        var roomSize = if (hasBadge) 30f else 20f
        var showCourse = true

        // Sizing priority: the room badge keeps the biggest type it can and the time takes what is
        // left. The badge never grows past the point where the time would have to wrap, so a narrow
        // widget shrinks both instead of pushing a line outside the card.
        val minTimeSize = 13f
        // A bigger card gives the extra width to the room code, not to the time.
        if (hasBadge) roomSize = (30f + (contentWidth - 230f) * 0.10f).coerceIn(30f, 44f)

        /** Widest the badge may get before the time on a single line would no longer fit. */
        fun badgeCeiling(timeText: String, face: Typeface, share: Float): Float {
            val timeFloor = measure.width(timeText, minTimeSize, face) * 1.05f
            return (contentWidth - timeFloor - COLUMN_GAP)
                .coerceIn(contentWidth * 0.28f, contentWidth * share)
        }

        fun badgeWidth(text: String, size: Float, labelSize: Float, ceiling: Float): Float = minOf(
            maxOf(measure.width(text, size, bold), measure.width(ROOM_LABEL, labelSize, medium)) +
                BADGE_PAD_H * 2,
            ceiling,
        )

        val primaryCeiling = badgeCeiling(time, monoBold, 0.62f)

        fun primaryBadgeWidth(): Float = badgeWidth(room, roomSize, ROOM_LABEL_SIZE, primaryCeiling)

        // Shrink the code only as far as the width demands, never below a readable floor.
        if (hasBadge) {
            while (roomSize > 15f && measure.width(room, roomSize, bold) + BADGE_PAD_H * 2 > primaryCeiling) {
                roomSize -= 0.5f
            }
        }

        fun leftWidth(): Float = if (hasBadge) {
            (contentWidth - primaryBadgeWidth() - COLUMN_GAP).coerceAtLeast(contentWidth * 0.40f)
        } else {
            contentWidth.toFloat()
        }

        fun countdownWidth(target: WidgetCountdown): Float =
            measure.width(target.label, COUNTDOWN_LABEL_SIZE, medium) + COUNTDOWN_GAP +
                measure.width("00:00:00", COUNTDOWN_SIZE, monoBold)

        // The timer shares the footer with the zone and live clock, so it only appears when it
        // still fits next to them at the smallest footer type.
        var showCountdown = countdown?.let {
            countdownWidth(it) + measure.width(zone, 7f, regular) + 6 +
                measure.width("00:00:00", 8f, Typeface.MONOSPACE) <= contentWidth
        } ?: false

        // The room code is the hero: the time is fitted to the narrower text column instead.
        timeSize = measure.fitWidth(time, timeSize, 13f, monoBold, leftWidth())

        fun footerLeftWidth(): Float = if (showCountdown && countdown != null) {
            countdownWidth(countdown)
        } else {
            measure.width(dataTime, footerSize, regular)
        }

        fun footerWidth(): Float = footerLeftWidth() +
            measure.width(zone, footerSize, regular) + 6 +
            measure.width("00:00:00", footerSize + 1, Typeface.MONOSPACE)
        while (footerWidth() > contentWidth && footerSize > 7f) footerSize -= 0.5f

        fun footerHeight(): Float {
            val rightWidth = measure.width(zone, footerSize, regular) + 6 +
                measure.width("00:00:00", footerSize + 1, Typeface.MONOSPACE)
            val leftHeight = if (showCountdown && countdown != null) {
                measure.height("00:00:00", COUNTDOWN_SIZE, monoBold, oneLine = true)
            } else {
                measure.height(dataTime, footerSize, regular, availableWidthDp = contentWidth - rightWidth)
            }
            return maxOf(
                leftHeight,
                measure.height(zone, footerSize, regular),
                measure.height("00:00:00", footerSize + 1, Typeface.MONOSPACE),
            )
        }

        fun badgeHeight(text: String, size: Float, labelSize: Float, width: Float): Float =
            BADGE_PAD_V * 2 + measure.height(ROOM_LABEL, labelSize, medium) + 2 +
                measure.height(text, size, bold, availableWidthDp = (width - BADGE_PAD_H * 2).coerceAtLeast(1f))

        fun primaryHeight(): Float {
            val width = leftWidth()
            val column = measure.height(heading, smallSize, medium, availableWidthDp = width) + 4 +
                measure.height(time, timeSize, monoBold, availableWidthDp = width) + 1 +
                (if (!hasBadge) measure.height(room, roomSize, bold, availableWidthDp = width) else 0f) +
                (if (showCourse) {
                    3 + measure.height(course, courseSize, regular, oneLine = true, availableWidthDp = width)
                } else 0f) +
                (if (hint.isNotEmpty()) 3 + measure.height(hint, smallSize, regular, availableWidthDp = width) else 0f)
            if (!hasBadge) return column
            val badge = badgeHeight(room, roomSize, ROOM_LABEL_SIZE, primaryBadgeWidth())
            return maxOf(column, badge)
        }

        fun baseHeight(): Float = primaryHeight() + 6 + footerHeight()

        // The countdown is the first detail dropped: a small card keeps its time, room and course.
        if (baseHeight() > budget) showCountdown = false
        // Trim the secondary text first; the room badge keeps its size as long as possible.
        while (baseHeight() > budget && timeSize > 12f) timeSize -= 1f
        if (baseHeight() > budget) {
            smallSize = 10f
            courseSize = 12f
            footerSize = minOf(footerSize, 9f)
        }
        if (baseHeight() > budget) showCourse = false
        while (baseHeight() > budget && roomSize > 16f) roomSize -= 1f
        // At very large accessibility fonts, keep core text unellipsized and use the smallest
        // readable core size. Enlarging the widget restores all supplementary details.
        while (baseHeight() > budget && timeSize > 10f) timeSize -= 1f

        val metaHeight = if (metadata.isEmpty()) 0f else {
            2 + measure.height(metadata, smallSize, regular, availableWidthDp = leftWidth())
        }

        // One row per remaining class of the day, measured with the same rule as the primary row:
        // the room badge keeps the biggest readable code, the time takes the width that is left.
        val rowRoomSize = (22f + (contentWidth - 230f) * 0.06f).coerceIn(22f, 32f)
        val rows = secondary.map { occurrence ->
            val rowRoom = occurrence.course.room.orEmpty()
            val rowTime = timeRange(occurrence)
            val ceiling = badgeCeiling(rowTime, bold, 0.55f)
            var size = rowRoomSize
            if (rowRoom.isNotEmpty()) {
                while (size > 13f && measure.width(rowRoom, size, bold) + BADGE_PAD_H * 2 > ceiling) size -= 0.5f
            }
            val badge = badgeWidth(rowRoom, size, NEXT_ROOM_LABEL_SIZE, ceiling)
            val column = if (rowRoom.isEmpty()) {
                contentWidth.toFloat()
            } else {
                (contentWidth - badge - COLUMN_GAP).coerceAtLeast(contentWidth * 0.40f)
            }
            val fitted = measure.fitWidth(rowTime, NEXT_TIME_SIZE, 12f, bold, column)
            val height = maxOf(
                measure.height(rowTime, fitted, bold, availableWidthDp = column) + 2 +
                    measure.height(
                        occurrence.course.name,
                        ROW_COURSE_SIZE,
                        regular,
                        oneLine = true,
                        availableWidthDp = column,
                    ),
                if (rowRoom.isEmpty()) 0f else badgeHeight(rowRoom, size, NEXT_ROOM_LABEL_SIZE, badge),
            )
            Row(occurrence, rowTime, occurrence.course.name, rowRoom, fitted, size, height)
        }
        // A typical 4×3 may offer only 180dp. Trim the primary type once so at least one remaining
        // class stays visible; restore the large type when even that cannot buy a row.
        if (rows.isNotEmpty() && primary != null && heightDp >= 180 && showCourse) {
            val originalTimeSize = timeSize
            val originalRoomSize = roomSize
            while (baseHeight() + ROW_TOP_SPACE + rows.first().height > budget &&
                (timeSize > 20f || roomSize > 20f)
            ) {
                if (timeSize > 20f) timeSize -= 1f
                if (roomSize > 20f) roomSize -= 1f
            }
            if (baseHeight() + ROW_TOP_SPACE + rows.first().height > budget) {
                timeSize = originalTimeSize
                roomSize = originalRoomSize
            }
        }
        // Add rows in schedule order and stop at the first one that no longer fits, so the list
        // never shows a later class while hiding an earlier one.
        var usedHeight = baseHeight()
        val shownRows = rows.takeWhile { row ->
            val fits = usedHeight + ROW_TOP_SPACE + row.height <= budget
            if (fits) usedHeight += ROW_TOP_SPACE + row.height
            fits
        }
        val showMetadata = metadata.isNotEmpty() && usedHeight + metaHeight <= budget
        val hiddenRows = rows.size - shownRows.size
        val moreLabel = when {
            hiddenRows > 0 -> "+$hiddenRows jadwal lagi hari ini"
            rows.isEmpty() && primary != null -> "Tidak ada kuliah lagi hari ini"
            else -> ""
        }
        val showMore = moreLabel.isNotEmpty() &&
            usedHeight + (if (showMetadata) metaHeight else 0f) + 4 +
                measure.height(moreLabel, smallSize, regular, oneLine = true) <= budget

        val pxPadding = (padding * metrics.density).toInt()
        views.setViewPadding(android.R.id.background, pxPadding, pxPadding, pxPadding, pxPadding)
        views.text(R.id.widget_status, heading, smallSize)
        views.text(R.id.widget_time, time, timeSize)
        views.text(R.id.widget_room, room, roomSize)
        views.text(R.id.widget_room_label, ROOM_LABEL, ROOM_LABEL_SIZE)
        views.text(R.id.widget_room_plain, room, roomSize)
        views.text(R.id.widget_course, course, courseSize)
        views.text(R.id.widget_metadata, metadata, smallSize)
        views.text(R.id.widget_hint, hint, smallSize)
        views.text(R.id.widget_data_time, dataTime, footerSize)
        views.text(R.id.widget_zone, zone, footerSize)
        views.text(R.id.widget_countdown_label, countdown?.label.orEmpty(), COUNTDOWN_LABEL_SIZE)
        views.setTextViewTextSize(R.id.widget_countdown_clock, TypedValue.COMPLEX_UNIT_SP, COUNTDOWN_SIZE)
        views.setTextViewTextSize(R.id.widget_clock, TypedValue.COMPLEX_UNIT_SP, footerSize + 1)
        views.setString(R.id.widget_clock, "setTimeZone", settings.zoneId)
        views.visible(R.id.widget_room_badge, hasBadge)
        views.visible(R.id.widget_room_plain, !hasBadge)
        views.visible(R.id.widget_course, showCourse)
        views.visible(R.id.widget_metadata, showMetadata)
        views.visible(R.id.widget_hint, hint.isNotEmpty())
        views.visible(R.id.widget_data_time, !showCountdown)
        views.visible(R.id.widget_countdown, showCountdown)
        // Chronometer base is on the elapsedRealtime timebase and the launcher drives the tick, so
        // no per-minute alarms are needed; the next widget repaint simply rebases it.
        if (showCountdown && countdown != null) {
            views.setChronometerCountDown(R.id.widget_countdown_clock, true)
            views.setChronometer(
                R.id.widget_countdown_clock,
                SystemClock.elapsedRealtime() + countdown.remainingMillis,
                null,
                true,
            )
            views.setContentDescription(R.id.widget_countdown, countdownDescription(countdown))
        }
        // RemoteViews cannot diff children, so the list is dropped and rebuilt on every update.
        views.removeAllViews(R.id.widget_rows)
        views.visible(R.id.widget_rows, shownRows.isNotEmpty())
        views.text(R.id.widget_more, moreLabel, smallSize)
        views.visible(R.id.widget_more, showMore)
        views.setContentDescription(R.id.widget_time, if (primary != null) "Jam kuliah $time" else time)
        views.setContentDescription(R.id.widget_room_badge, if (primary != null) "Ruang $room" else room)
        views.setContentDescription(R.id.widget_room_plain, room)
        views.setContentDescription(R.id.widget_data_time, "Data diperbarui ${state.now.format(timeFormat)}")
        val openMain = if (primary != null && !preview) {
            ScheduleIntents.openCourse(context, primary.course.id)
        } else ScheduleIntents.openSettings(context)
        views.setOnClickPendingIntent(android.R.id.background, openMain)
        shownRows.forEach { row -> views.addView(R.id.widget_rows, rowViews(context, row, preview, openMain)) }
        return views
    }

    /** One list row: the room code keeps its own badge, mirroring the primary card. */
    private fun rowViews(
        context: Context,
        row: Row,
        preview: Boolean,
        fallback: PendingIntent,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.campus_widget_secondary_row)
        views.text(R.id.widget_row_time, row.time, row.timeSize)
        views.text(R.id.widget_row_course, row.course, ROW_COURSE_SIZE)
        views.text(R.id.widget_row_room, row.room, row.roomSize)
        views.text(R.id.widget_row_room_label, ROOM_LABEL, NEXT_ROOM_LABEL_SIZE)
        views.visible(R.id.widget_row_room_badge, row.room.isNotEmpty())
        views.setContentDescription(
            R.id.widget_row_root,
            listOfNotNull(row.time, row.course, row.room.takeIf(String::isNotBlank)?.let { "ruang $it" })
                .joinToString(" · "),
        )
        if (row.room.isNotEmpty()) {
            views.setContentDescription(R.id.widget_row_room_badge, "Ruang ${row.room}")
        }
        views.setOnClickPendingIntent(
            R.id.widget_row_root,
            if (preview) fallback else ScheduleIntents.openCourse(context, row.occurrence.course.id),
        )
        return views
    }

    private fun timeRange(occurrence: Occurrence) =
        "${occurrence.start.format(timeFormat)}–${occurrence.end.format(timeFormat)}"

    /** The ticking view is unreadable to TalkBack, so spell the remaining time out once. */
    private fun countdownDescription(countdown: WidgetCountdown): String {
        val minutes = (countdown.remainingMillis / 60_000).coerceAtLeast(1)
        val hours = minutes / 60
        val rest = minutes % 60
        val duration = when {
            hours > 0 && rest > 0 -> "$hours jam $rest menit"
            hours > 0 -> "$hours jam"
            else -> "$minutes menit"
        }
        return if (countdown.active) "Sisa $duration" else "Mulai dalam $duration"
    }

    private fun zoneLabel(settings: SettingsEntity, state: ScheduleSnapshot): String = when (settings.zoneId) {
        "Asia/Jakarta", "Asia/Pontianak" -> "WIB"
        "Asia/Makassar", "Asia/Ujung_Pandang" -> "WITA"
        "Asia/Jayapura" -> "WIT"
        // Never relabel an arbitrary configured zone as WIB just because the device is in Indonesia.
        else -> if (state.now.offset.totalSeconds == 0) "UTC" else "UTC${state.now.offset.id}"
    }

    private fun RemoteViews.text(id: Int, value: String, size: Float) {
        setTextViewText(id, value)
        setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_SP, size)
    }

    private fun RemoteViews.visible(id: Int, visible: Boolean) {
        setViewVisibility(id, if (visible) View.VISIBLE else View.GONE)
    }

    private class TextMeasure(context: Context, widthDp: Int) {
        private val metrics = context.resources.displayMetrics
        private val widthPx = (widthDp * metrics.density).toInt().coerceAtLeast(1)

        private fun paint(size: Float, face: Typeface) = TextPaint().apply {
            isAntiAlias = true
            typeface = face
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, size, metrics)
        }

        fun width(text: String, size: Float, face: Typeface): Float =
            paint(size, face).measureText(text) / metrics.density

        fun height(
            text: String,
            size: Float,
            face: Typeface,
            oneLine: Boolean = false,
            availableWidthDp: Float? = null,
        ): Float {
            val availableWidth = availableWidthDp?.let { (it * metrics.density).toInt().coerceAtLeast(1) } ?: widthPx
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint(size, face), availableWidth)
                .setIncludePad(false)
                .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
                .setMaxLines(if (oneLine) 1 else Int.MAX_VALUE)
                .build()
            // A small margin covers font/launcher rounding without assuming the user's font scale.
            return layout.height / metrics.density + 1f
        }

        fun fitWidth(
            text: String,
            preferred: Float,
            minimum: Float,
            face: Typeface,
            availableWidthDp: Float? = null,
        ): Float {
            val limitPx = availableWidthDp?.let { (it * metrics.density).toInt().coerceAtLeast(1) } ?: widthPx
            var size = preferred
            while (size > minimum && paint(size, face).measureText(text) > limitPx) size -= 1f
            return size.coerceAtLeast(minimum)
        }
    }
}
