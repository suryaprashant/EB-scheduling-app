package com.ebdepot.scheduler

import java.time.LocalTime
import java.util.Locale
import kotlin.math.round

data class AllocationRequest(
    val busNumber: Int,
    val arrivalTime: String, // "HH:mm" or "H:mm"
    val arrivalSoc: Double,  // 0.0 to 100.0
)

data class AllocationResult(
    val busNumber: Int,
    val arrivalMinute: Int,
    val arrivalSoc: Double,
    val allocatedCharger: Int?,
    val pluginMinute: Int?,
    val plugoutMinute: Int?,
    val expectedSoc: Double?,
    val statusText: String,
    val chargingDurationMinutes: Int = 0,
    val isMissedAndReallocated: Boolean = false,
    val sessionFound: Boolean = true,
    val isEarlyWaiting: Boolean = false,
    val candidateSessionId: String? = null,
)

data class ChargerSnapshot(
    val chargerNumber: Int,
    val isIdle: Boolean,
    val activeSession: ChargeSession?,
)

object ChargingAllocationEngine {

    /**
     * Parses a time string like "20:00", "8:00 PM", "08:30" into minutes from start of day.
     * Supports 24-hour format (e.g. 20:00 -> 1200) and early morning cutoff if needed.
     */
    fun parseTimeToMinutes(timeStr: String): Int? {
        val trimmed = timeStr.trim()
        if (trimmed.isEmpty()) return null

        // Try standard HH:mm or H:mm
        val regex24 = Regex("""^(\d{1,2}):(\d{2})$""")
        val match24 = regex24.find(trimmed)
        if (match24 != null) {
            val (hStr, mStr) = match24.destructured
            val h = hStr.toIntOrNull() ?: return null
            val m = mStr.toIntOrNull() ?: return null
            if (h in 0..23 && m in 0..59) {
                return h * 60 + m
            }
        }

        // Try 12-hour format e.g. "8:00 PM", "8:00:00 PM"
        val regex12 = Regex("""^(\d{1,2}):(\d{2})(?::\d{2})?\s*([APap][Mm])$""")
        val match12 = regex12.find(trimmed)
        if (match12 != null) {
            val (hStr, mStr, ampm) = match12.destructured
            var h = hStr.toIntOrNull() ?: return null
            val m = mStr.toIntOrNull() ?: return null
            if (h in 1..12 && m in 0..59) {
                if (ampm.equals("PM", ignoreCase = true) && h != 12) h += 12
                if (ampm.equals("AM", ignoreCase = true) && h == 12) h = 0
                return h * 60 + m
            }
        }

        return null
    }

    /**
     * Formats minute of day to "HH:mm" and "h:mm a".
     */
    fun formatTime24(minute: Int): String {
        val normalized = ((minute % 1440) + 1440) % 1440
        return String.format(Locale.US, "%02d:%02d", normalized / 60, normalized % 60)
    }

    fun formatTime12(minute: Int): String {
        val normalized = ((minute % 1440) + 1440) % 1440
        val hour24 = normalized / 60
        val min = normalized % 60
        val ampm = if (hour24 >= 12) "PM" else "AM"
        val hour12 = if (hour24 % 12 == 0) 12 else hour24 % 12
        return String.format(Locale.US, "%d:%02d %s", hour12, min, ampm)
    }

    fun formatTimeDisplay(minute: Int): String {
        return "${formatTime24(minute)} (${formatTime12(minute)})"
    }

    /**
     * Exact implementation of the Excel VBA Sub Electric_Bus_Charging_Schedule:
     *
     * 1. Loop through all database rows for BusNo.
     * 2. If Arrival > ScheduledEnd -> firstSessionMissed = True, SessDuration = ScheduledEnd - ScheduledStart.
     * 3. Else (Session Found):
     *    - If Arrival < ScheduledStart:
     *        timeDiff = (ScheduledStart - Arrival) in hours
     *        If timeDiff <= 1 -> ChargingTime = ScheduledEnd - ScheduledStart, plugin = ScheduledStart, status = "Arrived early (<1 hr). Waiting for scheduled charger."
     *    - If timeDiff > 1:
     *        Normal Case:
     *        If Arrival < ScheduledStart -> ChargingTime = ScheduledEnd - ScheduledStart, plugin = ScheduledStart
     *        Else -> ChargingTime = ScheduledEnd - Arrival, plugin = Arrival
     * 4. SOC Calculation:
     *    Tchg = ChargingTime in minutes
     *    SOC_dep = Round(SOC_arr + 0.95 * Tchg / 60 * 240 / 360 * 100, 2)
     *    SOC_exp = min(100, SOC_dep)
     *    T_plugout = ScheduledEnd
     *    txtChg = Charger
     * 5. SafeExit (New Charger Logic):
     *    If firstSessionMissed = True And timeDiff > 1:
     *        RequiredEnd = Arrival + SessDuration
     *        Check chargers 1..20 for availability during [Arrival, RequiredEnd].
     *        First free charger allocated:
     *        T_plugin = Arrival
     *        T_plugout = RequiredEnd
     *        txtChg = allocatedCharger
     *        lblStatus = "Missed session. New charger allocated: " & i
     */
    fun allocate(
        request: AllocationRequest,
        sessions: List<ChargeSession>,
    ): AllocationResult {
        val busNo = request.busNumber
        val arrivalMinute = parseTimeToMinutes(request.arrivalTime)
            ?: return AllocationResult(
                busNumber = busNo,
                arrivalMinute = 0,
                arrivalSoc = request.arrivalSoc,
                allocatedCharger = null,
                pluginMinute = null,
                plugoutMinute = null,
                expectedSoc = null,
                statusText = "Enter valid arrival time (hh:mm)",
                sessionFound = false,
            )

        var sessionFound = false
        var firstSessionMissed = false
        var sessDuration = 0
        var timeDiff = 0.0 // in hours

        var scheduledStart = 0
        var scheduledEnd = 0
        var charger = 0
        var chargingTime = 0
        var pluginTime = 0
        var statusCaption = ""
        var isEarlyWait = false
        var matchedSessionId: String? = null

        // Filter sessions for this bus, keeping original order in database
        val busSessions = sessions.filter { it.bus == busNo }

        for (session in busSessions) {
            val sStart = session.startMinute
            val sEnd = session.endMinute

            // Missed session check
            if (arrivalMinute > sEnd) {
                firstSessionMissed = true
                sessDuration = sEnd - sStart
            } else {
                // Session found
                charger = session.charger
                sessionFound = true
                scheduledStart = sStart
                scheduledEnd = sEnd
                matchedSessionId = session.id

                // Wait condition (early arrival <= 1 hour)
                if (arrivalMinute < scheduledStart) {
                    timeDiff = (scheduledStart - arrivalMinute) / 60.0
                    if (timeDiff <= 1.0) {
                        chargingTime = scheduledEnd - scheduledStart
                        pluginTime = scheduledStart
                        statusCaption = "Arrived early (<1 hr). Waiting for scheduled charger."
                        isEarlyWait = true
                        break
                    }
                }

                // Normal case
                if (arrivalMinute < scheduledStart) {
                    chargingTime = scheduledEnd - scheduledStart
                    pluginTime = scheduledStart
                } else {
                    chargingTime = scheduledEnd - arrivalMinute
                    pluginTime = arrivalMinute
                }
                statusCaption = "Allocated to scheduled charger."
                break
            }
        }

        // No session found
        if (!sessionFound) {
            // Check if all sessions were missed and we can still allocate an available charger
            if (firstSessionMissed && sessDuration > 0) {
                val requiredEnd = arrivalMinute + sessDuration
                for (ch in 1..20) {
                    val isBusy = sessions.any { existing ->
                        existing.charger == ch && !(requiredEnd <= existing.startMinute || arrivalMinute >= existing.endMinute)
                    }
                    if (!isBusy) {
                        val tchg = sessDuration
                        val socDep = round((request.arrivalSoc + 0.95 * (tchg / 60.0) * (240.0 / 360.0) * 100.0) * 100.0) / 100.0
                        val expectedSoc = minOf(100.0, socDep)
                        return AllocationResult(
                            busNumber = busNo,
                            arrivalMinute = arrivalMinute,
                            arrivalSoc = request.arrivalSoc,
                            allocatedCharger = ch,
                            pluginMinute = arrivalMinute,
                            plugoutMinute = requiredEnd,
                            expectedSoc = expectedSoc,
                            statusText = "Missed session. New charger allocated: $ch",
                            chargingDurationMinutes = tchg,
                            isMissedAndReallocated = true,
                            sessionFound = true,
                        )
                    }
                }
            }

            return AllocationResult(
                busNumber = busNo,
                arrivalMinute = arrivalMinute,
                arrivalSoc = request.arrivalSoc,
                allocatedCharger = null,
                pluginMinute = null,
                plugoutMinute = null,
                expectedSoc = null,
                statusText = "Bus missed all scheduled sessions.",
                sessionFound = false,
            )
        }

        // SOC calculation
        val tchg = chargingTime
        val socGain = 0.95 * (tchg.toDouble() / 60.0) * (240.0 / 360.0) * 100.0
        val socDep = round((request.arrivalSoc + socGain) * 100.0) / 100.0
        val expectedSoc = minOf(100.0, socDep)

        var finalPlugin = pluginTime
        var finalPlugout = scheduledEnd
        var finalCharger = charger
        var isMissedReallocated = false

        // New charger logic (only if missed + >1 hr early)
        if (firstSessionMissed && timeDiff > 1.0) {
            val requiredEnd = arrivalMinute + sessDuration

            for (ch in 1..20) {
                var isBusy = false
                for (existing in sessions) {
                    if (existing.charger == ch) {
                        val existingStart = existing.startMinute
                        val existingEnd = existing.endMinute
                        // Overlap check: NOT (RequiredEnd <= existingStart Or Arrival >= existingEnd)
                        if (!(requiredEnd <= existingStart || arrivalMinute >= existingEnd)) {
                            isBusy = true
                            break
                        }
                    }
                }

                if (!isBusy) {
                    finalPlugin = arrivalMinute
                    finalPlugout = requiredEnd
                    finalCharger = ch
                    statusCaption = "Missed session. New charger allocated: $ch"
                    isMissedReallocated = true
                    break
                }
            }
        }

        return AllocationResult(
            busNumber = busNo,
            arrivalMinute = arrivalMinute,
            arrivalSoc = request.arrivalSoc,
            allocatedCharger = finalCharger,
            pluginMinute = finalPlugin,
            plugoutMinute = finalPlugout,
            expectedSoc = expectedSoc,
            statusText = statusCaption,
            chargingDurationMinutes = if (isMissedReallocated) sessDuration else tchg,
            isMissedAndReallocated = isMissedReallocated,
            sessionFound = true,
            isEarlyWaiting = isEarlyWait,
            candidateSessionId = matchedSessionId,
        )
    }

    /**
     * Evaluates the status of all 20 chargers at the given minute of day.
     * Exactly like the right-side grid in the Excel form (Time: 20:00, Chg 1..20 Idle/Charging).
     */
    fun getChargerSnapshotsAtTime(
        minuteOfDay: Int,
        sessions: List<ChargeSession>,
    ): List<ChargerSnapshot> {
        return (1..20).map { chargerNum ->
            val active = sessions.firstOrNull { session ->
                session.charger == chargerNum &&
                    minuteOfDay >= session.startMinute &&
                    minuteOfDay < session.endMinute
            }
            ChargerSnapshot(
                chargerNumber = chargerNum,
                isIdle = active == null,
                activeSession = active,
            )
        }
    }
}
