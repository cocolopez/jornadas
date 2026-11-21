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
    val dailyExpenses: Double = 0.0   // viáticos por día
)

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

@Database(entities = [Person::class, Workday::class, DayExpense::class], version = 2, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): AppDao
    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(c: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(c.applicationContext, AppDb::class.java, "jornadas.db")
                .addMigrations(MIGRATION_1_2)
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
            out += if (cur == null) Workday(personId = p.id, date = d, pay = pay ?: p.dailyPay, expenses = 0.0)
            else cur.copy(pay = pay ?: cur.pay)
        }
        dao.upsertWorkdays(out)
        if (dayExp != null) dao.setDayExpenses(days.map { DayExpense(it, dayExp) })
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
        if (on) dao.upsertWorkday(Workday(personId = p.id, date = date.toEpochDay(), pay = p.dailyPay, expenses = 0.0))
        else dao.deleteWorkday(p.id, date.toEpochDay())
    }

    fun saveWorkday(w: Workday) = viewModelScope.launch { dao.upsertWorkday(w) }

    fun savePerson(p: Person, applyToExisting: Boolean) = viewModelScope.launch {
        val id = if (p.id == 0L) dao.insertPerson(p) else { dao.updatePerson(p); p.id }
        if (applyToExisting) dao.applyRates(id, p.dailyPay, p.dailyExpenses)
    }

    fun deletePerson(p: Person) = viewModelScope.launch { dao.deletePerson(p) }
}
