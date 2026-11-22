package com.perez.jornadas

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.delay
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val AR = Locale("es", "AR")
private val palette = listOf(
    Color(0xFF5B6CE0), Color(0xFFE8654B), Color(0xFFE2A12B),
    Color(0xFF3DAA6D), Color(0xFFC65BA8), Color(0xFF3BA3D9)
)
private val DOW = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")
private fun colorOf(i: Int) = palette[i % palette.size]
private fun money(d: Double): String = NumberFormat.getCurrencyInstance(AR).format(d)
private fun moneyShort(d: Double): String =
    NumberFormat.getCurrencyInstance(AR).apply { maximumFractionDigits = 0; minimumFractionDigits = 0 }.format(d)
private fun String.toAmount(): Double? = trim().replace(',', '.').toDoubleOrNull()
private fun fmtInput(d: Double): String = when {
    d == 0.0 -> ""
    d % 1.0 == 0.0 -> d.toLong().toString()
    else -> d.toString()
}
private fun fmtMult(m: Double): String = if (m % 1.0 == 0.0) m.toLong().toString() else m.toString().replace('.', ',')
private fun specialDateText(s: SpecialDay): String {
    val mn = java.time.Month.of(s.month).getDisplayName(TextStyle.FULL, AR)
    return "${s.day} de $mn" + if (s.year == 0) " · todos los años" else " de ${s.year}"
}
private fun String.cap() = replaceFirstChar { it.uppercase() }
private fun monthTitle(m: YearMonth) = m.month.getDisplayName(TextStyle.FULL, AR).cap() + " " + m.year
/** Parte de viático que le toca a esta persona ese día: (viático del día) / (personas que van ese día). */
private fun expOf(w: Workday, counts: Map<Long, Int>, ov: Map<Long, Double>, def: Double): Double {
    val n = counts[w.date] ?: 0
    return if (n == 0) 0.0 else (ov[w.date] ?: def) / n
}
private fun countsOf(works: List<Workday>): Map<Long, Int> = works.groupingBy { it.date }.eachCount()
private fun dateTitle(d: LocalDate) = d.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM yyyy", AR)).cap()

/* ---------- Raíz: header, pestañas animadas y pantalla de entrada ---------- */

@Composable
fun AppRoot(vm: MainVM) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var splash by rememberSaveable { mutableStateOf(true) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshToday() }
    LaunchedEffect(Unit) { delay(1500); splash = false }

    val tabs = listOf(
        Triple("Calendario", Icons.Rounded.CalendarMonth, "Marcá tus días de trabajo"),
        Triple("Planilla", Icons.Rounded.TableChart, "Pago y viático de cada día"),
        Triple("Resumen", Icons.Rounded.Assessment, "Totales por persona"),
        Triple("Personas", Icons.Rounded.Group, "Quién trabaja y sus tarifas")
    )
    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = { AppHeader(tabs[tab].third) },
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    tabs.forEachIndexed { i, t ->
                        NavigationBarItem(
                            selected = tab == i, onClick = { tab = i }, icon = { Icon(t.second, null) }, label = { Text(t.first) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }
            }
        ) { pad ->
            AnimatedContent(
                targetState = tab,
                modifier = Modifier.padding(pad).fillMaxSize(),
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(tween(260)) { it * dir / 5 } + fadeIn(tween(260))) togetherWith
                        (slideOutHorizontally(tween(200)) { -it * dir / 5 } + fadeOut(tween(160)))
                },
                label = "tabs"
            ) { t ->
                when (t) {
                    0 -> CalendarScreen(vm)
                    1 -> PlanillaScreen(vm)
                    2 -> SummaryScreen(vm)
                    else -> PeopleScreen(vm)
                }
            }
        }
        AnimatedVisibility(visible = splash, exit = fadeOut(tween(450))) { Splash() }
    }
}

/* ---------- Piezas reutilizables ---------- */

@Composable
fun Avatar(name: String, color: Color, size: Dp = 36.dp) {
    Box(Modifier.size(size).background(color, CircleShape), contentAlignment = Alignment.Center) {
        Text(name.trim().take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun Dot(color: Color, size: Dp = 9.dp) {
    val s = remember { Animatable(0f) }
    LaunchedEffect(Unit) { s.animateTo(1f, spring(dampingRatio = 0.5f)) }
    Box(Modifier.size(size).graphicsLayer { scaleX = s.value; scaleY = s.value }.background(color, CircleShape))
}

@Composable
private fun animatedMoney(v: Double, short: Boolean = false): String {
    val a by animateFloatAsState(v.toFloat(), tween(600), label = "money")
    val fmt = { x: Double -> if (short) moneyShort(x) else money(x) }
    return if (a == v.toFloat()) fmt(v) else fmt(a.toDouble())
}

/** Tarjeta blanca con esquinas grandes y sombra suave. */
@Composable
fun SoftCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier, shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        content = content
    )
}

@Composable
fun StatPill(icon: ImageVector, label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.18f)).padding(10.dp)) {
        Icon(icon, null, Modifier.size(18.dp), tint = Color.White)
        Spacer(Modifier.height(6.dp))
        Text(value, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        Text(label, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun MiniStat(icon: ImageVector, label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(16.dp)).background(accent.copy(alpha = 0.12f)).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = accent)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
fun Line(label: String, value: String, bold: Boolean = false, icon: ImageVector? = null) {
    val w = if (bold) FontWeight.Bold else FontWeight.Normal
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Text(label, Modifier.weight(1f), fontWeight = w)
        Text(value, fontWeight = w)
    }
}

@Composable
fun MoneyLine(label: String, v: Double, bold: Boolean = false, icon: ImageVector? = null) =
    Line(label, animatedMoney(v), bold, icon)

/* ---------- Encabezado de mes ---------- */

@Composable
fun MonthHeader(vm: MainVM) {
    val m by vm.month.collectAsState()
    var pick by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(onClick = { vm.month.value = m.minusMonths(1) }) { Icon(Icons.Rounded.ChevronLeft, "Mes anterior") }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { pick = true }) {
                AnimatedContent(targetState = m, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) }, label = "title") {
                    Text(monthTitle(it), style = MaterialTheme.typography.titleLarge)
                }
            }
            AnimatedVisibility(visible = m != YearMonth.now()) {
                IconButton(onClick = { vm.month.value = YearMonth.now() }) { Icon(Icons.Rounded.Today, "Ir a hoy") }
            }
        }
        FilledTonalIconButton(onClick = { vm.month.value = m.plusMonths(1) }) { Icon(Icons.Rounded.ChevronRight, "Mes siguiente") }
    }
    if (pick) MonthPicker(m, { pick = false }) { vm.month.value = it; pick = false }
}

@Composable
fun MonthPicker(current: YearMonth, onDismiss: () -> Unit, onPick: (YearMonth) -> Unit) {
    var year by remember { mutableIntStateOf(current.year) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = { year-- }) { Icon(Icons.Rounded.ChevronLeft, "Año anterior") }
                Text(year.toString())
                IconButton(onClick = { year++ }) { Icon(Icons.Rounded.ChevronRight, "Año siguiente") }
            }
        },
        text = {
            Column {
                for (r in 0..3) Row(Modifier.fillMaxWidth()) {
                    for (c in 0..2) {
                        val mo = r * 3 + c + 1
                        val sel = year == current.year && mo == current.monthValue
                        val label = java.time.Month.of(mo).getDisplayName(TextStyle.SHORT, AR).cap()
                        val mod = Modifier.weight(1f).padding(2.dp)
                        if (sel) FilledTonalButton(onClick = { onPick(YearMonth.of(year, mo)) }, modifier = mod, contentPadding = PaddingValues(0.dp)) { Text(label) }
                        else TextButton(onClick = { onPick(YearMonth.of(year, mo)) }, modifier = mod, contentPadding = PaddingValues(0.dp)) { Text(label) }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}

/* ---------- Calendario ---------- */

@Composable
fun CalendarScreen(vm: MainVM) {
    val m by vm.month.collectAsState()
    val persons by vm.persons.collectAsState()
    val days by vm.workdays.collectAsState()
    val selecting by vm.selecting.collectAsState()
    val selected by vm.selected.collectAsState()
    val assignees by vm.assignees.collectAsState()
    val specials by vm.specials.collectAsState()
    var sheetDate by remember { mutableStateOf<LocalDate?>(null) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp)) {
            AnimatedVisibility(visible = !selecting) {
                OutlinedButton(onClick = { vm.startSelecting(null) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.SelectAll, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Seleccionar varios días")
                }
            }
            MonthHeader(vm)
            Row(Modifier.fillMaxWidth()) {
                listOf("L", "M", "M", "J", "V", "S", "D").forEach {
                    Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            AnimatedContent(
                targetState = m,
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(tween(260)) { it * dir / 4 } + fadeIn(tween(260))) togetherWith
                        (slideOutHorizontally(tween(200)) { -it * dir / 4 } + fadeOut(tween(160)))
                },
                label = "month"
            ) { mm ->
                Column {
                    val offset = mm.atDay(1).dayOfWeek.value - 1
                    val len = mm.lengthOfMonth()
                    val rows = (offset + len + 6) / 7
                    for (r in 0 until rows) {
                        Row(Modifier.fillMaxWidth()) {
                            for (c in 0..6) {
                                val n = r * 7 + c - offset + 1
                                if (n in 1..len) {
                                    val d = mm.atDay(n)
                                    val e = d.toEpochDay()
                                    DayCell(
                                        d, persons, days.filter { it.date == e }, e in selected, specials.any { it.matches(d) }, Modifier.weight(1f),
                                        onClick = { if (selecting) vm.toggleSelected(e) else sheetDate = d },
                                        onLongClick = { if (selecting) vm.toggleSelected(e) else vm.startSelecting(e) }
                                    )
                                } else Spacer(Modifier.weight(1f).height(58.dp))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            TotalCard(vm, days)
            Spacer(Modifier.height(if (selecting) 480.dp else 16.dp))
        }

        AnimatedVisibility(
            visible = selecting,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(250)) { it } + fadeIn(tween(250)),
            exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(200))
        ) { SelectionPanel(vm, persons, m, selected, assignees) }
    }

    sheetDate?.let { d -> DaySheet(vm, d, persons, days.filter { it.date == d.toEpochDay() }) { sheetDate = null } }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DayCell(
    date: LocalDate, persons: List<Person>, works: List<Workday>, selected: Boolean, special: Boolean,
    modifier: Modifier, onClick: () -> Unit, onLongClick: () -> Unit
) {
    val has = works.isNotEmpty()
    val isToday = date == LocalDate.now()
    val cs = MaterialTheme.colorScheme
    val bg by animateColorAsState(
        when { selected -> cs.secondaryContainer; has -> cs.primaryContainer; else -> cs.surfaceContainer },
        tween(250), label = "cellBg"
    )
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier.height(58.dp).padding(2.dp).clip(shape).background(bg)
            .then(if (selected) Modifier.border(2.dp, cs.secondary, shape) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(24.dp).background(if (isToday) cs.primary else Color.Transparent, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    date.dayOfMonth.toString(),
                    color = if (isToday) cs.onPrimary else cs.onSurface,
                    fontWeight = if (has || isToday || selected) FontWeight.Bold else FontWeight.Normal,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (special) Icon(Icons.Rounded.Star, null, Modifier.size(11.dp), tint = cs.tertiary)
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            works.forEach { w ->
                key(w.personId) {
                    val i = persons.indexOfFirst { it.id == w.personId }
                    if (i >= 0) Dot(colorOf(i), 8.dp)
                }
            }
        }
    }
}

@Composable
fun SelectionPanel(vm: MainVM, persons: List<Person>, month: YearMonth, selected: Set<Long>, assignees: Set<Long>) {
    var showAmounts by remember { mutableStateOf(false) }
    var pay by remember { mutableStateOf("") }
    var exp by remember { mutableStateOf("") }
    val monthDays = (1..month.lengthOfMonth()).map { month.atDay(it) }
    val ready = selected.isNotEmpty() && assignees.isNotEmpty()

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp, shadowElevation = 16.dp,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 470.dp).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (selected.isEmpty()) "Tocá los días para elegirlos" else "${selected.size} días elegidos",
                    Modifier.weight(1f), style = MaterialTheme.typography.titleMedium
                )
                IconButton(onClick = { vm.exitSelecting() }) { Icon(Icons.Rounded.Close, "Salir de la selección") }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = { vm.setSelected(monthDays.filter { it.dayOfWeek.value <= 5 }.map { it.toEpochDay() }.toSet()) }, label = { Text("Lun a Vie") })
                AssistChip(onClick = { vm.setSelected(monthDays.filter { it.dayOfWeek.value >= 6 }.map { it.toEpochDay() }.toSet()) }, label = { Text("Sáb y Dom") })
                AssistChip(onClick = { vm.setSelected(monthDays.map { it.toEpochDay() }.toSet()) }, label = { Text("Todo el mes") })
                AssistChip(onClick = { vm.setSelected(emptySet()) }, label = { Text("Ninguno") })
            }
            Text("Todos los…", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DOW.forEachIndexed { idx, name ->
                    val ofDow = monthDays.filter { it.dayOfWeek.value == idx + 1 }.map { it.toEpochDay() }
                    val all = ofDow.isNotEmpty() && ofDow.all { it in selected }
                    FilterChip(
                        selected = all,
                        onClick = { vm.setSelected(if (all) selected - ofDow.toSet() else selected + ofDow) },
                        label = { Text(name) }
                    )
                }
            }
            Text("¿Quién va?", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                persons.forEachIndexed { i, p ->
                    FilterChip(
                        selected = p.id in assignees, onClick = { vm.toggleAssignee(p.id) }, label = { Text(p.name) },
                        leadingIcon = {
                            if (p.id in assignees) Icon(Icons.Rounded.Check, null, Modifier.size(18.dp))
                            else Box(Modifier.size(10.dp).background(colorOf(i), CircleShape))
                        }
                    )
                }
            }
            TextButton(onClick = { showAmounts = !showAmounts }, contentPadding = PaddingValues(horizontal = 4.dp)) {
                Icon(Icons.Rounded.Payments, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (showAmounts) "Ocultar montos" else "Fijar montos para estos días (opcional)")
            }
            AnimatedVisibility(visible = showAmounts) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(pay, { pay = it }, label = { Text("Pago por persona") }, singleLine = true,
                            leadingIcon = { Icon(Icons.Rounded.Payments, null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                        OutlinedTextField(exp, { exp = it }, label = { Text("Viático del día") }, singleLine = true,
                            leadingIcon = { Icon(Icons.Rounded.ReceiptLong, null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    }
                    Text(
                        "Pago: vacío usa el pago de la persona para ese día de la semana. Viático del día: total que se reparte entre los que van; vacío no lo cambia.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { pay.toAmount()?.let { vm.setPayOnExisting(it) } },
                            enabled = ready && pay.toAmount() != null
                        ) { Text("Solo cambiar pago") }
                        OutlinedButton(
                            onClick = { exp.toAmount()?.let { vm.setSelectedDayExpense(it) } },
                            enabled = selected.isNotEmpty() && exp.toAmount() != null
                        ) { Text("Solo fijar viático") }
                        TextButton(onClick = { vm.clearSelectedDayExpense() }, enabled = selected.isNotEmpty()) { Text("Viático estándar") }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.assign(pay.toAmount(), exp.toAmount()) }, enabled = ready, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.PersonAdd, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Asignar")
                }
                OutlinedButton(onClick = { vm.unassign() }, enabled = ready, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.PersonRemove, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Quitar")
                }
            }
        }
    }
}

@Composable
fun TotalCard(vm: MainVM, days: List<Workday>, title: String = "Total del mes") {
    val ov by vm.dayExpenses.collectAsState()
    val def by vm.defaultExpense.collectAsState()
    val pay = days.sumOf { it.pay }
    val counts = remember(days) { countsOf(days) }
    val exp = days.sumOf { expOf(it, counts, ov, def) }
    Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(BrandBrush).padding(20.dp)) {
        Text(title, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelLarge)
        Text(animatedMoney(pay + exp), color = Color.White, style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatPill(Icons.Rounded.CalendarMonth, "Días", days.size.toString(), Modifier.weight(1f))
            StatPill(Icons.Rounded.Payments, "Pago", animatedMoney(pay, short = true), Modifier.weight(1.3f))
            StatPill(Icons.Rounded.ReceiptLong, "Viáticos", animatedMoney(exp, short = true), Modifier.weight(1.3f))
        }
    }
}

/* ---------- Edición de un día ---------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DaySheet(vm: MainVM, date: LocalDate, persons: List<Person>, works: List<Workday>, onClose: () -> Unit) {
    val ov by vm.dayExpenses.collectAsState()
    val def by vm.defaultExpense.collectAsState()
    val e = date.toEpochDay()
    val dayTotal = ov[e] ?: def
    val n = works.size
    val specials by vm.specials.collectAsState()
    val sp = specials.firstOrNull { it.matches(date) }
    var specialDialog by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
            Text(dateTitle(date), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            AssistChip(
                onClick = { specialDialog = true },
                label = { Text(if (sp != null) "${sp.label} ×${fmtMult(sp.multiplier)}" else "Pago especial para este día") },
                leadingIcon = { Icon(Icons.Rounded.Star, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.tertiary) }
            )
            Spacer(Modifier.height(8.dp))
            if (specialDialog) SpecialDayDialog(
                initial = sp ?: SpecialDay(label = "Día especial", month = date.monthValue, day = date.dayOfMonth, year = date.year, multiplier = 2.0),
                onDismiss = { specialDialog = false },
                onDelete = { vm.deleteSpecial(it); specialDialog = false },
                onSave = { s, apply -> vm.saveSpecial(s, apply); specialDialog = false }
            )
            if (persons.isEmpty()) Text("Primero agregá personas en la pestaña Personas.")
            persons.forEachIndexed { i, p ->
                PersonDayRow(vm, date, p, colorOf(i), works.firstOrNull { it.personId == p.id })
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            var expText by remember(e) { mutableStateOf(ov[e]?.let { fmtInput(it) } ?: "") }
            OutlinedTextField(
                expText, { expText = it; vm.setDayExpense(e, it.toAmount()) },
                label = { Text("Viático del día (total)") },
                placeholder = { Text(fmtInput(def)) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.ReceiptLong, null) },
                supportingText = {
                    Text(
                        if (n > 0) "Se reparte entre $n: ${money(dayTotal / n)} cada uno. Vacío = viático estándar."
                        else "Todavía no hay nadie asignado. Vacío = viático estándar (${money(def)})."
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            MoneyLine("Total del día", works.sumOf { it.pay } + if (n > 0) dayTotal else 0.0, bold = true)
        }
    }
}

@Composable
fun PersonDayRow(vm: MainVM, date: LocalDate, p: Person, color: Color, w: Workday?) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { vm.toggle(p, date, w == null) }) {
        Checkbox(checked = w != null, onCheckedChange = { vm.toggle(p, date, it) })
        Avatar(p.name, color, 28.dp)
        Spacer(Modifier.width(10.dp))
        Text(p.name, style = MaterialTheme.typography.titleMedium)
    }
    AnimatedVisibility(visible = w != null) {
        if (w != null) {
            var pay by remember(w.id) { mutableStateOf(fmtInput(w.pay)) }
            var note by remember(w.id) { mutableStateOf(w.note) }
            fun save() = vm.saveWorkday(w.copy(pay = pay.toAmount() ?: 0.0, note = note))
            Row(Modifier.padding(start = 12.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(pay, { pay = it; save() }, label = { Text("Pago") }, singleLine = true,
                    leadingIcon = { Icon(Icons.Rounded.Payments, null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                OutlinedTextField(note, { note = it; save() }, label = { Text("Lugar / nota") }, singleLine = true,
                    leadingIcon = { Icon(Icons.Rounded.Place, null) }, modifier = Modifier.weight(1f))
            }
        }
    }
}

/* ---------- Resumen ---------- */

@Composable
fun SummaryScreen(vm: MainVM) {
    val persons by vm.persons.collectAsState()
    val monthDays by vm.workdays.collectAsState()
    val allDays by vm.allWorkdays.collectAsState()
    val ov by vm.dayExpenses.collectAsState()
    val def by vm.defaultExpense.collectAsState()
    var all by rememberSaveable { mutableStateOf(false) }
    var export by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    val days = if (all) allDays else monthDays
    val counts = remember(days) { countsOf(days) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selected = !all, onClick = { all = false }, label = { Text("Este mes") })
            Spacer(Modifier.width(8.dp))
            FilterChip(selected = all, onClick = { all = true }, label = { Text("Todo el historial") })
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { export = true }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.FileDownload, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Exportar Excel")
            }
            OutlinedButton(onClick = { showImport = true }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.FileUpload, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Importar")
            }
        }
        if (!all) MonthHeader(vm) else Spacer(Modifier.height(12.dp))
        TotalCard(vm, days, if (all) "Total de todos los días" else "Total del mes")
        Spacer(Modifier.height(16.dp))
        persons.forEachIndexed { i, p ->
            val ws = days.filter { it.personId == p.id }
            val pay = ws.sumOf { it.pay }
            val exp = ws.sumOf { expOf(it, counts, ov, def) }
            SoftCard(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(p.name, colorOf(i), 46.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (ws.size == 1) "1 día" else "${ws.size} días",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(animatedMoney(pay + exp), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MiniStat(Icons.Rounded.Payments, "Pago", animatedMoney(pay, short = true), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                        MiniStat(Icons.Rounded.ReceiptLong, "Viáticos", animatedMoney(exp, short = true), MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
                    }
                    if (all && ws.isNotEmpty()) {
                        HorizontalDivider(Modifier.padding(vertical = 12.dp))
                        Text("Mes por mes", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(4.dp))
                        ws.groupBy { YearMonth.from(LocalDate.ofEpochDay(it.date)) }
                            .entries.sortedByDescending { it.key }
                            .forEach { (ym, list) ->
                                val mp = list.sumOf { it.pay }
                                val me = list.sumOf { expOf(it, counts, ov, def) }
                                Line(monthTitle(ym) + " · " + list.size + (if (list.size == 1) " día" else " días"), money(mp + me))
                            }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    if (export) ExportDialog(vm, startAll = all) { export = false }
    if (showImport) ImportDialog(vm) { showImport = false }
}

/* ---------- Personas ---------- */

@Composable
fun PeopleScreen(vm: MainVM) {
    val persons by vm.persons.collectAsState()
    var editing by remember { mutableStateOf<Person?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Person?>(null) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                val def by vm.defaultExpense.collectAsState()
                var txt by remember { mutableStateOf(fmtInput(def)) }
                SoftCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.ReceiptLong, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.width(8.dp))
                            Text("Viático estándar por día", style = MaterialTheme.typography.titleSmall)
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            txt, { txt = it; vm.setDefaultExpense(it.toAmount() ?: 0.0) },
                            label = { Text("Total por día") }, singleLine = true,
                            leadingIcon = { Icon(Icons.Rounded.Payments, null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Se reparte entre las personas que van ese día: si van los dos, cada uno la mitad. Cada día se puede cambiar.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            item { SpecialDaysCard(vm) }
            if (persons.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.Group, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.height(12.dp))
                    Text("Todavía no hay personas", style = MaterialTheme.typography.titleMedium)
                    Text("Agregá a quien trabaja para empezar a marcar días.", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
            items(persons, key = { it.id }) { p ->
                val i = persons.indexOf(p)
                val rates = p.weekdayRates().mapIndexedNotNull { k, r -> r?.let { "${DOW[k]} ${moneyShort(it)}" } }
                SoftCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(p.name, colorOf(i), 48.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.titleMedium)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Payments, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(4.dp))
                                Text("Base " + money(p.dailyPay) + "/día", style = MaterialTheme.typography.bodySmall)
                            }
                            if (rates.isNotEmpty()) Text(
                                rates.joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { editing = p }) { Icon(Icons.Rounded.Edit, "Editar") }
                        IconButton(onClick = { deleting = p }) { Icon(Icons.Rounded.Delete, "Borrar") }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { adding = true },
            icon = { Icon(Icons.Rounded.PersonAdd, null) },
            text = { Text("Agregar") },
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onSecondary,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        )
    }

    if (adding) PersonDialog(null, { adding = false }) { p, _ -> vm.savePerson(p, false); adding = false }
    editing?.let { e -> PersonDialog(e, { editing = null }) { p, apply -> vm.savePerson(p, apply); editing = null } }
    deleting?.let { d ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            icon = { Icon(Icons.Rounded.Delete, null) },
            title = { Text("¿Borrar a ${d.name}?") },
            text = { Text("También se borran todos los días cargados de esta persona.") },
            confirmButton = { TextButton(onClick = { vm.deletePerson(d); deleting = null }) { Text("Borrar") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
fun PersonDialog(p: Person?, onDismiss: () -> Unit, onSave: (Person, Boolean) -> Unit) {
    var name by remember { mutableStateOf(p?.name ?: "") }
    var pay by remember { mutableStateOf(fmtInput(p?.dailyPay ?: 0.0)) }
    val rates = remember {
        mutableStateListOf<String>().also { l ->
            (p?.weekdayRates() ?: List<Double?>(7) { null }).forEach { l.add(if (it == null) "" else fmtInput(it)) }
        }
    }
    var apply by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(if (p == null) Icons.Rounded.PersonAdd else Icons.Rounded.Edit, null) },
        title = { Text(if (p == null) "Nueva persona" else "Editar persona") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, singleLine = true,
                    leadingIcon = { Icon(Icons.Rounded.Person, null) })
                OutlinedTextField(pay, { pay = it }, label = { Text("Pago base por día") }, singleLine = true,
                    leadingIcon = { Icon(Icons.Rounded.Payments, null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                Text("Pago según el día de la semana", style = MaterialTheme.typography.titleSmall)
                Text("Por ejemplo, todos los viernes un monto y todos los sábados otro. Vacío = pago base.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                DOW.indices.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { k ->
                            OutlinedTextField(
                                rates[k], { rates[k] = it }, label = { Text(DOW[k]) }, singleLine = true,
                                placeholder = { Text(pay) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                if (p != null) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { apply = !apply }) {
                    Checkbox(apply, { apply = it })
                    Text("Aplicar estos pagos también a los días ya cargados", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                onSave(
                    (p ?: Person(name = "")).copy(
                        name = name.trim(), dailyPay = pay.toAmount() ?: 0.0,
                        weekdayPays = rates.joinToString(",") { it.toAmount()?.toString() ?: "" }
                    ),
                    apply
                )
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

/* ---------- Planilla: cada día con su pago y su viático editable ---------- */

@Composable
fun PlanillaScreen(vm: MainVM) {
    val persons by vm.persons.collectAsState()
    val days by vm.workdays.collectAsState()
    val ov by vm.dayExpenses.collectAsState()
    val def by vm.defaultExpense.collectAsState()
    var sheetDate by remember { mutableStateOf<LocalDate?>(null) }
    val dates = remember(days) { days.map { it.date }.distinct().sorted() }
    val specials by vm.specials.collectAsState()

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { MonthHeader(vm) }
        LazyColumn(
            Modifier.weight(1f), contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (dates.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.TableChart, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.height(12.dp))
                    Text("No hay días cargados en este mes", style = MaterialTheme.typography.titleMedium)
                    Text("Marcalos en el Calendario y acá les ponés el viático de cada uno.", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
            items(dates, key = { it }) { e ->
                val d = LocalDate.ofEpochDay(e)
                val works = days.filter { it.date == e }
                val n = works.size
                val total = ov[e] ?: def
                SoftCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Row(Modifier.fillMaxWidth().clickable { sheetDate = d }, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(d.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", AR)).cap(), style = MaterialTheme.typography.titleSmall)
                                specials.firstOrNull { it.matches(d) }?.let { sp ->
                                    Text("★ ${sp.label} ×${fmtMult(sp.multiplier)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                                }
                                Text(
                                    works.joinToString(" · ") { w -> (persons.firstOrNull { it.id == w.personId }?.name ?: "?") + " " + moneyShort(w.pay) },
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            works.forEach { w ->
                                val i = persons.indexOfFirst { it.id == w.personId }
                                if (i >= 0) { Avatar(persons[i].name, colorOf(i), 28.dp); Spacer(Modifier.width(4.dp)) }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        var txt by remember(e) { mutableStateOf(ov[e]?.let { fmtInput(it) } ?: "") }
                        OutlinedTextField(
                            txt, { txt = it; vm.setDayExpense(e, it.toAmount()) },
                            label = { Text("Viático del día (total)") },
                            placeholder = { Text(fmtInput(def)) },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Rounded.ReceiptLong, null) },
                            supportingText = {
                                Text("Entre $n: ${money(total / n)} cada uno" + if (ov[e] == null) " (viático estándar)" else "")
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            if (dates.isNotEmpty()) item { TotalCard(vm, days) }
        }
    }
    sheetDate?.let { d -> DaySheet(vm, d, persons, days.filter { it.date == d.toEpochDay() }) { sheetDate = null } }
}

@Composable
fun ExportDialog(vm: MainVM, startAll: Boolean, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val m by vm.month.collectAsState()
    var all by remember { mutableStateOf(startAll) }
    val mime = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    val fileName = if (all) "Jornadas_historial.xlsx" else "Jornadas_$m.xlsx"
    val period = if (all) "Todo el historial" else monthTitle(m)
    val bytes = { vm.buildExport(if (all) null else m, period) }

    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mime)) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(bytes()) }
                Toast.makeText(ctx, "Archivo guardado", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(ctx, "No se pudo guardar el archivo", Toast.LENGTH_LONG).show()
            }
        }
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.FileDownload, null) },
        title = { Text("Exportar a Excel") },
        text = {
            Column {
                Text(
                    "Un archivo con una hoja por persona (cada día con su pago, viático y total), más un resumen general.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth().clickable { all = false }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = !all, onClick = { all = false })
                    Text("Solo " + monthTitle(m))
                }
                Row(Modifier.fillMaxWidth().clickable { all = true }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = all, onClick = { all = true })
                    Text("Todo el historial")
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = {
                    try {
                        val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
                        val f = File(dir, fileName)
                        f.writeBytes(bytes())
                        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = mime
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        ctx.startActivity(Intent.createChooser(send, "Compartir planilla"))
                    } catch (e: Exception) {
                        Toast.makeText(ctx, "No se pudo compartir el archivo", Toast.LENGTH_LONG).show()
                    }
                    onDismiss()
                }) { Text("Compartir") }
                TextButton(onClick = { saver.launch(fileName) }) { Text("Guardar") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

/* ---------- Días especiales (pago multiplicado) ---------- */

@Composable
fun SpecialDaysCard(vm: MainVM) {
    val specials by vm.specials.collectAsState()
    var editing by remember { mutableStateOf<SpecialDay?>(null) }
    SoftCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Star, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.width(8.dp))
                Text("Días especiales", style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Feriados u otros días con pago distinto, por ejemplo doble. Se aplica solo al marcar esos días.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            specials.sortedWith(compareBy({ it.month }, { it.day }, { it.year })).forEach { s ->
                Row(Modifier.fillMaxWidth().clickable { editing = s }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(s.label, style = MaterialTheme.typography.titleSmall)
                        Text(specialDateText(s), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                        Text(
                            "×" + fmtMult(s.multiplier), Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.onTertiaryContainer, fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = { vm.deleteSpecial(s) }) { Icon(Icons.Rounded.Delete, "Quitar") }
                }
            }
            if (specials.isEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = { editing = SpecialDay(label = "Navidad", month = 12, day = 25, year = 0, multiplier = 2.0) }, label = { Text("+ Navidad ×2") })
                AssistChip(onClick = { editing = SpecialDay(label = "Año Nuevo", month = 1, day = 1, year = 0, multiplier = 2.0) }, label = { Text("+ Año Nuevo ×2") })
            }
            TextButton(onClick = {
                val t = LocalDate.now()
                editing = SpecialDay(label = "Día especial", month = t.monthValue, day = t.dayOfMonth, year = 0, multiplier = 2.0)
            }) {
                Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Agregar día especial")
            }
        }
    }
    editing?.let { s ->
        SpecialDayDialog(
            initial = s, onDismiss = { editing = null },
            onDelete = { vm.deleteSpecial(it); editing = null },
            onSave = { sd, apply -> vm.saveSpecial(sd, apply); editing = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpecialDayDialog(initial: SpecialDay, onDismiss: () -> Unit, onDelete: (SpecialDay) -> Unit, onSave: (SpecialDay, Boolean) -> Unit) {
    var label by remember { mutableStateOf(initial.label) }
    var date by remember {
        val y = if (initial.year == 0) LocalDate.now().year else initial.year
        val ym = YearMonth.of(y, initial.month)
        mutableStateOf(ym.atDay(minOf(initial.day, ym.lengthOfMonth())))
    }
    var yearly by remember { mutableStateOf(initial.year == 0) }
    var mult by remember { mutableStateOf(fmtMult(initial.multiplier)) }
    var apply by remember { mutableStateOf(true) }
    var picker by remember { mutableStateOf(false) }
    val m = mult.toAmount()

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Star, null) },
        title = { Text(if (initial.id == 0L) "Día especial" else "Editar día especial") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(label, { label = it }, label = { Text("Nombre (ej. Navidad)") }, singleLine = true)
                OutlinedButton(onClick = { picker = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.CalendarMonth, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(date.format(DateTimeFormatter.ofPattern(if (yearly) "d 'de' MMMM" else "d 'de' MMMM yyyy", AR)))
                }
                Row(Modifier.fillMaxWidth().clickable { yearly = !yearly }, verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = yearly, onCheckedChange = { yearly = it })
                    Spacer(Modifier.width(10.dp))
                    Text("Se repite todos los años")
                }
                Text("El pago se multiplica por", style = MaterialTheme.typography.titleSmall)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1.5, 2.0, 3.0).forEach { k ->
                        FilterChip(selected = m == k, onClick = { mult = fmtMult(k) }, label = { Text("×" + fmtMult(k)) })
                    }
                }
                OutlinedTextField(
                    mult, { mult = it }, label = { Text("Multiplicador") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                Row(Modifier.fillMaxWidth().clickable { apply = !apply }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(apply, { apply = it })
                    Text("Aplicar al pago de los días ya cargados en esa fecha", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = m != null && m > 0.0, onClick = {
                onSave(
                    SpecialDay(
                        id = initial.id, label = label.trim().ifBlank { "Día especial" },
                        month = date.monthValue, day = date.dayOfMonth, year = if (yearly) 0 else date.year,
                        multiplier = m ?: 2.0
                    ),
                    apply
                )
            }) { Text("Guardar") }
        },
        dismissButton = {
            Row {
                if (initial.id != 0L) TextButton(onClick = { onDelete(initial) }) { Text("Quitar") }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        }
    )

    if (picker) {
        val st = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { picker = false },
            confirmButton = {
                TextButton(onClick = {
                    st.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    picker = false
                }) { Text("Listo") }
            },
            dismissButton = { TextButton(onClick = { picker = false }) { Text("Cancelar") } }
        ) { DatePicker(state = st) }
    }
}

/* ---------- Importar una planilla exportada por la app ---------- */

@Composable
fun ImportDialog(vm: MainVM, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            onDismiss()
        } else {
            val data = try { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } } catch (e: Exception) { null }
            if (data == null) {
                Toast.makeText(ctx, "No se pudo abrir el archivo", Toast.LENGTH_LONG).show()
                onDismiss()
            } else {
                vm.importXlsx(data) { msg -> Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show(); onDismiss() }
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.FileUpload, null) },
        title = { Text("Importar desde Excel") },
        text = {
            Text(
                "Elegí una planilla que exportó esta app. Se cargan los días con su pago y viático, las personas con sus tarifas y los días especiales. " +
                    "Lo que ya existe en esas fechas se actualiza; no se borra nada.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = { TextButton(onClick = { picker.launch(arrayOf("*/*")) }) { Text("Elegir archivo") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
