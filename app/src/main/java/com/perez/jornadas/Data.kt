package com.perez.jornadas

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth

@Entity(tableName = "person")
data class Person(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val dailyPay: Double = 0.0,       // lo que te pagan por día
    val dailyExpenses: Double = 0.0,  // (sin uso: el viático ahora es un total por día)
    val weekdayPays: String = ""      // pago por día de la semana, lun..dom separados por coma; vacío = pago base
)

/** Pagos por día de la semana (lunes=0 ... domingo=6). null = usa el pago base. */
fun Person.weekdayRates(): List<Double?> {
    val parts = weekdayPays.split(",")
    return (0..6).map { parts.getOrNull(it)?.trim()?.toDoubleOrNull() }
}
/** Pago que le toca a esta persona en una fecha: el del día de la semana si lo hay, si no el base. */
fun Person.payFor(date: LocalDate): Double = weekdayRates()[date.dayOfWeek.value - 1] ?: dailyPay

@Entity(
    tableName = "workday",
    indices = [Index(value = ["personId", "date"], unique = true)],
    foreignKeys = [ForeignKey(
        entity = Person::class, parentColumns = ["id"],
        childColumns = ["personId"], onDelete = ForeignKey.CASCADE
    )]
)
data class Workday(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personId: Long,
    val date: Long,            // LocalDate.toEpochDay()
    val pay: Double,
    val expenses: Double,
    val note: String = ""
)

/** Día con pago especial (por ejemplo Navidad ×2). year = 0 significa "todos los años". */
@Entity(tableName = "special_day")
data class SpecialDay(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val month: Int,
    val day: Int,
    val year: Int = 0,
    val multiplier: Double = 2.0
)
fun SpecialDay.matches(d: LocalDate): Boolean = d.monthValue == month && d.dayOfMonth == day && (year == 0 || d.year == year)

/** Viático total de un día (se reparte entre las personas que van). Si no hay fila, vale el viático estándar. */
@Entity(tableName = "day_expense")
data class DayExpense(@PrimaryKey val date: Long, val amount: Double)

@Dao
interface AppDao {
    @Query("SELECT * FROM person ORDER BY name") fun persons(): Flow<List<Person>>
    @Query("SELECT COUNT(*) FROM person") suspend fun personCount(): Int
    @Insert suspend fun insertPerson(p: Person): Long
    @Update suspend fun updatePerson(p: Person)
    @Delete suspend fun deletePerson(p: Person)

    @Query("SELECT * FROM workday WHERE date BETWEEN :from AND :to")
    fun workdays(from: Long, to: Long): Flow<List<Workday>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertWorkday(w: Workday)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertWorkdays(list: List<Workday>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertWorkdays(list: List<Workday>)
    @Query("SELECT * FROM workday WHERE personId = :pid") suspend fun workdaysOfPerson(pid: Long): List<Workday>
    @Query("SELECT * FROM special_day ORDER BY month, day") fun specialDays(): Flow<List<SpecialDay>>
    @Insert suspend fun insertSpecial(s: SpecialDay): Long
    @Update suspend fun updateSpecial(s: SpecialDay)
    @Delete suspend fun deleteSpecial(s: SpecialDay)
    @Query("SELECT * FROM workday ORDER BY date") fun allWorkdays(): Flow<List<Workday>>
    @Query("SELECT * FROM day_expense") fun dayExpenses(): Flow<List<DayExpense>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun setDayExpense(e: DayExpense)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun setDayExpenses(list: List<DayExpense>)
    @Query("DELETE FROM day_expense WHERE date = :date") suspend fun clearDayExpense(date: Long)
    @Query("DELETE FROM day_expense WHERE date IN (:dates)") suspend fun clearDayExpenses(dates: List<Long>)
    @Query("SELECT * FROM workday WHERE date IN (:dates)") suspend fun workdaysOn(dates: List<Long>): List<Workday>
    @Query("DELETE FROM workday WHERE personId = :pid AND date = :date")
    suspend fun deleteWorkday(pid: Long, date: Long)
    @Query("UPDATE workday SET pay = :pay, expenses = :exp WHERE personId = :pid")
    suspend fun applyRates(pid: Long, pay: Double, exp: Double)
}

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS day_expense (date INTEGER NOT NULL, amount REAL NOT NULL, PRIMARY KEY(date))")
    }
}

private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE person ADD COLUMN weekdayPays TEXT NOT NULL DEFAULT ''")
    }
}

private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS special_day (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, label TEXT NOT NULL, month INTEGER NOT NULL, day INTEGER NOT NULL, year INTEGER NOT NULL, multiplier REAL NOT NULL)")
    }
}

@Database(entities = [Person::class, Workday::class, DayExpense::class, SpecialDay::class], version = 4, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): AppDao
    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(c: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(c.applicationContext, AppDb::class.java, "jornadas.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build().also { inst = it }
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainVM(app: Application) : AndroidViewModel(app) {
    private val dao = AppDb.get(app).dao()
    val month = MutableStateFlow(YearMonth.now())   // siempre arranca en el mes actual
    val persons = dao.persons().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val workdays = month.flatMapLatest { m ->
        dao.workdays(m.atDay(1).toEpochDay(), m.atEndOfMonth().toEpochDay())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ----- Días especiales (pago multiplicado) -----
    val specials = dao.specialDays().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    /** Pago de una persona en una fecha: tarifa del día de la semana por el multiplicador del día especial (si lo hay). */
    fun payOf(p: Person, d: LocalDate): Double = p.payFor(d) * (specials.value.firstOrNull { it.matches(d) }?.multiplier ?: 1.0)

    fun saveSpecial(sd: SpecialDay, applyToExisting: Boolean) = viewModelScope.launch {
        val id = if (sd.id == 0L) dao.insertSpecial(sd) else { dao.updateSpecial(sd); sd.id }
        if (applyToExisting) {
            val saved = sd.copy(id = id)
            val byId = persons.value.associateBy { it.id }
            val list = allWorkdays.value.filter { saved.matches(LocalDate.ofEpochDay(it.date)) }.mapNotNull { w ->
                byId[w.personId]?.let { p -> w.copy(pay = p.payFor(LocalDate.ofEpochDay(w.date)) * saved.multiplier) }
            }
            if (list.isNotEmpty()) dao.upsertWorkdays(list)
        }
    }

    fun deleteSpecial(sd: SpecialDay) = viewModelScope.launch { dao.deleteSpecial(sd) }

    // ----- Viáticos: un total por día, repartido entre los que van -----
    private val prefs = app.getSharedPreferences("app", Context.MODE_PRIVATE)
    val defaultExpense = MutableStateFlow(prefs.getString("defExp", "0")?.toDoubleOrNull() ?: 0.0)
    fun setDefaultExpense(v: Double) { defaultExpense.value = v; prefs.edit().putString("defExp", v.toString()).apply() }
    val dayExpenses = dao.dayExpenses().map { l -> l.associate { it.date to it.amount } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    fun setDayExpense(date: Long, amount: Double?) = viewModelScope.launch {
        if (amount == null) dao.clearDayExpense(date) else dao.setDayExpense(DayExpense(date, amount))
    }
    fun setSelectedDayExpense(amount: Double) = viewModelScope.launch {
        dao.setDayExpenses(selected.value.map { DayExpense(it, amount) })
    }
    fun clearSelectedDayExpense() = viewModelScope.launch { dao.clearDayExpenses(selected.value.toList()) }

    // ----- Todo el historial + exportar -----
    val allWorkdays = dao.allWorkdays().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Arma el .xlsx. month = null exporta todo el historial. */
    fun buildExport(month: YearMonth?, period: String): ByteArray {
        val all = allWorkdays.value
        val ws = if (month == null) all else {
            val from = month.atDay(1).toEpochDay()
            val to = month.atEndOfMonth().toEpochDay()
            all.filter { it.date in from..to }
        }
        return XlsxExporter.build(persons.value, ws, dayExpenses.value, defaultExpense.value, specials.value, period)
    }

    // ----- Importar una planilla exportada por la app -----
    fun importXlsx(bytes: ByteArray, onDone: (String) -> Unit) = viewModelScope.launch {
        val msg = try { doImport(bytes) } catch (e: Exception) {
            "No pude leer el archivo. ¿Es una planilla Excel exportada por esta app?"
        }
        onDone(msg)
    }

    private suspend fun doImport(bytes: ByteArray): String {
        val wb = XlsxReader.read(bytes)
        val reserved = setOf("Resumen", "Por mes", "Personas", "Especiales")
        val existing = persons.value.toMutableList()
        fun key(s: String) = s.trim().lowercase()
        var newPersons = 0
        var newSpecials = 0

        // Personas y tarifas
        wb["Personas"]?.let { sh ->
            for (r in 2..sh.maxRow) {
                val name = sh[r, 0] ?: continue
                val base = sh.num(r, 1) ?: 0.0
                val wp = (0..6).joinToString(",") { sh.num(r, 2 + it)?.toString() ?: "" }
                val cur = existing.firstOrNull { key(it.name) == key(name) }
                if (cur == null) {
                    val p = Person(name = name.trim(), dailyPay = base, weekdayPays = wp)
                    existing.add(p.copy(id = dao.insertPerson(p)))
                    newPersons++
                } else {
                    val upd = cur.copy(dailyPay = base, weekdayPays = wp)
                    dao.updatePerson(upd)
                    existing[existing.indexOf(cur)] = upd
                }
            }
        }
        // Viático estándar (solo si todavía no lo cargaste)
        wb["Resumen"]?.num(2, 2)?.let { if (it > 0.0 && defaultExpense.value == 0.0) setDefaultExpense(it) }
        // Días especiales
        wb["Especiales"]?.let { sh ->
            val cur = specials.value
            for (r in 2..sh.maxRow) {
                val mo = sh.num(r, 1)?.toInt() ?: continue
                val d = sh.num(r, 2)?.toInt() ?: continue
                if (mo !in 1..12 || d !in 1..31) continue
                val y = sh.num(r, 3)?.toInt() ?: 0
                val m = sh.num(r, 4)?.takeIf { it > 0.0 } ?: 2.0
                val label = sh[r, 0] ?: "Día especial"
                val same = cur.firstOrNull { it.month == mo && it.day == d && it.year == y }
                if (same == null) { dao.insertSpecial(SpecialDay(label = label, month = mo, day = d, year = y, multiplier = m)); newSpecials++ }
                else dao.updateSpecial(same.copy(label = label, multiplier = m))
            }
        }
        // Días de cada persona
        val work = ArrayList<Workday>()
        val dayExp = HashMap<Long, Double>()
        for ((name, sh) in wb) {
            if (name in reserved || sh[1, 0] != "Fecha") continue
            val found = existing.firstOrNull { XlsxExporter.sanitizeName(it.name).equals(name, ignoreCase = true) }
                ?: existing.firstOrNull { key(it.name) == key(name) }
            val person = found ?: run {
                val p = Person(name = name.trim())
                val np = p.copy(id = dao.insertPerson(p))
                existing.add(np); newPersons++
                np
            }
            for (r in 2..sh.maxRow) {
                val serial = sh.num(r, 0) ?: break      // fila TOTAL o vacía: termina
                val date = serial.toLong() - 25569
                work.add(Workday(personId = person.id, date = date, pay = sh.num(r, 3) ?: 0.0, expenses = 0.0, note = sh[r, 2] ?: ""))
                sh.num(r, 4)?.let { dayExp[date] = it }
            }
        }
        if (work.isEmpty() && newPersons == 0 && newSpecials == 0) return "No encontré días en el archivo."
        if (work.isNotEmpty()) dao.upsertWorkdays(work)
        val def = defaultExpense.value
        for ((date, v) in dayExp) {
            if (kotlin.math.abs(v - def) > 0.005) dao.setDayExpense(DayExpense(date, v)) else dao.clearDayExpense(date)
        }
        return "Listo: ${work.size} días cargados" +
            (if (newPersons > 0) ", $newPersons personas nuevas" else "") +
            (if (newSpecials > 0) ", $newSpecials días especiales" else "") + "."
    }

    // ----- Selección de varios días -----
    val selecting = MutableStateFlow(false)
    val selected = MutableStateFlow<Set<Long>>(emptySet())      // días (epochDay) elegidos
    val assignees = MutableStateFlow<Set<Long>>(emptySet())     // personas elegidas para asignar

    fun startSelecting(first: Long?) { selecting.value = true; selected.value = if (first == null) emptySet() else setOf(first) }
    fun toggleSelected(epoch: Long) { selected.value = if (epoch in selected.value) selected.value - epoch else selected.value + epoch }
    fun setSelected(days: Set<Long>) { selected.value = days }
    fun toggleAssignee(id: Long) { assignees.value = if (id in assignees.value) assignees.value - id else assignees.value + id }
    fun exitSelecting() { selecting.value = false; selected.value = emptySet(); assignees.value = emptySet() }

    /** Marca a las personas elegidas en los días elegidos. Si viene pay lo fija (si no, usa la tarifa o deja lo que había). Si viene dayExp fija el viático total de esos días. */
    fun assign(pay: Double?, dayExp: Double?) = viewModelScope.launch {
        val days = selected.value.toList()
        val people = persons.value.filter { it.id in assignees.value }
        if (days.isEmpty() || people.isEmpty()) return@launch
        val existing = dao.workdaysOn(days)
        val out = ArrayList<Workday>()
        for (d in days) for (p in people) {
            val cur = existing.firstOrNull { it.date == d && it.personId == p.id }
            out += if (cur == null) Workday(personId = p.id, date = d, pay = pay ?: payOf(p, LocalDate.ofEpochDay(d)), expenses = 0.0)
            else cur.copy(pay = pay ?: cur.pay)
        }
        dao.upsertWorkdays(out)
        if (dayExp != null) dao.setDayExpenses(days.map { DayExpense(it, dayExp) })
    }

    /** Cambia solo el pago de las personas elegidas en los días elegidos donde ya van. */
    fun setPayOnExisting(pay: Double) = viewModelScope.launch {
        val ids = assignees.value
        val list = dao.workdaysOn(selected.value.toList()).filter { it.personId in ids }.map { it.copy(pay = pay) }
        if (list.isNotEmpty()) dao.upsertWorkdays(list)
    }

    fun unassign() = viewModelScope.launch {
        val people = persons.value.filter { it.id in assignees.value }
        for (d in selected.value) for (p in people) dao.deleteWorkday(p.id, d)
    }

    // ----- Hoy -----
    val today = MutableStateFlow(LocalDate.now())
    fun refreshToday() { today.value = LocalDate.now() }
    val todayWorks = today.flatMapLatest { d -> dao.workdays(d.toEpochDay(), d.toEpochDay()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init { viewModelScope.launch { seedOnce() } }

    /** Primera vez que se abre la app: carga los días del Excel (Perez2023.xlsx). No cambia el mes que se muestra. */
    private suspend fun seedOnce() {
        val prefs = getApplication<Application>().getSharedPreferences("app", Context.MODE_PRIVATE)
        if (prefs.getBoolean("seeded", false)) return
        if (dao.personCount() == 0) {
            val text = getApplication<Application>().assets.open("seed.json").bufferedReader().readText()
            val json = JSONObject(text)
            for (name in json.keys()) {
                val pid = dao.insertPerson(Person(name = name))
                val arr = json.getJSONArray(name)
                dao.insertWorkdays((0 until arr.length()).map {
                    Workday(personId = pid, date = LocalDate.parse(arr.getString(it)).toEpochDay(), pay = 0.0, expenses = 0.0)
                })
            }
        }
        prefs.edit().putBoolean("seeded", true).apply()
    }

    fun toggle(p: Person, date: LocalDate, on: Boolean) = viewModelScope.launch {
        if (on) dao.upsertWorkday(Workday(personId = p.id, date = date.toEpochDay(), pay = payOf(p, date), expenses = 0.0))
        else dao.deleteWorkday(p.id, date.toEpochDay())
    }

    fun saveWorkday(w: Workday) = viewModelScope.launch { dao.upsertWorkday(w) }

    fun savePerson(p: Person, applyToExisting: Boolean) = viewModelScope.launch {
        val id = if (p.id == 0L) dao.insertPerson(p) else { dao.updatePerson(p); p.id }
        if (applyToExisting) {
            val person = p.copy(id = id)
            dao.upsertWorkdays(dao.workdaysOfPerson(id).map { it.copy(pay = payOf(person, LocalDate.ofEpochDay(it.date))) })
        }
    }

    fun deletePerson(p: Person) = viewModelScope.launch { dao.deletePerson(p) }
}
