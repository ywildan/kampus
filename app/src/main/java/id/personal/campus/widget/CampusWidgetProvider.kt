package id.personal.campus.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
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
        val next = state.upcoming.firstOrNull { it.key != primary?.key }
        val nextLabel = if (next != null) {
            "${if (preview) "PRATINJAU BERIKUTNYA" else "BERIKUTNYA"} · ${next.start.format(dateFormat)}"
        } else "Tidak ada kuliah berikutnya dalam semester"
        // Room lives in its own right-hand badge, so it is no longer glued to the time string.
        val nextTime = next?.let(::timeRange).orEmpty()
        val nextRoom = next?.course?.room.orEmpty()
        val nextCourse = next?.course?.name.orEmpty()
        val dataTime = "Data ${state.now.format(timeFormat)}"
        val zone = zoneLabel(settings, state)

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
        var nextRoomSize = 22f
        var showCourse = true

        // Sizing priority: the room badge keeps the biggest type it can and the time takes what is
        // left. The badge never grows past the point where the time would have to wrap, so a narrow
        // widget shrinks both instead of pushing a line outside the card.
        val minTimeSize = 13f
        // A bigger card gives the extra width to the room code, not to the time.
        if (hasBadge) roomSize = (30f + (contentWidth - 230f) * 0.10f).coerceIn(30f, 44f)
        if (nextRoom.isNotEmpty()) nextRoomSize = (22f + (contentWidth - 230f) * 0.06f).coerceIn(22f, 32f)

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
        val nextCeiling = badgeCeiling(nextTime, bold, 0.55f)

        fun primaryBadgeWidth(): Float = badgeWidth(room, roomSize, ROOM_LABEL_SIZE, primaryCeiling)
        fun nextBadgeWidth(): Float = badgeWidth(nextRoom, nextRoomSize, NEXT_ROOM_LABEL_SIZE, nextCeiling)

        // Shrink the code only as far as the width demands, never below a readable floor.
        if (hasBadge) {
            while (roomSize > 15f && measure.width(room, roomSize, bold) + BADGE_PAD_H * 2 > primaryCeiling) {
                roomSize -= 0.5f
            }
        }
        while (nextRoomSize > 13f && measure.width(nextRoom, nextRoomSize, bold) + BADGE_PAD_H * 2 > nextCeiling) {
            nextRoomSize -= 0.5f
        }

        fun leftWidth(): Float = if (hasBadge) {
            (contentWidth - primaryBadgeWidth() - COLUMN_GAP).coerceAtLeast(contentWidth * 0.40f)
        } else {
            contentWidth.toFloat()
        }

        fun nextLeftWidth(): Float = if (nextRoom.isEmpty()) {
            contentWidth.toFloat()
        } else {
            (contentWidth - nextBadgeWidth() - COLUMN_GAP).coerceAtLeast(contentWidth * 0.40f)
        }

        // The room code is the hero: the time is fitted to the narrower text column instead.
        timeSize = measure.fitWidth(time, timeSize, 13f, monoBold, leftWidth())

        fun footerWidth(): Float = measure.width(dataTime, footerSize, regular) +
            measure.width(zone, footerSize, regular) + 6 +
            measure.width("00:00:00", footerSize + 1, Typeface.MONOSPACE)
        while (footerWidth() > contentWidth && footerSize > 7f) footerSize -= 0.5f

        fun footerHeight(): Float {
            val dataWidth = contentWidth - measure.width(zone, footerSize, regular) - 6 -
                measure.width("00:00:00", footerSize + 1, Typeface.MONOSPACE)
            return maxOf(
                measure.height(dataTime, footerSize, regular, availableWidthDp = dataWidth),
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

        fun nextRowHeight(): Float {
            if (next == null) return measure.height(nextLabel, smallSize, medium, availableWidthDp = leftWidth())
            val width = nextLeftWidth()
            val column = measure.height(nextLabel, smallSize, medium, availableWidthDp = width) + 2 +
                measure.height(nextTime, NEXT_TIME_SIZE, bold, availableWidthDp = width)
            val badge = badgeHeight(nextRoom, nextRoomSize, NEXT_ROOM_LABEL_SIZE, nextBadgeWidth())
            return maxOf(column, badge)
        }

        val nextHeight = 15 + nextRowHeight()
        // A typical 4×3 may offer only 180dp. Reserve room for its extra day/time/room first;
        // the next course name is supplementary. Restore large core type if that still cannot fit.
        if (primary != null && heightDp >= 180 && showCourse) {
            val originalTimeSize = timeSize
            val originalRoomSize = roomSize
            while (baseHeight() + nextHeight > budget && (timeSize > 20f || roomSize > 20f)) {
                if (timeSize > 20f) timeSize -= 1f
                if (roomSize > 20f) roomSize -= 1f
            }
            if (baseHeight() + nextHeight > budget) {
                timeSize = originalTimeSize
                roomSize = originalRoomSize
            }
        }
        val showNext = primary != null && baseHeight() + nextHeight <= budget
        val usedHeight = baseHeight() + (if (showNext) nextHeight else 0f)
        val showMetadata = metadata.isNotEmpty() && usedHeight + metaHeight <= budget
        val showNextCourse = next != null && showNext &&
            usedHeight + (if (showMetadata) metaHeight else 0f) +
                2 + measure.height(nextCourse, 12f, regular, oneLine = true, availableWidthDp = nextLeftWidth()) <= budget

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
        views.text(R.id.widget_next_label, nextLabel, smallSize)
        views.text(R.id.widget_next_time, nextTime, NEXT_TIME_SIZE)
        views.text(R.id.widget_next_room, nextRoom, nextRoomSize)
        views.text(R.id.widget_next_room_label, ROOM_LABEL, NEXT_ROOM_LABEL_SIZE)
        views.text(R.id.widget_next_course, nextCourse, 12f)
        views.text(R.id.widget_data_time, dataTime, footerSize)
        views.text(R.id.widget_zone, zone, footerSize)
        views.setTextViewTextSize(R.id.widget_clock, TypedValue.COMPLEX_UNIT_SP, footerSize + 1)
        views.setString(R.id.widget_clock, "setTimeZone", settings.zoneId)
        views.visible(R.id.widget_room_badge, hasBadge)
        views.visible(R.id.widget_room_plain, !hasBadge)
        views.visible(R.id.widget_course, showCourse)
        views.visible(R.id.widget_metadata, showMetadata)
        views.visible(R.id.widget_hint, hint.isNotEmpty())
        views.visible(R.id.widget_next_container, showNext)
        views.visible(R.id.widget_next_time, next != null)
        views.visible(R.id.widget_next_room_badge, next != null)
        views.visible(R.id.widget_next_course, showNextCourse)
        views.setContentDescription(R.id.widget_time, if (primary != null) "Jam kuliah $time" else time)
        views.setContentDescription(R.id.widget_room_badge, if (primary != null) "Ruang $room" else room)
        views.setContentDescription(R.id.widget_room_plain, room)
        if (next != null) {
            views.setContentDescription(R.id.widget_next_row, "$nextCourse · $nextTime · ruang $nextRoom")
        }
        views.setContentDescription(R.id.widget_data_time, "Data diperbarui ${state.now.format(timeFormat)}")
        val openMain = if (primary != null && !preview) {
            ScheduleIntents.openCourse(context, primary.course.id)
        } else ScheduleIntents.openSettings(context)
        views.setOnClickPendingIntent(android.R.id.background, openMain)
        views.setOnClickPendingIntent(
            R.id.widget_next_container,
            if (next != null && !preview) ScheduleIntents.openCourse(context, next.course.id) else openMain,
        )
        return views
    }

    private fun timeRange(occurrence: Occurrence) =
        "${occurrence.start.format(timeFormat)}–${occurrence.end.format(timeFormat)}"

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
