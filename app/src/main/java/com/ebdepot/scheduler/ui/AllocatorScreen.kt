package com.ebdepot.scheduler.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.EvStation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ebdepot.scheduler.AllocationRequest
import com.ebdepot.scheduler.AllocationResult
import com.ebdepot.scheduler.ChargeSession
import com.ebdepot.scheduler.ChargingAllocationEngine
import kotlinx.coroutines.delay
import java.util.Locale
import java.util.UUID

private val Forest = Color(0xFF1A6B52)
private val DeepForest = Color(0xFF123D30)
private val Background = Color(0xFFF5F7F4)
private val Ink = Color(0xFF183129)
private val Muted = Color(0xFF718078)
private val Mint = Color(0xFFE4F3EA)
private val Amber = Color(0xFF9A6517)
private val AmberBackground = Color(0xFFFFF3D9)
private val IdleGreen = Color(0xFF2E7D32)
private val IdleGreenBg = Color(0xFFE8F5E9)

@Composable
fun AllocatorScreen(
    sessions: List<ChargeSession>,
    onAddSessionToPlan: (ChargeSession) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Inputs matching the Excel VBA UserForm:
    var busNumberInput by remember { mutableStateOf("4") }
    var arrivalTimeInput by remember { mutableStateOf("20:00") }
    var arrivalSocInput by remember { mutableStateOf("10") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var allocationResult by remember { mutableStateOf<AllocationResult?>(null) }
    var addedToPlanSuccess by remember { mutableStateOf(false) }

    // Animated Charging Status (VBA: ShowChargingUI animation)
    var chargingDotIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(allocationResult) {
        if (allocationResult?.allocatedCharger != null) {
            while (true) {
                delay(800L)
                chargingDotIndex = (chargingDotIndex + 1) % 4
            }
        }
    }

    val chargingAnimationText = when (chargingDotIndex) {
        0 -> "Charging"
        1 -> "Charging."
        2 -> "Charging.."
        else -> "Charging..."
    }

    // Evaluate live allocation on initial load so user immediately sees the Bus 4 calculation
    LaunchedEffect(Unit) {
        val req = AllocationRequest(
            busNumber = 4,
            arrivalTime = "20:00",
            arrivalSoc = 10.0,
        )
        allocationResult = ChargingAllocationEngine.allocate(req, sessions)
    }

    val evaluatedMinute = remember(arrivalTimeInput) {
        ChargingAllocationEngine.parseTimeToMinutes(arrivalTimeInput) ?: 1200
    }

    val chargerSnapshots = remember(evaluatedMinute, sessions) {
        ChargingAllocationEngine.getChargerSnapshotsAtTime(evaluatedMinute, sessions)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp),
    ) {
        Spacer(Modifier.height(18.dp))

        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(46.dp),
                shape = RoundedCornerShape(14.dp),
                color = Mint,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.BatteryChargingFull, contentDescription = null, tint = Forest)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "DEPOT OPERATIONS",
                    fontSize = 11.sp,
                    color = Muted,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                )
                Text(
                    text = "EB Charger Allocation",
                    fontSize = 20.sp,
                    color = Ink,
                    fontWeight = FontWeight.Bold,
                )
            }

            // Quick Preset / Sample Button
            OutlinedButton(
                onClick = {
                    busNumberInput = "4"
                    arrivalTimeInput = "20:00"
                    arrivalSocInput = "10"
                    errorMessage = null
                    addedToPlanSuccess = false
                    val req = AllocationRequest(4, "20:00", 10.0)
                    allocationResult = ChargingAllocationEngine.allocate(req, sessions)
                },
                shape = RoundedCornerShape(12.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier.testTag("sample_preset_button"),
            ) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp), tint = Forest)
                Spacer(Modifier.width(4.dp))
                Text("Sample (Bus 4)", fontSize = 11.sp, color = Forest, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(16.dp))

        // 1. INPUT FORM CARD (Electric Bus Number, Arrival Time, Arrival SOC%)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "Vehicle & Arrival Parameters",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Ink,
                )
                Text(
                    text = "Enter EB number, arrival time, and arrival battery SOC%",
                    fontSize = 12.sp,
                    color = Muted,
                )

                Spacer(Modifier.height(16.dp))

                // Electric Bus Number
                OutlinedTextField(
                    value = busNumberInput,
                    onValueChange = {
                        busNumberInput = it.filter(Char::isDigit).take(3)
                        errorMessage = null
                        addedToPlanSuccess = false
                    },
                    label = { Text("Electric Bus Number") },
                    placeholder = { Text("e.g. 4") },
                    leadingIcon = { Icon(Icons.Filled.DirectionsBus, contentDescription = null, tint = Forest) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("bus_number_input"),
                )

                Spacer(Modifier.height(12.dp))

                // Electric Bus Arrival Time
                OutlinedTextField(
                    value = arrivalTimeInput,
                    onValueChange = {
                        arrivalTimeInput = it.take(8)
                        errorMessage = null
                        addedToPlanSuccess = false
                    },
                    label = { Text("Electric Bus Arrival time (HH:mm)") },
                    placeholder = { Text("e.g. 20:00 or 8:00 PM") },
                    leadingIcon = { Icon(Icons.Filled.Schedule, contentDescription = null, tint = Forest) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("arrival_time_input"),
                )

                // Quick time suggestions
                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf("13:00", "20:00", "21:30", "22:00").forEach { timeTag ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (arrivalTimeInput == timeTag) Mint else Background,
                            modifier = Modifier.clickable {
                                arrivalTimeInput = timeTag
                                errorMessage = null
                                addedToPlanSuccess = false
                            },
                        ) {
                            Text(
                                text = timeTag,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (arrivalTimeInput == timeTag) Forest else Muted,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Electric Bus SOC level (%)
                OutlinedTextField(
                    value = arrivalSocInput,
                    onValueChange = {
                        arrivalSocInput = it.filter { c -> c.isDigit() || c == '.' }.take(5)
                        errorMessage = null
                        addedToPlanSuccess = false
                    },
                    label = { Text("Electric Bus SOC level (%)") },
                    placeholder = { Text("e.g. 10") },
                    leadingIcon = { Icon(Icons.Filled.BatteryChargingFull, contentDescription = null, tint = Forest) },
                    trailingIcon = { Text("%", color = Muted, modifier = Modifier.padding(end = 12.dp)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("arrival_soc_input"),
                )

                errorMessage?.let { error ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = error,
                        color = Color(0xFFAE4939),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Action Buttons: Allocate & Reset
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = {
                            val busNum = busNumberInput.toIntOrNull()
                            if (busNum == null || busNum <= 0) {
                                errorMessage = "Enter Bus Number"
                                return@Button
                            }
                            val parsedTime = ChargingAllocationEngine.parseTimeToMinutes(arrivalTimeInput)
                            if (parsedTime == null) {
                                errorMessage = "Enter valid arrival time (hh:mm)"
                                return@Button
                            }
                            val soc = arrivalSocInput.toDoubleOrNull()
                            if (soc == null || soc !in 0.0..100.0) {
                                errorMessage = "Enter valid SOC level (0 to 100%)"
                                return@Button
                            }

                            val req = AllocationRequest(
                                busNumber = busNum,
                                arrivalTime = arrivalTimeInput,
                                arrivalSoc = soc,
                            )
                            allocationResult = ChargingAllocationEngine.allocate(req, sessions)
                            addedToPlanSuccess = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Forest),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1.3f)
                            .height(48.dp)
                            .testTag("allocate_button"),
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Allocate", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            busNumberInput = ""
                            arrivalTimeInput = ""
                            arrivalSocInput = ""
                            allocationResult = null
                            errorMessage = null
                            addedToPlanSuccess = false
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("reset_button"),
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp), tint = Muted)
                        Spacer(Modifier.width(6.dp))
                        Text("Reset", color = Muted, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // 2. OUTPUT ALLOCATION RESULTS
        allocationResult?.let { res ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("allocation_results_card"),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (res.isMissedAndReallocated) AmberBackground else Mint,
                ),
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    // Status row
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (res.isMissedAndReallocated) Icons.Filled.WarningAmber else Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = if (res.isMissedAndReallocated) Amber else Forest,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = res.statusText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (res.isMissedAndReallocated) Amber else Forest,
                            modifier = Modifier.weight(1f),
                        )

                        // Charging animation indicator
                        if (res.allocatedCharger != null) {
                            Surface(
                                shape = RoundedCornerShape(100.dp),
                                color = Color.White.copy(alpha = 0.85f),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Filled.Bolt,
                                        contentDescription = null,
                                        tint = Forest,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = chargingAnimationText,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Forest,
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    // 4 output metric boxes matching the Excel Form
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        ResultMetricTile(
                            label = "Allocated Charger",
                            value = res.allocatedCharger?.let { "Charger $it" } ?: "None",
                            caption = if (res.isMissedAndReallocated) "Reallocated" else "Scheduled",
                            accentColor = if (res.isMissedAndReallocated) Amber else Forest,
                            modifier = Modifier.weight(1f),
                        )
                        ResultMetricTile(
                            label = "Plug-in Time",
                            value = res.pluginMinute?.let { ChargingAllocationEngine.formatTime24(it) } ?: "--:--",
                            caption = res.pluginMinute?.let { ChargingAllocationEngine.formatTime12(it) } ?: "",
                            accentColor = Ink,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        ResultMetricTile(
                            label = "Plug-out Time",
                            value = res.plugoutMinute?.let { ChargingAllocationEngine.formatTime24(it) } ?: "--:--",
                            caption = res.plugoutMinute?.let { ChargingAllocationEngine.formatTime12(it) } ?: "",
                            accentColor = Ink,
                            modifier = Modifier.weight(1f),
                        )
                        ResultMetricTile(
                            label = "Expected SOC",
                            value = res.expectedSoc?.let { String.format(Locale.US, "%.2f%%", it) } ?: "--",
                            caption = res.expectedSoc?.let {
                                val gain = it - res.arrivalSoc
                                "+${String.format(Locale.US, "%.2f%%", gain)} gain"
                            } ?: "",
                            accentColor = if (res.isMissedAndReallocated) Amber else Forest,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    // Save to Plan Action Button
                    if (res.allocatedCharger != null && res.pluginMinute != null && res.plugoutMinute != null) {
                        Spacer(Modifier.height(14.dp))
                        if (!addedToPlanSuccess) {
                            Button(
                                onClick = {
                                    val newSession = ChargeSession(
                                        id = "alloc-${UUID.randomUUID().toString().take(8)}",
                                        bus = res.busNumber,
                                        charger = res.allocatedCharger,
                                        startMinute = res.pluginMinute,
                                        endMinute = res.plugoutMinute,
                                        durationMinutes = res.plugoutMinute - res.pluginMinute,
                                    )
                                    onAddSessionToPlan(newSession)
                                    addedToPlanSuccess = true
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (res.isMissedAndReallocated) Amber else Forest,
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("save_session_button"),
                            ) {
                                Icon(Icons.Filled.AddCircleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Add This Session to Depot Plan", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color.White.copy(alpha = 0.9f),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = IdleGreen, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Added to Depot Plan & Saved!", fontSize = 12.sp, color = IdleGreen, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // 3. SOC DOUGHNUT CHARTS (VBA: CreateSOCChart & CreateSOCChart1)
            Text(
                text = "Battery State of Charge (SOC)",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Ink,
            )
            Text(
                text = "Calculated via 240 kW DC fast chargers, 360 kWh pack & 95% efficiency",
                fontSize = 12.sp,
                color = Muted,
            )

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SocDoughnutCard(
                    title = "SOC @ Arr. Time",
                    socValue = res.arrivalSoc,
                    chartColor = Amber,
                    modifier = Modifier.weight(1f),
                )

                SocDoughnutCard(
                    title = "SOC @ Dep. Time",
                    socValue = res.expectedSoc ?: res.arrivalSoc,
                    chartColor = Forest,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // 4. REAL-TIME CHARGER STATUS PANEL (Right side of Excel UserForm)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.EvStation, contentDescription = null, tint = Forest, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Charger Allocation Status",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Ink,
                        )
                        Text(
                            text = "Depot status evaluated at ${ChargingAllocationEngine.formatTime24(evaluatedMinute)}",
                            fontSize = 12.sp,
                            color = Muted,
                        )
                    }

                    // Status pill
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = Mint,
                    ) {
                        Text(
                            text = "Time: ${ChargingAllocationEngine.formatTime24(evaluatedMinute)}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Forest,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // 2 columns of 10 chargers each (Chg 1..10 and Chg 11..20)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Column 1: Chg 1 to 10
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        chargerSnapshots.take(10).forEach { snapshot ->
                            ChargerStatusRow(
                                snapshot = snapshot,
                                isAllocatedHere = allocationResult?.allocatedCharger == snapshot.chargerNumber,
                            )
                        }
                    }

                    // Column 2: Chg 11 to 20
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        chargerSnapshots.drop(10).forEach { snapshot ->
                            ChargerStatusRow(
                                snapshot = snapshot,
                                isAllocatedHere = allocationResult?.allocatedCharger == snapshot.chargerNumber,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(96.dp))
    }
}

@Composable
private fun ResultMetricTile(
    label: String,
    value: String,
    caption: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Muted,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = accentColor,
            )
            if (caption.isNotEmpty()) {
                Text(
                    text = caption,
                    fontSize = 10.sp,
                    color = Muted,
                )
            }
        }
    }
}

@Composable
private fun ChargerStatusRow(
    snapshot: com.ebdepot.scheduler.ChargerSnapshot,
    isAllocatedHere: Boolean,
) {
    val borderColor = if (isAllocatedHere) Forest else Color(0xFFE8EFEA)
    val background = when {
        isAllocatedHere -> Mint
        snapshot.isIdle -> IdleGreenBg
        else -> Color(0xFFFFF3D9)
    }
    val statusText = when {
        isAllocatedHere -> "Allocated"
        snapshot.isIdle -> "Idle"
        else -> "Bus ${snapshot.activeSession?.bus ?: ""}"
    }
    val statusTextColor = when {
        isAllocatedHere -> Forest
        snapshot.isIdle -> IdleGreen
        else -> Amber
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .border(width = if (isAllocatedHere) 1.5.dp else 1.dp, color = borderColor, shape = RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Chg ${snapshot.chargerNumber.toString().padStart(2, '0')}",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = Ink,
            modifier = Modifier.weight(1f),
        )

        Surface(
            shape = RoundedCornerShape(6.dp),
            color = if (isAllocatedHere) Forest else (if (snapshot.isIdle) IdleGreen else Amber),
        ) {
            Text(
                text = statusText,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}
