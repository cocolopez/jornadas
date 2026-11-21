package com.perez.jornadas

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val AR = Locale("es", "AR")
private val palette = listOf(
    Color(0xFF1E88E5), Color(0xFFE53935), Color(0xFF43A047),
    Color(0xFFFB8C00), Color(0xFF8E24AA), Color(0xFF00897B)
)
private fun colorOf(i: Int) = palette[i % palette.size]
private fun money(d: Double): String = NumberFormat.getCurrencyInstance(AR).format(d)
private fun String.toAmount(): Double? = trim().replace(',', '.').toDoubleOrNull()
private fun fmtInput(d: Double): String = when {
    d == 0.0 -> ""
    d % 1.0 == 0.0 -> d.toLong().toString()
    else -> d.toString()
}
private fun String.cap() = replaceFirstChar { it.uppercase() }
private fun monthTitle(m: YearMonth) = m.month.getDisplayName(TextStyle.FULL, AR).cap() + " " + m.year
private fun dateTitle(d: LocalDate) = d.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM yyyy", AR)).cap()

@Composable
fun AppRoot(vm: MainVM) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshToday() }
    val tabs = listOf("Calendario" to Icons.Default.DateRange, "Resumen" to Icons.Default.CheckCircle, "Personas" to Icons.Default.Person)
    Scaffold(bottomBar = {
        NavigationBar {
            tabs.forEachIndexed { i, (label, icon) ->
                NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(icon, null) }, label = { Text(label) })
            }
        }
    }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> CalendarScreen(vm)
                1 -> SummaryScreen(vm)
                else -> PeopleScreen(vm)
            }
        }
    }
}

/* ---------- Encabezado de mes ---------- */

@Composable
fun MonthHeader(vm: MainVM) {
    val m by vm.month.collectAsState()
    var pick by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        IconButton(onClick = { vm.month.value = m.minusMonths(1) }) { Icon(Icons.Default.ArrowBack, "Mes anterior") }
        TextButton(onClick = { pick = true }) { Text(monthTitle(m), style = MaterialTheme.typography.titleLarge) }
        IconButton(onClick = { vm.month.value = m.plusMonths(1) }) { Icon(Icons.Default.ArrowForward, "Mes siguiente") }
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
                IconButton(onClick = { year-- }) { Icon(Icons.Default.ArrowBack, "Año anterior") }
                Text(year.toString())
                IconButton(onClick = { year++ }) { Icon(Icons.Default.ArrowForward, "Año siguiente") }
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
    val quick by vm.quick.collectAsState()
    var selected by remember { mutableStateOf<LocalDate?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 8.dp)) {
        TodayCard(vm, persons)
        QuickBar(vm, persons, quick)
        MonthHeader(vm)
        Row(Modifier.fillMaxWidth()) {
            listOf("L", "M", "M", "J", "V", "S", "D").forEach {
                Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
            }
        }
        val offset = m.atDay(1).dayOfWeek.value - 1
        val len = m.lengthOfMonth()
        val rows = (offset + len + 6) / 7
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (c in 0..6) {
                    val n = r * 7 + c - offset + 1
                    if (n in 1..len) {
                        val d = m.atDay(n)
                        val dayWorks = days.filter { it.date == d.toEpochDay() }
                        DayCell(
                            d, persons, dayWorks, Modifier.weight(1f),
                            onClick = { if (quick.isEmpty()) selected = d else vm.quickMark(d, dayWorks) },
                            onLongClick = { selected = d }
                        )
                    } else Spacer(Modifier.weight(1f).height(60.dp))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        // Leyenda
        persons.forEachIndexed { i, p ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                Box(Modifier.size(12.dp).background(colorOf(i), CircleShape)); Spacer(Modifier.width(8.dp)); Text(p.name)
            }
        }
        Spacer(Modifier.height(12.dp))
        TotalCard(days)
        Spacer(Modifier.height(16.dp))
    }

    selected?.let { d -> DaySheet(vm, d, persons, days.filter { it.date == d.toEpochDay() }) { selected = null } }
}

@Composable
fun TodayCard(vm: MainVM, persons: List<Person>) {
    val today by vm.today.collectAsState()
    val works by vm.todayWorks.collectAsState()
    var sheet by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Hoy: " + dateTitle(today), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                IconButton(onClick = { sheet = true }) { Icon(Icons.Default.Edit, "Editar montos de hoy") }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                persons.forEach { p ->
                    val on = works.any { it.personId == p.id }
                    FilterChip(
                        selected = on, onClick = { vm.toggle(p, today, !on) }, label = { Text(p.name) },
                        leadingIcon = if (on) ({ Icon(Icons.Default.Check, null) }) else null
                    )
                }
            }
        }
    }
    if (sheet) DaySheet(vm, today, persons, works) { sheet = false }
}

@Composable
fun QuickBar(vm: MainVM, persons: List<Person>, quick: Set<Long>) {
    Text("Marcado rápido: elegí quién y tocá los días", style = MaterialTheme.typography.labelMedium)
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        persons.forEachIndexed { i, p ->
            FilterChip(
                selected = p.id in quick, onClick = { vm.toggleQuick(p.id) }, label = { Text(p.name) },
                leadingIcon = { Box(Modifier.size(10.dp).background(colorOf(i), CircleShape)) }
            )
        }
    }
    Text(
        if (quick.isEmpty()) "Sin selección: tocá un día para editarlo."
        else "Tocá un día para marcar o desmarcar. Mantené apretado para editar montos.",
        style = MaterialTheme.typography.bodySmall
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DayCell(date: LocalDate, persons: List<Person>, works: List<Workday>, modifier: Modifier, onClick: () -> Unit, onLongClick: () -> Unit) {
    val has = works.isNotEmpty()
    Column(
        modifier.height(60.dp).padding(1.dp)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), MaterialTheme.shapes.small)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(4.dp)
    ) {
        Text(date.dayOfMonth.toString(), fontWeight = if (has) FontWeight.Bold else FontWeight.Normal, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            works.forEach { w ->
                val i = persons.indexOfFirst { it.id == w.personId }
                if (i >= 0) Box(Modifier.size(9.dp).background(colorOf(i), CircleShape))
            }
        }
    }
}

@Composable
fun TotalCard(days: List<Workday>) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Total del mes", style = MaterialTheme.typography.titleMedium)
            Line("Días cargados", days.size.toString())
            Line("Pago", money(days.sumOf { it.pay }))
            Line("Viáticos", money(days.sumOf { it.expenses }))
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            Line("Total", money(days.sumOf { it.pay + it.expenses }), bold = true)
        }
    }
}

@Composable
fun Line(label: String, value: String, bold: Boolean = false) {
    val w = if (bold) FontWeight.Bold else FontWeight.Normal
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontWeight = w); Text(value, fontWeight = w)
    }
}

/* ---------- Edición de un día ---------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DaySheet(vm: MainVM, date: LocalDate, persons: List<Person>, works: List<Workday>, onClose: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
            Text(dateTitle(date), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            if (persons.isEmpty()) Text("Primero agregá personas en la pestaña Personas.")
            persons.forEachIndexed { i, p ->
                PersonDayRow(vm, date, p, colorOf(i), works.firstOrNull { it.personId == p.id })
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Line("Total del día", money(works.sumOf { it.pay + it.expenses }), bold = true)
        }
    }
}

@Composable
fun PersonDayRow(vm: MainVM, date: LocalDate, p: Person, color: Color, w: Workday?) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { vm.toggle(p, date, w == null) }) {
        Checkbox(checked = w != null, onCheckedChange = { vm.toggle(p, date, it) })
        Box(Modifier.size(10.dp).background(color, CircleShape)); Spacer(Modifier.width(8.dp))
        Text(p.name, style = MaterialTheme.typography.titleMedium)
    }
    if (w != null) {
        var pay by remember(w.id) { mutableStateOf(fmtInput(w.pay)) }
        var exp by remember(w.id) { mutableStateOf(fmtInput(w.expenses)) }
        var note by remember(w.id) { mutableStateOf(w.note) }
        fun save() = vm.saveWorkday(w.copy(pay = pay.toAmount() ?: 0.0, expenses = exp.toAmount() ?: 0.0, note = note))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(start = 12.dp, bottom = 4.dp)) {
            OutlinedTextField(pay, { pay = it; save() }, label = { Text("Pago") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
            OutlinedTextField(exp, { exp = it; save() }, label = { Text("Viáticos") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
        }
        OutlinedTextField(note, { note = it; save() }, label = { Text("Lugar / nota") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, bottom = 8.dp))
    }
}

/* ---------- Resumen ---------- */

@Composable
fun SummaryScreen(vm: MainVM) {
    val persons by vm.persons.collectAsState()
    val days by vm.workdays.collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
        MonthHeader(vm)
        persons.forEachIndexed { i, p ->
            val ws = days.filter { it.personId == p.id }
            Card(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(12.dp).background(colorOf(i), CircleShape)); Spacer(Modifier.width(8.dp))
                        Text(p.name, style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(6.dp))
                    Line("Días trabajados", ws.size.toString())
                    Line("Pago", money(ws.sumOf { it.pay }))
                    Line("Viáticos", money(ws.sumOf { it.expenses }))
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    Line("Total", money(ws.sumOf { it.pay + it.expenses }), bold = true)
                }
            }
        }
        TotalCard(days)
        Spacer(Modifier.height(16.dp))
    }
}

/* ---------- Personas ---------- */

@Composable
fun PeopleScreen(vm: MainVM) {
    val persons by vm.persons.collectAsState()
    var editing by remember { mutableStateOf<Person?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Person?>(null) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(persons, key = { it.id }) { p ->
                val i = persons.indexOf(p)
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(12.dp).background(colorOf(i), CircleShape)); Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.titleMedium)
                            Text("Pago/día: ${money(p.dailyPay)}  ·  Viáticos/día: ${money(p.dailyExpenses)}", style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { editing = p }) { Icon(Icons.Default.Edit, "Editar") }
                        IconButton(onClick = { deleting = p }) { Icon(Icons.Default.Delete, "Borrar") }
                    }
                }
            }
        }
        FloatingActionButton(onClick = { adding = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            Icon(Icons.Default.Add, "Agregar persona")
        }
    }

    if (adding) PersonDialog(null, { adding = false }) { p, _ -> vm.savePerson(p, false); adding = false }
    editing?.let { e -> PersonDialog(e, { editing = null }) { p, apply -> vm.savePerson(p, apply); editing = null } }
    deleting?.let { d ->
        AlertDialog(
            onDismissRequest = { deleting = null },
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
    var exp by remember { mutableStateOf(fmtInput(p?.dailyExpenses ?: 0.0)) }
    var apply by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (p == null) "Nueva persona" else "Editar persona") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, singleLine = true)
                OutlinedTextField(pay, { pay = it }, label = { Text("Pago por día") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(exp, { exp = it }, label = { Text("Viáticos por día") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                if (p != null) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { apply = !apply }) {
                    Checkbox(apply, { apply = it })
                    Text("Aplicar estas tarifas también a los días ya cargados", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                onSave((p ?: Person(name = "")).copy(name = name.trim(), dailyPay = pay.toAmount() ?: 0.0, dailyExpenses = exp.toAmount() ?: 0.0), apply)
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
