package com.shamil.launcher.glance

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.time.ZoneId

/** One calendar event (one occurrence of it, for repeating events). Times are milliseconds. */
data class CalEvent(
    val eventId: Long,
    val title: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    /** The calendar's colour for this event (ARGB), like in his calendar app. */
    val colour: Int,
    val location: String?,
)

/**
 * Reads his upcoming events from the phone's calendar (only on the phone, nothing is sent anywhere).
 * Needs the "read calendar" permission, which Seelie asks for when he taps "Tap to show your events".
 */
object AgendaSource {
    fun hasPermission(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Every event from now until the end of tomorrow that hasn't ended yet, earliest first. */
    fun upcoming(context: Context, now: Long = System.currentTimeMillis()): List<CalEvent> {
        if (!hasPermission(context)) return emptyList()
        val zone = ZoneId.systemDefault()
        val until = LocalDate.now(zone).plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()
        // "Instances" = every occurrence in the time window, so repeating events show up too.
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .also { ContentUris.appendId(it, now - 24 * 3_600_000L) }   // a day back, so all-day events of today are included
            .also { ContentUris.appendId(it, until) }
            .build()
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.DISPLAY_COLOR,
            CalendarContract.Instances.EVENT_LOCATION,
        )
        val events = mutableListOf<CalEvent>()
        runCatching {
            context.contentResolver.query(uri, projection, "${CalendarContract.Instances.VISIBLE} = 1", null,
                "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
                while (c.moveToNext()) {
                    val allDay = c.getInt(4) == 1
                    var begin = c.getLong(2)
                    var end = c.getLong(3)
                    if (allDay) {
                        // All-day events are stored at midnight UTC; move them to midnight here.
                        val day = java.time.Instant.ofEpochMilli(begin).atZone(ZoneId.of("UTC")).toLocalDate()
                        val lastDay = java.time.Instant.ofEpochMilli(end).atZone(ZoneId.of("UTC")).toLocalDate()
                        begin = day.atStartOfDay(zone).toInstant().toEpochMilli()
                        end = lastDay.atStartOfDay(zone).toInstant().toEpochMilli()
                    }
                    if (end <= now) continue   // already over
                    events += CalEvent(
                        eventId = c.getLong(0),
                        title = c.getString(1)?.takeIf { it.isNotBlank() } ?: "(No title)",
                        begin = begin, end = end, allDay = allDay,
                        colour = c.getInt(5),
                        location = c.getString(6)?.takeIf { it.isNotBlank() },
                    )
                }
            }
        }
        return events.sortedBy { it.begin }
    }

    /** The event to show on the home: the one happening now or the next one today (all-day events only if nothing else). */
    fun nextToday(events: List<CalEvent>, now: Long = System.currentTimeMillis()): CalEvent? {
        val zone = ZoneId.systemDefault()
        val endOfToday = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val today = events.filter { it.begin < endOfToday && it.end > now }
        return today.firstOrNull { !it.allDay } ?: today.firstOrNull()
    }

    /** Opens one event in his calendar app. */
    fun openIntent(event: CalEvent): Intent =
        Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.eventId))
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.begin)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.end)
}
