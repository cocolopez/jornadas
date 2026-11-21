package com.perez.jornadas

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.*
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
    @Query("DELETE FROM workday WHERE personId = :pid AND date = :date")
    suspend fun deleteWorkday(pid: Long, date: Long)
    @Query("UPDATE workday SET pay = :pay, expenses = :exp WHERE personId = :pid")
    suspend fun applyRates(pid: Long, pay: Double, exp: Double)
}

@Database(entities = [Person::class, Workday::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): AppDao
    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(c: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(c.applicationContext, AppDb::class.java, "jornadas.db")
                .build().also { inst = it }
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainVM(app: Application) : AndroidViewModel(app) {
    private val dao = AppDb.get(app).dao()
    val month = MutableStateFlow(YearMonth.now())
    val persons = dao.persons().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val workdays = month.flatMapLatest { m ->
        dao.workdays(m.atDay(1).toEpochDay(), m.atEndOfMonth().toEpochDay())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Marcado rápido: personas seleccionadas para marcar días con un toque
    val quick = MutableStateFlow<Set<Long>>(emptySet())
    fun toggleQuick(id: Long) { quick.value = if (id in quick.value) quick.value - id else quick.value + id }

    // "Hoy"
    val today = MutableStateFlow(LocalDate.now())
    fun refreshToday() { today.value = LocalDate.now() }
    val todayWorks = today.flatMapLatest { d -> dao.workdays(d.toEpochDay(), d.toEpochDay()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun quickMark(date: LocalDate, works: List<Workday>) = viewModelScope.launch {
        val sel = persons.value.filter { it.id in quick.value }
        val allHave = sel.isNotEmpty() && sel.all { p -> works.any { it.personId == p.id } }
        sel.forEach { p ->
            if (allHave) dao.deleteWorkday(p.id, date.toEpochDay())
            else if (works.none { it.personId == p.id })
                dao.upsertWorkday(Workday(personId = p.id, date = date.toEpochDay(), pay = p.dailyPay, expenses = p.dailyExpenses))
        }
    }

    init { viewModelScope.launch { seedOnce() } }

    /** Primera vez que se abre la app: carga los días del Excel (Perez2023.xlsx). */
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
            month.value = YearMonth.of(2022, 12)
        }
        prefs.edit().putBoolean("seeded", true).apply()
    }

    fun toggle(p: Person, date: LocalDate, on: Boolean) = viewModelScope.launch {
        if (on) dao.upsertWorkday(Workday(personId = p.id, date = date.toEpochDay(), pay = p.dailyPay, expenses = p.dailyExpenses))
        else dao.deleteWorkday(p.id, date.toEpochDay())
    }

    fun saveWorkday(w: Workday) = viewModelScope.launch { dao.upsertWorkday(w) }

    fun savePerson(p: Person, applyToExisting: Boolean) = viewModelScope.launch {
        val id = if (p.id == 0L) dao.insertPerson(p) else { dao.updatePerson(p); p.id }
        if (applyToExisting) dao.applyRates(id, p.dailyPay, p.dailyExpenses)
    }

    fun deletePerson(p: Person) = viewModelScope.launch { dao.deletePerson(p) }
}
