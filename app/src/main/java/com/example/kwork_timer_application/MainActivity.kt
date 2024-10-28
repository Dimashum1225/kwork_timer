package com.example.kwork_timer_application

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.ImageView
import android.widget.NumberPicker
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Room
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var tv: TextView
    private lateinit var bt: ImageView
    private lateinit var timerAdapter: TimerAdapter
    private val timerList = mutableListOf<TimerItemEntity>()

    private lateinit var database: TimerDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tv = findViewById(R.id.textView2)
        bt = findViewById(R.id.imageView)
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerview)

        // Инициализация базы данных
        database = Room.databaseBuilder(
            applicationContext,
            TimerDatabase::class.java, "timer_database"
        ).build()

        // Загрузка таймеров из базы данных
        lifecycleScope.launch {
            val timersFromDb = database.timerDao().getAllTimers()
            timerList.addAll(timersFromDb)
            timerAdapter.notifyDataSetChanged()
        }

        // Инициализация адаптера
        timerAdapter = TimerAdapter(timerList, this) { timer ->
            lifecycleScope.launch {
                // Удаление таймера из базы данных
                database.timerDao().delete(timer) // Убедитесь, что у вас есть доступ к базе данных
            }
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = timerAdapter

        // Кнопка для добавления нового таймера
        bt.setOnClickListener {
            showTimePickerDialog { name, days, hours, minutes, seconds ->
                val totalMillis = (days * 86400000L) + (hours * 3600000L) + (minutes * 60000L) + (seconds * 1000L)
                val newTimer = TimerItemEntity(name = name, remainingTimeMillis = totalMillis)
                timerList.add(newTimer)
                timerAdapter.notifyItemInserted(timerList.size - 1)

                // Сохранение таймера в базе данных
                lifecycleScope.launch {
                    database.timerDao().insert(newTimer)
                }
            }
        }
    }

    // Функция для отображения диалога выбора времени и имени таймера
    private fun showTimePickerDialog(onTimeSet: (String, Int, Int, Int, Int) -> Unit) {
        val builder = AlertDialog.Builder(this)
        val inflater = LayoutInflater.from(this)
        val dialogView = inflater.inflate(R.layout.dialog_time_input, null)

        // Инициализация NumberPicker'ов
        val daysPicker = dialogView.findViewById<NumberPicker>(R.id.input_days)
        val hoursPicker = dialogView.findViewById<NumberPicker>(R.id.input_hours)
        val minutesPicker = dialogView.findViewById<NumberPicker>(R.id.input_minutes)
        val secondsPicker = dialogView.findViewById<NumberPicker>(R.id.input_seconds)
        val nameInput = dialogView.findViewById<EditText>(R.id.input_timer_name) // Поле для имени таймера

        // Настройка пределов для NumberPicker'ов
        daysPicker.maxValue = 100
        hoursPicker.maxValue = 23
        minutesPicker.maxValue = 59
        secondsPicker.maxValue = 59

        builder.setView(dialogView)
            .setPositiveButton("OK") { _, _ ->
                val name = nameInput.text.toString().ifBlank { "Без имени" } // Используем введённое имя или "Без имени" по умолчанию
                val days = daysPicker.value
                val hours = hoursPicker.value
                val minutes = minutesPicker.value
                val seconds = secondsPicker.value
                onTimeSet(name, days, hours, minutes, seconds)
            }
            .setNegativeButton("Cancel", null)

        builder.create().show()
    }
}

// Модель данных для таймера с аннотацией @Entity для базы данных
@Entity(tableName = "timer_table")
data class TimerItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    var name: String,
    var remainingTimeMillis: Long
)
