package com.example.kwork_timer_application

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.CountDownTimer
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat

class TimerService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var remainingTimeMillis: Long = 0L
    private var timerName: String = "Таймер"
    private lateinit var notificationManager: NotificationManager
    private var timerId: Int = 0

    // Используем MutableMap для хранения активных таймеров
    private val activeTimers = mutableMapOf<Int, Long>()
    private var countdownTimer: CountDownTimer? = null



    // Остальные ваши переменные...

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()


    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "STOP_TIMER" -> {
                stopTimer()
                return START_NOT_STICKY
            }"PAUSE_TIMER" -> {
            pauseTimer()
            return START_NOT_STICKY
        }
            else -> {
                remainingTimeMillis = intent?.getLongExtra("remainingTimeMillis", 0L) ?: 0L
                timerName = intent?.getStringExtra("timerName") ?: "Таймер"
                timerId = intent?.getIntExtra("timerId", 0) ?: 0
                Log.d("TimerService", "Service started with remainingTimeMillis: $remainingTimeMillis, timerName: $timerName, timerId: $timerId")

                activeTimers[timerId] = remainingTimeMillis  // Добавляем таймер в список активных

                // Останавливаем предыдущий таймер уведомления, если он был
                countdownTimer?.cancel()

                // Создаем новый CountdownTimer для обновления уведомления только последнего таймера
                countdownTimer = object : CountDownTimer(remainingTimeMillis, 1000) {
                    override fun onTick(millisUntilFinished: Long) {
                        updateNotification(millisUntilFinished, timerId)
                    }

                    override fun onFinish() {
                        notifyTimerFinished(timerId)         // Показываем уведомление о завершении таймера
                        activeTimers.remove(timerId)          // Удаляем таймер из списка активных
                        notificationManager.cancel(timerId)   // Удаляем уведомление после завершения
                        stopSelfIfNoActiveTimers()            // Останавливаем сервис, если больше нет активных таймеров
                    }
                }.start()
            }
        }
        return START_NOT_STICKY
    }
    private fun pauseTimer() {
        countdownTimer?.cancel()
        updatePausedNotification()
        Log.d("TimerService", "Timer paused for ID: $timerId")
    }
    private fun updatePausedNotification() {
        val notificationLayout = RemoteViews(packageName, R.layout.notification_timer).apply {
            setTextViewText(R.id.notification_title, "$timerName (Пауза)")
            setTextViewText(R.id.notification_time, "На паузе")
        }

        val notification = NotificationCompat.Builder(this, "TIMER_CHANNEL")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContent(notificationLayout)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(timerId, notification)
    }

    private fun stopTimer() {
        Log.d("TimerService", "Stopping timer with ID: $timerId")

        countdownTimer?.cancel()            // Останавливаем счетчик уведомления
        activeTimers.remove(timerId)         // Удаляем таймер из списка активных
        notificationManager.cancel(timerId)  // Удаляем уведомление
        stopSelfIfNoActiveTimers()           // Проверяем, нужно ли остановить сервис
    }

    private fun stopSelfIfNoActiveTimers() {
        if (activeTimers.isEmpty()) {
            countdownTimer?.cancel()            // Отменяем таймер уведомления
            stopSelf()                           // Останавливаем сервис
        }
    }



    private val updateTimerTask = object : Runnable {
        override fun run() {
            // Итерация по всем активным таймерам
            for (id in activeTimers.keys) {
                val remainingTime = activeTimers[id] ?: continue
                if (remainingTime > 0) {
                    activeTimers[id] = remainingTime - 1000 // Обновление оставшегося времени
                    updateNotification(remainingTime - 1000, id)
                } else {
                    notifyTimerFinished(id) // Уведомление о завершении таймера
                    activeTimers.remove(id) // Удаляем таймер из активных
                }
            }
            // Запланируйте следующий запуск через 1 секунду
            handler.postDelayed(this, 1000)
            if (activeTimers.isEmpty()) stopSelf() // Остановка сервиса, если больше нет активных таймеров
        }
    }
    private fun updateNotification(timeMillis: Long, timerId: Int) {
        val minutes = (timeMillis / 1000) / 60
        val seconds = (timeMillis / 1000) % 60
        val formattedTime = String.format("%02d:%02d", minutes, seconds)

        Log.d("TimerService", "Updating notification: $formattedTime")

        val notificationLayout = RemoteViews(packageName, R.layout.notification_timer).apply {
            setTextViewText(R.id.notification_title, timerName)
            setTextViewText(R.id.notification_time, formattedTime)

        }

        val notification = NotificationCompat.Builder(this, "TIMER_CHANNEL")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContent(notificationLayout)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(createStopPendingIntent(timerId)) // Убедитесь, что это также корректно
            .build()

        startForeground(timerId, notification) // Используем timerId для идентификации уведомления
    }

    private fun notifyTimerFinished(timerId: Int) {
        // Отправка уведомления о завершении таймера
        val finishedNotification = NotificationCompat.Builder(this, "TIMER_CHANNEL")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Таймер завершен")
            .setContentText("Таймер '$timerName' завершен.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(timerId, finishedNotification) // Использование timerId
    }
    private fun createPausePendingIntent(timerId: Int): PendingIntent {
        val pauseIntent = Intent(this, TimerService::class.java).apply {
            action = "PAUSE_TIMER"
            putExtra("timerId", timerId)
        }
        return PendingIntent.getService(this, timerId, pauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun createStopPendingIntent(timerId: Int): PendingIntent {
        val stopIntent = Intent(this, TimerService::class.java).apply {
            action = "STOP_TIMER" // Определяем действие
            putExtra("timerId", timerId) // Передаем timerId
        }
        // Добавляем FLAG_IMMUTABLE
        return PendingIntent.getService(this, timerId, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }


    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "TIMER_CHANNEL",
                "Таймер",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateTimerTask)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
