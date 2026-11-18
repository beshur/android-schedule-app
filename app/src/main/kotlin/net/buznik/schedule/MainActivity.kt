package net.buznik.schedule

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

class MainActivity : ComponentActivity() {
    private var resumed by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize()) { App(resumed) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumed++
    }
}

data class AppInfo(val pkg: String, val label: String, val icon: ImageBitmap)

fun loadApps(ctx: Context): List<AppInfo> {
    val pm = ctx.packageManager
    return listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)
        .flatMap { pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(it), 0) }
        .map { it.activityInfo.applicationInfo }
        .distinctBy { it.packageName }
        .filter { it.packageName != ctx.packageName }
        .map { AppInfo(it.packageName, pm.getApplicationLabel(it).toString(), pm.getApplicationIcon(it).toBitmap(96, 96).asImageBitmap()) }
        .sortedBy { it.label.lowercase() }
}

private val DATE = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.ENGLISH)
private val NEXT = DateTimeFormatter.ofPattern("EEE, MMM d HH:mm", Locale.ENGLISH)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(resumed: Int) {
    val ctx = LocalContext.current
    val apps = remember(resumed) { loadApps(ctx).associateBy { it.pkg } }
    var schedules by remember(resumed) { mutableStateOf(Store.load(ctx)) }
    val serviceOn = remember(resumed) { WakeService.instance != null }
    var editing by remember { mutableStateOf<Schedule?>(null) }

    fun save(list: List<Schedule>) {
        Store.save(ctx, list)
        schedules = list
    }

    editing?.let { s ->
        Editor(
            initial = s,
            apps = apps,
            isNew = schedules.none { it.id == s.id },
            onSave = { e -> save(if (schedules.any { it.id == e.id }) schedules.map { if (it.id == e.id) e else it } else schedules + e); editing = null },
            onDelete = { save(schedules.filter { it.id != s.id }); editing = null },
            onCancel = { editing = null },
        )
        return
    }

    Column(Modifier.padding(32.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Schedules", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            Button(onClick = { editing = Schedule(UUID.randomUUID().toString().take(8)) }) { Text("Add schedule") }
        }
        if (!serviceOn) {
            Card(Modifier.padding(top = 16.dp).fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("The accessibility service is off, so schedules won't run.", Modifier.weight(1f))
                    Button(onClick = { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) { Text("Turn on") }
                }
            }
        }
        if (schedules.isEmpty()) Text("No schedules yet.", Modifier.padding(top = 24.dp))
        val now = LocalDateTime.now()
        LazyColumn(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(schedules, key = { it.id }) { s ->
                Card(onClick = { editing = s }, Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        AppLabel(s.app, apps, Modifier.weight(1f)) {
                            Text("${s.time} · ${describe(s.date, s.rule)}", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                s.next(now)?.let { "Next: " + it.format(NEXT) } ?: "No upcoming runs",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Switch(checked = s.enabled, onCheckedChange = { on -> save(schedules.map { if (it.id == s.id) it.copy(enabled = on) else it }) })
                    }
                }
            }
        }
    }
}

@Composable
fun AppLabel(pkg: String, apps: Map<String, AppInfo>, modifier: Modifier = Modifier, extra: @Composable () -> Unit = {}) {
    val app = apps[pkg]
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        app?.let { Image(it.icon, null, Modifier.size(48.dp).padding(end = 12.dp)) }
        Column {
            Text(app?.label ?: pkg.ifEmpty { "No app (wake TV only)" }, style = MaterialTheme.typography.titleMedium)
            extra()
        }
    }
}

@Composable
fun Editor(
    initial: Schedule,
    apps: Map<String, AppInfo>,
    isNew: Boolean,
    onSave: (Schedule) -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    var s by remember { mutableStateOf(initial) }
    var dialog by remember { mutableStateOf("") }
    var repeatMenu by remember { mutableStateOf(false) }

    Column(Modifier.padding(32.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(if (isNew) "New schedule" else "Edit schedule", style = MaterialTheme.typography.headlineMedium)
        OutlinedButton(onClick = { dialog = "app" }) { AppLabel(s.app, apps) }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { dialog = "date" }) { Text(s.date.format(DATE)) }
            OutlinedButton(onClick = { dialog = "time" }) { Text(s.time.toString()) }
        }
        Box {
            OutlinedButton(onClick = { repeatMenu = true }) { Text(describe(s.date, s.rule)) }
            DropdownMenu(expanded = repeatMenu, onDismissRequest = { repeatMenu = false }) {
                val choices = presets(s.date).let { if (s.rule in it) it else it + s.rule }
                choices.forEach { r ->
                    DropdownMenuItem(text = { Text(describe(s.date, r)) }, onClick = { s = s.copy(rule = r); repeatMenu = false })
                }
                DropdownMenuItem(text = { Text("Custom…") }, onClick = { repeatMenu = false; dialog = "custom" })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = s.enabled, onCheckedChange = { s = s.copy(enabled = it) })
            Text("Enabled", Modifier.padding(start = 12.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { onSave(s) }) { Text("Save") }
            OutlinedButton(onClick = onCancel) { Text("Cancel") }
            if (!isNew) TextButton(onClick = onDelete) { Text("Delete") }
        }
    }

    when (dialog) {
        "app" -> AppPicker(apps.values.toList(), onPick = { s = s.copy(app = it); dialog = "" }, onDismiss = { dialog = "" })
        "date" -> DateDialog(s.date, onPick = { s = s.copy(date = it, rule = s.rule?.retarget(s.date, it)) }, onDismiss = { dialog = "" })
        "time" -> TimeDialog(s.time, onPick = { s = s.copy(time = it) }, onDismiss = { dialog = "" })
        "custom" -> CustomRepeatDialog(s.date, s.rule, onPick = { s = s.copy(rule = it) }, onDismiss = { dialog = "" })
    }
}

/** Keeps a date-derived rule ("Weekly on Wednesday", "Monthly on the third Wednesday") following the new date. */
fun Rule.retarget(old: LocalDate, new: LocalDate): Rule = when {
    freq == Freq.WEEKLY && days == setOf(old.dayOfWeek) -> copy(days = setOf(new.dayOfWeek))
    freq == Freq.MONTHLY && nth != 0 -> copy(days = setOf(new.dayOfWeek), nth = nthOf(new))
    else -> this
}

@Composable
fun AppPicker(apps: List<AppInfo>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Choose app") },
        text = {
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                item { TextButton(onClick = { onPick("") }) { Text("No app (wake TV only)") } }
                items(apps, key = { it.pkg }) { a ->
                    TextButton(onClick = { onPick(a.pkg) }, Modifier.fillMaxWidth()) {
                        Image(a.icon, null, Modifier.size(40.dp).padding(end = 12.dp))
                        Text(a.label, Modifier.weight(1f))
                    }
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateDialog(initial: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) { DatePicker(state, title = null, headline = null, showModeToggle = false) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeDialog(initial: LocalTime, onPick: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(initial.hour, initial.minute, DateFormat.is24HourFormat(LocalContext.current))
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = MaterialTheme.shapes.extraLarge) {
            Column(Modifier.padding(24.dp)) {
                TimePicker(state, layoutType = TimePickerLayoutType.Horizontal)
                Row(Modifier.align(Alignment.End)) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)); onDismiss() }) { Text("OK") }
                }
            }
        }
    }
}

@Composable
fun Stepper(value: Int, min: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { onChange(maxOf(min, value - 1)) }) { Text("−") }
        Text("$value")
        TextButton(onClick = { onChange(value + 1) }) { Text("+") }
    }
}

@Composable
fun Choice(selected: Boolean, onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    Row(Modifier.selectable(selected, onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        content()
    }
}

private val UNITS = mapOf(Freq.DAILY to "day", Freq.WEEKLY to "week", Freq.MONTHLY to "month", Freq.YEARLY to "year")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CustomRepeatDialog(date: LocalDate, initial: Rule?, onPick: (Rule) -> Unit, onDismiss: () -> Unit) {
    var r by remember { mutableStateOf(initial ?: Rule(Freq.WEEKLY, days = setOf(date.dayOfWeek))) }
    var unitMenu by remember { mutableStateOf(false) }
    var untilDialog by remember { mutableStateOf(false) }
    val nth = nthOf(date)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom recurrence") },
        confirmButton = { TextButton(onClick = { onPick(r); onDismiss() }) { Text("Done") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Repeat every")
                    Stepper(r.interval, 1) { r = r.copy(interval = it) }
                    Box {
                        OutlinedButton(onClick = { unitMenu = true }) {
                            Text(UNITS.getValue(r.freq) + if (r.interval > 1) "s" else "")
                        }
                        DropdownMenu(expanded = unitMenu, onDismissRequest = { unitMenu = false }) {
                            UNITS.forEach { (f, label) ->
                                DropdownMenuItem(text = { Text(label) }, onClick = {
                                    r = when (f) {
                                        Freq.WEEKLY -> r.copy(freq = f, days = setOf(date.dayOfWeek), nth = 0)
                                        else -> r.copy(freq = f, days = emptySet(), nth = 0)
                                    }
                                    unitMenu = false
                                })
                            }
                        }
                    }
                }
                if (r.freq == Freq.WEEKLY) {
                    Text("Repeat on")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        DayOfWeek.values().forEach { d ->
                            FilterChip(
                                selected = d in r.days,
                                onClick = { r = r.copy(days = if (d in r.days && r.days.size > 1) r.days - d else r.days + d) },
                                label = { Text(d.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)) },
                            )
                        }
                    }
                }
                if (r.freq == Freq.MONTHLY) {
                    Choice(r.nth == 0, { r = r.copy(days = emptySet(), nth = 0) }) { Text("Monthly on day ${date.dayOfMonth}") }
                    Choice(r.nth != 0, { r = r.copy(days = setOf(date.dayOfWeek), nth = nth) }) {
                        Text("Monthly on the ${ordinal(nth)} ${date.dayOfWeek.label()}")
                    }
                }
                Text("Ends")
                Choice(r.until == null && r.count == 0, { r = r.copy(until = null, count = 0) }) { Text("Never") }
                Choice(r.until != null, { r = r.copy(until = r.until ?: date.plusMonths(1), count = 0) }) {
                    Text("On")
                    TextButton(onClick = { untilDialog = true }, enabled = r.until != null) {
                        Text((r.until ?: date.plusMonths(1)).format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)))
                    }
                }
                Choice(r.count > 0, { r = r.copy(until = null, count = maxOf(r.count, 13)) }) {
                    Text("After")
                    Stepper(maxOf(r.count, 13), 1) { r = r.copy(until = null, count = it) }
                    Text("occurrences")
                }
            }
        },
    )
    if (untilDialog) DateDialog(r.until ?: date, onPick = { r = r.copy(until = it, count = 0) }, onDismiss = { untilDialog = false })
}
