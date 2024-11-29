package com.example.kwork_timer_application

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.ImageView
import android.widget.NumberPicker
import android.widget.ProgressBar
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
import android.Manifest
import android.app.Dialog
import android.graphics.Color
import android.view.View
import android.widget.Button
import android.widget.LinearLayout

import yuku.ambilwarna.AmbilWarnaDialog

class MainActivity : AppCompatActivity() {

    private lateinit var tv: TextView
    private lateinit var bt: ImageView
    private lateinit var timerAdapter: TimerAdapter
    private val timerList = mutableListOf<TimerItemEntity>()
    private lateinit var changeColorButton:ImageView
    private var selectedColor:Int = Color.GREEN
    private lateinit var database: TimerDatabase
    private lateinit var LinearL:LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tv = findViewById(R.id.textView2)
        bt = findViewById(R.id.imageView)
        changeColorButton = findViewById(R.id.imageView2)

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerview)

        selectedColor = loadColor()

        LinearL = findViewById(R.id.linearLayoutHeader)

        applyColor(selectedColor)



        // Инициализация базы данных
        database = Room.databaseBuilder(
            applicationContext,
            TimerDatabase::class.java, "timer_database"
        ).build()
        checkNotificationPermission()
        // Загрузка таймеров из базы данных
        lifecycleScope.launch {
            val timersFromDb = database.timerDao().getAllTimers()
            timerList.addAll(timersFromDb)
            timerAdapter.notifyDataSetChanged()
        }

        // Инициализация адаптера
        timerAdapter = TimerAdapter(timerList, this,selectedColor) { timer ->
            lifecycleScope.launch {
                // Удаление таймера из базы данных
                database.timerDao().delete(timer) // Убедитесь, что у вас есть доступ к базе данных


            }
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = timerAdapter
        changeColorButton.setOnClickListener{
            openColorPickerDialog()
        }
        // Кнопка для добавления нового таймера
        bt.setOnClickListener {
            showTimePickerDialog { name, days, hours, minutes, seconds ->
                val totalMillis = (days * 86400000L) + (hours * 3600000L) + (minutes * 60000L) + (seconds * 1000L)
                val newTimer = TimerItemEntity(name = name, initilTime = totalMillis, remainingTimeMillis = totalMillis)
                timerList.add(newTimer)
                timerAdapter.notifyItemInserted(timerList.size - 1)

                // Сохранение таймера в базе данных
                lifecycleScope.launch {
                    database.timerDao().insert(newTimer)
                }

                // Запуск сервиса таймера
                startTimerService(name, totalMillis)
                // Пример вызова

            }
        }


    }
    private fun startTimerService(name: String, remainingTimeMillis: Long) {
        val intent = Intent(this, TimerService::class.java)
        intent.putExtra("remainingTimeMillis", remainingTimeMillis)
        intent.putExtra("timerName", name)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent) // Для Android 8.0 и выше (API 26+)
        } else {
            startService(intent) // Для Android 7.1 и ниже
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
        daysPicker.maxValue = 999
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
    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_CODE)
            } else {
                // Разрешение уже предоставлено, можете отправлять уведомления

            }
        } else {
            // Для версий ниже Android 13 разрешение не требуется

        }
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == NOTIFICATION_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Разрешение предоставлено, можете отправлять уведомления

            } else {
                // Разрешение не предоставлено, покажите сообщение или логику
                Log.e("Permission", "Notification permission denied")
            }
        }
    }
    private fun openColorPickerDialog() {
        val initialColor = Color.RED // Например, начальный цвет (красный)

        val colorPickerDialog = AmbilWarnaDialog(this, initialColor.toInt(),
            object : AmbilWarnaDialog.OnAmbilWarnaListener {
                override fun onOk(dialog: AmbilWarnaDialog?, color: Int) {
                    // Здесь вы получаете выбранный цвет (в формате Int)
                    // Можно использовать его, например, для установки цвета фона
                    applyColor(color)
                    saveColor(color)
                    timerAdapter.updateColor(color)
                }

                override fun onCancel(dialog: AmbilWarnaDialog?) {
                    // Обработка отмены выбора
                }
            })
        colorPickerDialog.show()
    }

    private fun applyColor(color: Int) {
        selectedColor = color
        Log.d("ColorCheck", "Applying Color: $color") // Логируем применяемый цвет
        LinearL.setBackgroundColor(color)
    }

    private fun saveColor(color: Int) {
        val sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putInt("selected_color_alpha", Color.alpha(color))
        editor.putInt("selected_color_red", Color.red(color))
        editor.putInt("selected_color_green", Color.green(color))
        editor.putInt("selected_color_blue", Color.blue(color))
        editor.apply()
        Log.d("ColorCheck", "Saved Color: $color")
    }


    private fun loadColor(): Int {
        val sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE)
        val alpha = sharedPreferences.getInt("selected_color_alpha", 255) // Значение по умолчанию
        val red = sharedPreferences.getInt("selected_color_red", 255) // Значение по умолчанию
        val green = sharedPreferences.getInt("selected_color_green", 255) // Значение по умолчанию
        val blue = sharedPreferences.getInt("selected_color_blue", 255) // Значение по умолчанию
        return Color.argb(alpha, red, green, blue)
    }


    companion object {
        private const val NOTIFICATION_PERMISSION_CODE = 1
    }
}

// Модель данных для таймера с аннотацией @Entity для базы данных
@Entity(tableName = "timer_table")
data class TimerItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    var name: String,
    var initilTime:Long,
    var remainingTimeMillis: Long,


)
