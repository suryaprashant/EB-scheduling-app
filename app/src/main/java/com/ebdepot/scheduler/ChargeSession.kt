package com.ebdepot.scheduler

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalTime
import java.util.Locale

data class ChargeSession(
    val id: String,
    val bus: Int,
    val charger: Int,
    val startMinute: Int,
    val endMinute: Int,
    val durationMinutes: Int,
)

enum class SessionStatus {
    ACTIVE,
    UPCOMING,
    COMPLETE,
}

enum class ScheduleFilter(val label: String) {
    ALL("All"),
    UPCOMING("Upcoming"),
    ACTIVE("Charging"),
    COMPLETE("Complete"),
    ISSUES("Issues"),
}

private const val PREFERENCES = "depot_charge"
private const val SESSIONS_KEY = "sessions"

fun loadSessions(context: Context): List<ChargeSession> {
    val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    val stored = preferences.getString(SESSIONS_KEY, null)
    if (stored != null) return decodeSessions(JSONArray(stored))

    val workbookData = context.assets.open("workbook_schedule.json")
        .bufferedReader()
        .use { it.readText() }
    val sessions = decodeSessions(JSONArray(workbookData))
    saveSessions(context, sessions)
    return sessions
}

fun saveSessions(context: Context, sessions: List<ChargeSession>) {
    val array = JSONArray()
    sessions.forEach { session ->
        array.put(
            JSONObject()
                .put("id", session.id)
                .put("bus", session.bus)
                .put("charger", session.charger)
                .put("startMinute", session.startMinute)
                .put("endMinute", session.endMinute)
                .put("durationMinutes", session.durationMinutes),
        )
    }
    context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        .edit()
        .putString(SESSIONS_KEY, array.toString())
        .apply()
}

private fun decodeSessions(array: JSONArray): List<ChargeSession> =
    List(array.length()) { index ->
        val item = array.getJSONObject(index)
        ChargeSession(
            id = item.getString("id"),
            bus = item.getInt("bus"),
            charger = item.getInt("charger"),
            startMinute = item.getInt("startMinute"),
            endMinute = item.getInt("endMinute"),
            durationMinutes = item.getInt("durationMinutes"),
        )
    }

fun conflicts(sessions: List<ChargeSession>): List<Pair<ChargeSession, ChargeSession>> {
    val result = mutableListOf<Pair<ChargeSession, ChargeSession>>()
    for (firstIndex in sessions.indices) {
        for (secondIndex in firstIndex + 1 until sessions.size) {
            val first = sessions[firstIndex]
            val second = sessions[secondIndex]
            val overlaps = first.startMinute < second.endMinute &&
                second.startMinute < first.endMinute
            if (overlaps && (first.charger == second.charger || first.bus == second.bus)) {
                result += first to second
            }
        }
    }
    return result
}

fun conflictSessionIds(sessions: List<ChargeSession>): Set<String> =
    conflicts(sessions).flatMapTo(mutableSetOf()) { listOf(it.first.id, it.second.id) }

fun currentScheduleMinute(): Int {
    val now = LocalTime.now()
    val minute = now.hour * 60 + now.minute
    return if (now.hour < 4) minute + 24 * 60 else minute
}

fun sessionStatus(session: ChargeSession, now: Int = currentScheduleMinute()): SessionStatus =
    when {
        now in session.startMinute until session.endMinute -> SessionStatus.ACTIVE
        session.startMinute > now -> SessionStatus.UPCOMING
        else -> SessionStatus.COMPLETE
    }

fun formatScheduleTime(minute: Int): String {
    val time = minute % (24 * 60)
    val suffix = if (minute >= 24 * 60) " +1" else ""
    return String.format(Locale.getDefault(), "%02d:%02d%s", time / 60, time % 60, suffix)
}

fun sessionValidationError(
    bus: Int,
    charger: Int,
    startTime: String,
    duration: Int,
    sessions: List<ChargeSession>,
): String? {
    if (bus !in 1..101) return "Enter a bus number from 1 to 101."
    if (charger !in 1..20) return "Enter a charger number from 1 to 20."
    if (!startTime.matches(Regex("\\d{2}:[0-5]\\d"))) {
        return "Enter the start time in 24-hour format, for example 21:30."
    }
    if (duration !in 5..360) return "Charging time must be between 5 and 360 minutes."

    val hour = startTime.substringBefore(':').toInt()
    if (hour !in 0..23) return "Enter a valid hour from 00 to 23."
    val startMinute = hour * 60 + startTime.substringAfter(':').toInt() +
        if (hour < 4) 24 * 60 else 0
    val endMinute = startMinute + duration

    val conflict = sessions.firstOrNull { existing ->
        val overlaps = startMinute < existing.endMinute && existing.startMinute < endMinute
        overlaps && (existing.charger == charger || existing.bus == bus)
    } ?: return null

    return when {
        conflict.charger == charger ->
            "Charger ${charger.toString().padStart(2, '0')} is already assigned to Bus ${conflict.bus} during that time."
        else -> "Bus $bus already has an overlapping charging session on Charger ${conflict.charger}."
    }
}
