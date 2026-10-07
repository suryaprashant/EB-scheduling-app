package com.ebdepot.scheduler

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChargingAllocationEngineTest {

    private val testSessions = listOf(
        // Bus 4 previous session (missed if arriving at 20:00)
        ChargeSession(
            id = "wb-023",
            bus = 4,
            charger = 3,
            startMinute = 780, // 13:00
            endMinute = 825,   // 13:45 (duration 45)
            durationMinutes = 45,
        ),
        // Bus 4 scheduled upcoming session
        ChargeSession(
            id = "wb-089",
            bus = 4,
            charger = 4,
            startMinute = 1300, // 21:40
            endMinute = 1325,   // 22:05 (duration 25)
            durationMinutes = 25,
        ),
        // Other charger sessions around 20:00
        ChargeSession(
            id = "ch1-prev",
            bus = 50,
            charger = 1,
            startMinute = 1055,
            endMinute = 1095, // ends at 18:15
            durationMinutes = 40,
        ),
        ChargeSession(
            id = "ch1-next",
            bus = 51,
            charger = 1,
            startMinute = 1245, // starts at 20:45
            endMinute = 1315,
            durationMinutes = 70,
        ),
    )

    @Test
    fun testBus4ArrivalAt2000With10Soc_MatchesExcelScreenshot() {
        // Request: Bus 4 arrives at 20:00 (1200 min) with 10% SOC
        val req = AllocationRequest(
            busNumber = 4,
            arrivalTime = "20:00",
            arrivalSoc = 10.0,
        )

        val result = ChargingAllocationEngine.allocate(req, testSessions)

        // Verifications matching user Excel VBA screenshot:
        assertEquals(4, result.busNumber)
        assertEquals(1200, result.arrivalMinute)
        assertEquals(10.0, result.arrivalSoc, 0.001)

        // Allocated Charger Number: 1
        assertEquals(1, result.allocatedCharger)

        // Plug-in time: 8:00:00 PM (1200)
        assertEquals(1200, result.pluginMinute)
        assertEquals("20:00", ChargingAllocationEngine.formatTime24(result.pluginMinute!!))
        assertEquals("8:00 PM", ChargingAllocationEngine.formatTime12(result.pluginMinute!!))

        // Plug-out time: 8:45:00 PM (1245)
        assertEquals(1245, result.plugoutMinute)
        assertEquals("20:45", ChargingAllocationEngine.formatTime24(result.plugoutMinute!!))
        assertEquals("8:45 PM", ChargingAllocationEngine.formatTime12(result.plugoutMinute!!))

        // Expected SOC level (%): 36.39
        assertNotNull(result.expectedSoc)
        assertEquals(36.39, result.expectedSoc!!, 0.01)

        // Status caption
        assertEquals("Missed session. New charger allocated: 1", result.statusText)
        assertTrue(result.isMissedAndReallocated)
    }

    @Test
    fun testArrivedEarlyWithin1Hour() {
        // Bus 4 arrives at 21:00 (1260 min) for session starting at 21:40 (1300 min)
        // timeDiff = 40 min / 60 = 0.67 hr <= 1 hr
        val req = AllocationRequest(
            busNumber = 4,
            arrivalTime = "21:00",
            arrivalSoc = 20.0,
        )

        // Only include the 21:40 session (no earlier missed session)
        val sessions = listOf(
            ChargeSession(
                id = "wb-089",
                bus = 4,
                charger = 4,
                startMinute = 1300,
                endMinute = 1325,
                durationMinutes = 25,
            )
        )

        val result = ChargingAllocationEngine.allocate(req, sessions)

        assertEquals(4, result.allocatedCharger)
        assertEquals(1300, result.pluginMinute)
        assertEquals(1325, result.plugoutMinute)
        assertEquals("Arrived early (<1 hr). Waiting for scheduled charger.", result.statusText)
    }
}
