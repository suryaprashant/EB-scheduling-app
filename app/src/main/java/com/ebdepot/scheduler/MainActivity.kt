package com.ebdepot.scheduler

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EvStation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.coroutines.delay

private val Forest = Color(0xFF1A6B52)
private val DeepForest = Color(0xFF123D30)
private val Background = Color(0xFFF5F7F4)
private val Ink = Color(0xFF183129)
private val Muted = Color(0xFF718078)
private val Line = Color(0xFFE2E9E4)
private val Mint = Color(0xFFE4F3EA)
private val Amber = Color(0xFF9A6517)
private val AmberBackground = Color(0xFFFFF3D9)
private val Red = Color(0xFFAE4939)

private enum class AppTab(val label: String) {
    OVERVIEW("Overview"),
    SCHEDULE("Schedule"),
    CHARGERS("Chargers"),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Forest,
                    onPrimary = Color.White,
                    secondary = DeepForest,
                    background = Background,
                    surface = Color.White,
                    onSurface = Ink,
                ),
            ) {
                DepotChargeApp()
            }
        }
    }
}

@Composable
private fun DepotChargeApp() {
    val context = LocalContext.current
    val sessions = remember {
        mutableStateListOf<ChargeSession>().apply { addAll(loadSessions(context)) }
    }
    var selectedTab by remember { mutableStateOf(AppTab.OVERVIEW) }
    var scheduleFilter by remember { mutableStateOf(ScheduleFilter.ALL) }
    var showAddSession by remember { mutableStateOf(false) }
    var selectedSession by remember { mutableStateOf<ChargeSession?>(null) }
    var currentMinute by remember { mutableStateOf(currentScheduleMinute()) }
    val sessionList = sessions.toList()

    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            currentMinute = currentScheduleMinute()
        }
    }

    Scaffold(
        containerColor = Background,
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                AppTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = {
                            Icon(
                                imageVector = when (tab) {
                                    AppTab.OVERVIEW -> Icons.Filled.Dashboard
                                    AppTab.SCHEDULE -> Icons.Filled.CalendarMonth
                                    AppTab.CHARGERS -> Icons.Filled.EvStation
                                },
                                contentDescription = null,
                            )
                        },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddSession = true },
                containerColor = Forest,
                contentColor = Color.White,
                shape = RoundedCornerShape(18.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add charging session")
            }
        },
    ) { innerPadding ->
        when (selectedTab) {
            AppTab.OVERVIEW -> OverviewScreen(
                sessions = sessionList,
                now = currentMinute,
                onOpenSchedule = {
                    scheduleFilter = ScheduleFilter.ALL
                    selectedTab = AppTab.SCHEDULE
                },
                onOpenIssues = {
                    scheduleFilter = ScheduleFilter.ISSUES
                    selectedTab = AppTab.SCHEDULE
                },
                onSessionClick = { selectedSession = it },
                modifier = Modifier.padding(innerPadding),
            )
            AppTab.SCHEDULE -> ScheduleScreen(
                sessions = sessionList,
                now = currentMinute,
                filter = scheduleFilter,
                onFilterChange = { scheduleFilter = it },
                onSessionClick = { selectedSession = it },
                modifier = Modifier.padding(innerPadding),
            )
            AppTab.CHARGERS -> ChargersScreen(
                sessions = sessionList,
                now = currentMinute,
                onSessionClick = { selectedSession = it },
                modifier = Modifier.padding(innerPadding),
            )
        }
    }

    if (showAddSession) {
        AddSessionDialog(
            onDismiss = { showAddSession = false },
            onSave = { bus, charger, time, duration ->
                val error = sessionValidationError(bus, charger, time, duration, sessions)
                if (error == null) {
                    val hour = time.substringBefore(':').toInt()
                    val start = hour * 60 + time.substringAfter(':').toInt() +
                        if (hour < 4) 24 * 60 else 0
                    sessions.add(
                        ChargeSession(
                            id = UUID.randomUUID().toString(),
                            bus = bus,
                            charger = charger,
                            startMinute = start,
                            endMinute = start + duration,
                            durationMinutes = duration,
                        ),
                    )
                    saveSessions(context, sessions)
                }
                error
            },
        )
    }

    selectedSession?.let { session ->
        SessionDetailsDialog(
            session = session,
            now = currentMinute,
            conflicted = session.id in conflictSessionIds(sessionList),
            onDismiss = { selectedSession = null },
            onDelete = {
                sessions.removeAll { it.id == session.id }
                saveSessions(context, sessions)
                selectedSession = null
            },
        )
    }
}

@Composable
private fun OverviewScreen(
    sessions: List<ChargeSession>,
    now: Int,
    onOpenSchedule: () -> Unit,
    onOpenIssues: () -> Unit,
    onSessionClick: (ChargeSession) -> Unit,
    modifier: Modifier = Modifier,
) {
    val overlaps = conflicts(sessions)
    val conflictedIds = conflictSessionIds(sessions)
    val upcoming = sessions
        .filter { it.endMinute > now }
        .sortedBy { it.startMinute }
        .take(3)
    val dateLabel = LocalDate.now().format(DateTimeFormatter.ofPattern("EEE, MMM d"))

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(46.dp),
                shape = RoundedCornerShape(15.dp),
                color = Mint,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Bolt, contentDescription = null, tint = Forest)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("DEPOTCHARGE", fontSize = 12.sp, color = Muted, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                Text("Bus depot · Operations", fontSize = 15.sp, color = Ink, fontWeight = FontWeight.SemiBold)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(dateLabel, fontSize = 13.sp, color = Ink, fontWeight = FontWeight.SemiBold)
                Text("24-hour plan", fontSize = 11.sp, color = Muted)
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("Good day, team", fontSize = 28.sp, color = Ink, fontWeight = FontWeight.Bold)
        Text("Here’s your depot charging overview.", fontSize = 14.sp, color = Muted)
        Spacer(Modifier.height(18.dp))

        Card(
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = DeepForest),
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.12f)) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF77D8A4)))
                            Spacer(Modifier.width(7.dp))
                            Text("SCHEDULE READY", fontSize = 10.sp, color = Color(0xFFD2E9DC), fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF8FE0B0), modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.height(16.dp))
                Text("${sessions.size}", fontSize = 40.sp, color = Color.White, fontWeight = FontWeight.Bold, lineHeight = 42.sp)
                Text("charging sessions planned", fontSize = 14.sp, color = Color(0xFFC1D8CB))
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MetricInHero(value = "101", label = "FLEET BUSES")
                    Spacer(Modifier.width(26.dp))
                    Box(Modifier.width(1.dp).height(32.dp).background(Color.White.copy(alpha = 0.2f)))
                    Spacer(Modifier.width(26.dp))
                    MetricInHero(value = "20", label = "CHARGERS")
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onOpenSchedule) {
                        Text("View plan  ›", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryCard(
                title = "Charging now",
                value = sessions.count { sessionStatus(it, now) == SessionStatus.ACTIVE }.toString(),
                caption = "active connections",
                icon = Icons.Filled.Bolt,
                modifier = Modifier.weight(1f),
            )
            SummaryCard(
                title = "Ready next",
                value = sessions.count { sessionStatus(it, now) == SessionStatus.UPCOMING }.toString(),
                caption = "upcoming sessions",
                icon = Icons.Filled.CalendarMonth,
                modifier = Modifier.weight(1f),
            )
        }

        if (overlaps.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenIssues),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = AmberBackground),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = Amber)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${overlaps.size} charger overlaps to review", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("Found in the imported Excel schedule", color = Muted, fontSize = 12.sp)
                    }
                    Text("Review  ›", color = Amber, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(26.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Next on the plan", fontSize = 19.sp, color = Ink, fontWeight = FontWeight.Bold)
                Text("Your upcoming bus connections", fontSize = 12.sp, color = Muted)
            }
            TextButton(onClick = onOpenSchedule) { Text("See all", color = Forest, fontWeight = FontWeight.SemiBold) }
        }
        Spacer(Modifier.height(8.dp))
        if (upcoming.isEmpty()) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
            ) {
                Text("No more sessions on this plan.", modifier = Modifier.padding(18.dp), color = Muted)
            }
        } else {
            upcoming.forEach { session ->
                SessionCard(
                    session = session,
                    now = now,
                    conflicted = session.id in conflictedIds,
                    onClick = { onSessionClick(session) },
                )
                Spacer(Modifier.height(9.dp))
            }
        }
        Spacer(Modifier.height(92.dp))
    }
}

@Composable
private fun MetricInHero(value: String, label: String) {
    Column {
        Text(value, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Color(0xFFC1D8CB), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
    }
}

@Composable
private fun SummaryCard(
    title: String,
    value: String,
    caption: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(21.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(Modifier.padding(16.dp)) {
            Icon(icon, contentDescription = null, tint = Forest, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(13.dp))
            Text(value, fontSize = 26.sp, color = Ink, fontWeight = FontWeight.Bold)
            Text(title, fontSize = 13.sp, color = Ink, fontWeight = FontWeight.SemiBold)
            Text(caption, fontSize = 11.sp, color = Muted)
        }
    }
}

@Composable
private fun ScheduleScreen(
    sessions: List<ChargeSession>,
    now: Int,
    filter: ScheduleFilter,
    onFilterChange: (ScheduleFilter) -> Unit,
    onSessionClick: (ChargeSession) -> Unit,
    modifier: Modifier = Modifier,
) {
    var search by remember { mutableStateOf("") }
    val conflictedIds = conflictSessionIds(sessions)
    val visible = sessions
        .asSequence()
        .filter { session ->
            when (filter) {
                ScheduleFilter.ALL -> true
                ScheduleFilter.ISSUES -> session.id in conflictedIds
                ScheduleFilter.ACTIVE -> sessionStatus(session, now) == SessionStatus.ACTIVE
                ScheduleFilter.UPCOMING -> sessionStatus(session, now) == SessionStatus.UPCOMING
                ScheduleFilter.COMPLETE -> sessionStatus(session, now) == SessionStatus.COMPLETE
            }
        }
        .filter {
            search.isBlank() || it.bus.toString().contains(search.trim()) ||
                it.charger.toString().contains(search.trim())
        }
        .sortedBy { it.startMinute }
        .toList()

    Column(modifier.fillMaxSize()) {
        ScreenHeading(
            eyebrow = "BUS DEPOT · EV OPERATIONS",
            title = "Charge schedule",
            subtitle = "${sessions.size} sessions · 20 chargers",
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 12.dp),
        )
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            placeholder = { Text("Find a bus or charger", color = Muted) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = Muted) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ScheduleFilter.entries.forEach { option ->
                val label = if (option == ScheduleFilter.ISSUES) {
                    "${option.label} ${conflicts(sessions).size}"
                } else {
                    option.label
                }
                FilterChip(
                    selected = filter == option,
                    onClick = { onFilterChange(option) },
                    label = { Text(label) },
                )
            }
        }
        if (filter == ScheduleFilter.ISSUES && visible.isNotEmpty()) {
            Text(
                "These sessions overlap on a charger in the source workbook.",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                color = Amber,
                fontSize = 12.sp,
            )
        }
        if (visible.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("No sessions match this view.", color = Muted)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                item {
                    Text(
                        "${visible.size} ${if (visible.size == 1) "session" else "sessions"}",
                        color = Muted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                }
                items(visible, key = { it.id }) { session ->
                    SessionCard(
                        session = session,
                        now = now,
                        conflicted = session.id in conflictedIds,
                        onClick = { onSessionClick(session) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ChargersScreen(
    sessions: List<ChargeSession>,
    now: Int,
    onSessionClick: (ChargeSession) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        ScreenHeading(
            eyebrow = "LIVE DEPOT VIEW",
            title = "Chargers",
            subtitle = "20 charging points · tap a session to review",
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 12.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items((1..20).toList()) { charger ->
                val chargerSessions = sessions.filter { it.charger == charger }
                val active = chargerSessions.firstOrNull { sessionStatus(it, now) == SessionStatus.ACTIVE }
                val next = chargerSessions
                    .filter { it.endMinute > now && it.startMinute >= now }
                    .minByOrNull { it.startMinute }
                ChargerCard(
                    charger = charger,
                    sessionCount = chargerSessions.size,
                    active = active,
                    next = next,
                    onClick = { (active ?: next)?.let(onSessionClick) },
                )
            }
        }
    }
}

@Composable
private fun ScreenHeading(
    eyebrow: String,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(eyebrow, fontSize = 10.sp, letterSpacing = 1.2.sp, color = Forest, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(5.dp))
        Text(title, fontSize = 27.sp, color = Ink, fontWeight = FontWeight.Bold)
        Text(subtitle, fontSize = 13.sp, color = Muted)
    }
}

@Composable
private fun SessionCard(
    session: ChargeSession,
    now: Int,
    conflicted: Boolean,
    onClick: () -> Unit,
) {
    val status = sessionStatus(session, now)
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(formatScheduleTime(session.startMinute), fontSize = 14.sp, color = Ink, fontWeight = FontWeight.Bold)
                Box(Modifier.padding(vertical = 5.dp).width(1.dp).height(12.dp).background(Line))
                Text(formatScheduleTime(session.endMinute), fontSize = 11.sp, color = Muted)
            }
            Spacer(Modifier.width(15.dp))
            Box(Modifier.width(3.dp).height(42.dp).clip(CircleShape).background(if (conflicted) Amber else Forest))
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text("Bus ${session.bus.toString().padStart(2, '0')}", fontSize = 15.sp, color = Ink, fontWeight = FontWeight.Bold)
                Text(
                    "Charger ${session.charger.toString().padStart(2, '0')}  ·  ${session.durationMinutes} min",
                    fontSize = 12.sp,
                    color = Muted,
                )
            }
            StatusBadge(status, conflicted)
        }
    }
}

@Composable
private fun StatusBadge(status: SessionStatus, conflicted: Boolean) {
    val (label, foreground, background) = when {
        conflicted -> Triple("Review", Amber, AmberBackground)
        status == SessionStatus.ACTIVE -> Triple("Charging", Forest, Mint)
        status == SessionStatus.UPCOMING -> Triple("Upcoming", Muted, Background)
        else -> Triple("Done", Muted, Background)
    }
    Surface(shape = RoundedCornerShape(100.dp), color = background) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
            color = foreground,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ChargerCard(
    charger: Int,
    sessionCount: Int,
    active: ChargeSession?,
    next: ChargeSession?,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(10.dp), color = Mint) {
                    Icon(
                        Icons.Filled.EvStation,
                        contentDescription = null,
                        tint = Forest,
                        modifier = Modifier.padding(8.dp).size(18.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.size(8.dp).clip(CircleShape)
                        .background(if (active != null) Forest else Color(0xFFB8C8BE)),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text("Charger ${charger.toString().padStart(2, '0')}", fontSize = 15.sp, color = Ink, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(3.dp))
            if (active != null) {
                Text("Bus ${active.bus} · charging", fontSize = 12.sp, color = Forest, fontWeight = FontWeight.SemiBold)
                Text("${formatScheduleTime(active.startMinute)} – ${formatScheduleTime(active.endMinute)}", fontSize = 11.sp, color = Muted)
            } else if (next != null) {
                Text("Next: Bus ${next.bus}", fontSize = 12.sp, color = Ink, fontWeight = FontWeight.SemiBold)
                Text("At ${formatScheduleTime(next.startMinute)}", fontSize = 11.sp, color = Muted)
            } else {
                Text("No upcoming session", fontSize = 12.sp, color = Muted)
                Text("$sessionCount sessions in plan", fontSize = 11.sp, color = Muted)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSessionDialog(
    onDismiss: () -> Unit,
    onSave: (Int, Int, String, Int) -> String?,
) {
    var bus by remember { mutableStateOf("") }
    var charger by remember { mutableStateOf("") }
    var start by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf("50") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("New session", fontWeight = FontWeight.Bold, color = Ink)
                Text("Assign a bus to a charger", fontSize = 13.sp, color = Muted, fontWeight = FontWeight.Normal)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = bus,
                    onValueChange = { bus = it.filter(Char::isDigit).take(3); error = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Bus number · 1–101") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = charger,
                    onValueChange = { charger = it.filter(Char::isDigit).take(2); error = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Charger · 1–20") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = start,
                    onValueChange = { start = it.take(5); error = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Start time · HH:mm") },
                    supportingText = { Text("Times before 04:00 are placed after midnight.") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = duration,
                    onValueChange = { duration = it.filter(Char::isDigit).take(3); error = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Charging time · minutes") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                error?.let { Text(it, color = Red, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val parsedBus = bus.toIntOrNull()
                    val parsedCharger = charger.toIntOrNull()
                    val parsedDuration = duration.toIntOrNull()
                    error = if (parsedBus == null || parsedCharger == null || parsedDuration == null) {
                        "Fill in all fields with valid numbers."
                    } else {
                        onSave(parsedBus, parsedCharger, start, parsedDuration)
                    }
                    if (error == null) onDismiss()
                },
            ) {
                Text("Add to plan", color = Forest, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Muted) } },
        containerColor = Color.White,
        shape = RoundedCornerShape(25.dp),
    )
}

@Composable
private fun SessionDetailsDialog(
    session: ChargeSession,
    now: Int,
    conflicted: Boolean,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Surface(shape = CircleShape, color = if (conflicted) AmberBackground else Mint) {
                Icon(
                    if (conflicted) Icons.Filled.WarningAmber else Icons.Filled.Bolt,
                    contentDescription = null,
                    tint = if (conflicted) Amber else Forest,
                    modifier = Modifier.padding(10.dp),
                )
            }
        },
        title = { Text("Bus ${session.bus.toString().padStart(2, '0')}", color = Ink, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("Charger ${session.charger.toString().padStart(2, '0')}", color = Muted)
                Text("${formatScheduleTime(session.startMinute)} – ${formatScheduleTime(session.endMinute)}", color = Ink, fontWeight = FontWeight.SemiBold)
                Text("${session.durationMinutes} minutes · ${sessionStatus(session, now).name.lowercase().replaceFirstChar(Char::uppercase)}", color = Muted)
                if (conflicted) {
                    Text("This session overlaps another assignment in the imported plan.", color = Amber, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDelete) {
                Icon(Icons.Filled.DeleteOutline, contentDescription = null, tint = Red)
                Spacer(Modifier.width(5.dp))
                Text("Remove", color = Red)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close", color = Forest) } },
        containerColor = Color.White,
        shape = RoundedCornerShape(25.dp),
    )
}
