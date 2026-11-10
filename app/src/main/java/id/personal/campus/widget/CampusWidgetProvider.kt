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
        val nextTimeRoom = next?.let { "${timeRange(it)} · ${it.course.room}" }.orEmpty()
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
        var smallSize = 11f
        var courseSize = 14f
        var footerSize = 10f
        var timeSize = measure.fitWidth(time, if (primary != null) 30f else 22f, 18f, monoBold)
        var roomSize = measure.fitWidth(room, if (primary != null) 30f else 20f, 18f, bold)
        var showCourse = true
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
        fun baseHeight(): Float = measure.height(heading, smallSize, medium) +
            4 + measure.height(time, timeSize, monoBold) + 1 + measure.height(room, roomSize, bold) +
            (if (showCourse) 3 + measure.height(course, courseSize, regular, oneLine = true) else 0f) +
            (if (hint.isNotEmpty()) 3 + measure.height(hint, smallSize, regular) else 0f) +
            6 + footerHeight()

        while (baseHeight() > budget && (timeSize > 18f || roomSize > 18f)) {
            timeSize = (timeSize - 1).coerceAtLeast(18f)
            roomSize = (roomSize - 1).coerceAtLeast(18f)
        }
        if (baseHeight() > budget) {
            smallSize = 10f
            courseSize = 12f
            footerSize = minOf(footerSize, 9f)
        }
        if (baseHeight() > budget) showCourse = false
        // At very large accessibility fonts, keep core text unellipsized and use the smallest
        // readable core size. Enlarging the widget restores all supplementary details.
        while (baseHeight() > budget && (timeSize > 16f || roomSize > 16f)) {
            timeSize = (timeSize - 1).coerceAtLeast(16f)
            roomSize = (roomSize - 1).coerceAtLeast(16f)
        }
        val metaHeight = if (metadata.isEmpty()) 0f else 2 + measure.height(metadata, smallSize, regular)
        val nextHeight = 15 + measure.height(nextLabel, smallSize, medium) +
            (if (next != null) 2 + measure.height(nextTimeRoom, 16f, bold) else 0f)
        // A typical 4×3 may offer only 180dp. Reserve room for its extra day/time/room first;
        // the next course name is supplementary. Restore large core type if that still cannot fit.
        if (primary != null && heightDp >= 180 && showCourse) {
            val originalTimeSize = timeSize
            val originalRoomSize = roomSize
            while (baseHeight() + nextHeight > budget && (timeSize > 22f || roomSize > 22f)) {
                if (timeSize > 22f) timeSize -= 1f
                if (roomSize > 22f) roomSize -= 1f
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
                2 + measure.height(nextCourse, 12f, regular, oneLine = true) <= budget

        val pxPadding = (padding * metrics.density).toInt()
        views.setViewPadding(android.R.id.background, pxPadding, pxPadding, pxPadding, pxPadding)
        views.text(R.id.widget_status, heading, smallSize)
        views.text(R.id.widget_time, time, timeSize)
        views.text(R.id.widget_room, room, roomSize)
        views.text(R.id.widget_course, course, courseSize)
        views.text(R.id.widget_metadata, metadata, smallSize)
        views.text(R.id.widget_hint, hint, smallSize)
        views.text(R.id.widget_next_label, nextLabel, smallSize)
        views.text(R.id.widget_next_time_room, nextTimeRoom, 16f)
        views.text(R.id.widget_next_course, nextCourse, 12f)
        views.text(R.id.widget_data_time, dataTime, footerSize)
        views.text(R.id.widget_zone, zone, footerSize)
        views.setTextViewTextSize(R.id.widget_clock, TypedValue.COMPLEX_UNIT_SP, footerSize + 1)
        views.setString(R.id.widget_clock, "setTimeZone", settings.zoneId)
        views.visible(R.id.widget_course, showCourse)
        views.visible(R.id.widget_metadata, showMetadata)
        views.visible(R.id.widget_hint, hint.isNotEmpty())
        views.visible(R.id.widget_next_container, showNext)
        views.visible(R.id.widget_next_time_room, next != null)
        views.visible(R.id.widget_next_course, showNextCourse)
        views.setContentDescription(R.id.widget_time, if (primary != null) "Jam kuliah $time" else time)
        views.setContentDescription(R.id.widget_room, if (primary != null) "Ruang $room" else room)
        views.setContentDescription(R.id.widget_next_time_room, "$nextCourse · $nextTimeRoom")
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
                .setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE)
                .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
                .setMaxLines(if (oneLine) 1 else Int.MAX_VALUE)
                .build()
            // A small margin covers font/launcher rounding without assuming the user's font scale.
            return layout.height / metrics.density + 1f
        }

        fun fitWidth(text: String, preferred: Float, minimum: Float, face: Typeface): Float {
            var size = preferred
            while (size > minimum && paint(size, face).measureText(text) > widthPx) size -= 1f
            return size.coerceAtLeast(minimum)
        }
    }
}
