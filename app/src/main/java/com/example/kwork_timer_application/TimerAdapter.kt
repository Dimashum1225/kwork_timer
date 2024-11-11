package com.example.kwork_timer_application

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
class TimerAdapter(
    private val timers: MutableList<TimerItemEntity>,
    private val context: Context,
    private val color: Int,
    private val onDeleteTimer: (TimerItemEntity) -> Unit

) : RecyclerView.Adapter<TimerAdapter.TimerViewHolder>() {
    private var itemColor:Int = color
    private val activeTimers = mutableMapOf<Int, CountDownTimer?>()
    fun updateColor(newColor: Int) {
        itemColor = newColor
        notifyDataSetChanged() // Обновляем все элементы
    }
    class TimerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val timerName: TextView = itemView.findViewById(R.id.timer_name)
        val timerTime: TextView = itemView.findViewById(R.id.timer_time)
        val buttonStart: Button = itemView.findViewById(R.id.button_start)
        val buttonPause: Button = itemView.findViewById(R.id.button_pause)
        val buttonStop: Button = itemView.findViewById(R.id.button_stop)
        val progressBar:ProgressBar = itemView.findViewById(R.id.progressBar)
        val line:View = itemView.findViewById(R.id.line)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TimerViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.timer_item, parent, false)
        return TimerViewHolder(view)
    }

    override fun onBindViewHolder(holder: TimerViewHolder, position: Int) {
        val timer = timers[position]
        holder.timerName.text = timer.name
        holder.timerTime.text = formatTime(timer.remainingTimeMillis)

        // Убедитесь, что вы не сбрасываете прогресс при пересоздании элемента
        holder.progressBar.progress = ((timer.initilTime - timer.remainingTimeMillis).toFloat() / timer.initilTime * 100).toInt()
        holder.buttonStart.setBackgroundColor(itemColor)
        holder.buttonPause.setBackgroundColor(itemColor)
        holder.buttonStop.setBackgroundColor(itemColor)
        holder.line.setBackgroundColor(itemColor)
        var isRunning = activeTimers.containsKey(timer.id)

        holder.buttonStart.setOnClickListener {
            if (!isRunning) {
                isRunning = true

                // Установка прогресса в 0% при старте
                holder.progressBar.progress = 0

                val countDownTimer = object : CountDownTimer(timer.remainingTimeMillis, 1000) {
                    override fun onTick(millisUntilFinished: Long) {
                        timer.remainingTimeMillis = millisUntilFinished
                        holder.timerTime.text = formatTime(millisUntilFinished)

                        // Расчет прогресса
                        val progress = ((timer.initilTime - millisUntilFinished).toFloat() / timer.initilTime * 100).toInt()
                        holder.progressBar.progress = progress
                    }

                    override fun onFinish() {
                        holder.progressBar.progress = 100
                        holder.timerTime.text = "00:00:00:00"
                        isRunning = false
                        activeTimers.remove(timer.id)
                    }
                }.start()

                activeTimers[timer.id] = countDownTimer

                // Запускаем сервис с оставшимся временем
                val serviceIntent = Intent(context, TimerService::class.java).apply {
                    putExtra("remainingTimeMillis", timer.remainingTimeMillis)
                    putExtra("timerName", timer.name)
                    putExtra("timerId", timer.id) // Используйте корректный timerId
                }
                context.startService(serviceIntent)
            }
        }

        holder.buttonPause.setOnClickListener {
            activeTimers[timer.id]?.cancel()
            activeTimers.remove(timer.id)
            isRunning = false
            val pauseIntent = Intent(context, TimerService::class.java).apply {
                action = "PAUSE_TIMER"
                putExtra("timerId", timer.id)
            }
            context.startService(pauseIntent)
        }


        holder.buttonStop.setOnClickListener {
            AlertDialog.Builder(context).apply {
                setTitle("Подтверждение удаления")
                setMessage("Вы уверены, что хотите удалить таймер?")
                setPositiveButton("Да") { _, _ ->
                    activeTimers[timer.id]?.cancel()
                    activeTimers.remove(timer.id)

                    val stopIntent = Intent(context, TimerService::class.java).apply {
                        action = "STOP_TIMER"
                        putExtra("timerId", timer.id)
                    }
                    context.startService(stopIntent)

                    onDeleteTimer(timer)
                    timers.removeAt(position)
                    notifyItemRemoved(position)
                    notifyItemRangeChanged(position, timers.size)
                    isRunning = false
                }
                setNegativeButton("Нет", null)
                create()
                show()
            }
        }
    }

    override fun getItemCount() = timers.size
}


fun formatTime(millis: Long): String {
    val seconds = millis / 1000 % 60
    val minutes = millis / 1000 / 60 % 60
    val hours = millis / 1000 / 60 / 60 % 24
    val days = millis / 1000 / 60 / 60 / 24

    return String.format("%02d:%02d:%02d:%02d", days, hours, minutes, seconds)
}
