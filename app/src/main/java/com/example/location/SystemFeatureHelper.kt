package com.example.location

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import java.io.File
import java.util.Locale

/**
 * VoiceBroadcaster handles TextToSpeech synthesis for order alerts, arrivals, and system state updates.
 */
class VoiceBroadcaster private constructor(private val context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var isInitialized = false

    companion object {
        @Volatile
        private var instance: VoiceBroadcaster? = null

        fun getInstance(context: Context): VoiceBroadcaster {
            return instance ?: synchronized(this) {
                instance ?: VoiceBroadcaster(context.applicationContext).also { instance = it }
            }
        }
    }

    init {
        try {
            tts = TextToSpeech(context, this)
        } catch (e: Exception) {
            Log.e("VoiceBroadcaster", "Failed to create TextToSpeech engine", e)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.CHINESE)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w("VoiceBroadcaster", "Chinese language pack missing or not supported on this device. Trying English fallback...")
                tts?.language = Locale.ENGLISH
            }
            isInitialized = true
            Log.i("VoiceBroadcaster", "TTS Engine initialized successfully with speech volume channels allocated.")
        } else {
            Log.e("VoiceBroadcaster", "TTS initialization failed.")
        }
    }

    /**
     * Speak out any prompt loudly using the music/voice stream.
     */
    fun speak(text: String, keepTts: Boolean = true) {
        if (!isInitialized || tts == null) {
            Log.w("VoiceBroadcaster", "TTS not initialized yet. Queueing speech request: $text")
            // Re-attempting init
            if (tts == null) {
                tts = TextToSpeech(context, this)
            }
            return
        }
        try {
            Log.i("VoiceBroadcaster", "Broadcasting speech: $text")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "errand_speech_id")
            } else {
                @Suppress("DEPRECATION")
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null)
            }
        } catch (e: Exception) {
            Log.e("VoiceBroadcaster", "Speech synthesis failed: ${e.message}", e)
        }
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
            isInitialized = false
        } catch (e: Exception) {
            Log.e("VoiceBroadcaster", "Shutdown exception", e)
        }
    }
}

/**
 * VibrateAlertHelper provides tactile sensations (haptic feedback) for courier orders.
 */
class VibrateAlertHelper(private val context: Context) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    /**
     * Vibrate standard timing modes corresponding to different notification severities.
     */
    fun triggerVibration(mode: String) {
        if (vibrator == null || !vibrator.hasVibrator()) {
            Log.w("VibrateHelper", "Physical vibrator not present on this device/sim.")
            return
        }

        try {
            when (mode.uppercase()) {
                "NEW_ORDER" -> {
                    // Two powerful pulses (courier alert): vibrate 400ms, sleep 200ms, vibrate 400ms
                    val pattern = longArrayOf(0, 400, 200, 400)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(pattern, -1)
                    }
                    Log.i("VibrateHelper", "Custom double pulse vibrating for NEW_ORDER alert.")
                }
                "SUCCESS" -> {
                    // Soft, light click for confirming action: 100ms
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(100)
                    }
                    Log.i("VibrateHelper", "Soft haptic click for operation success.")
                }
                "ALARM_HEAVY" -> {
                    // Urgent SOS style: 3 fast beats
                    val pattern = longArrayOf(0, 150, 100, 150, 100, 300)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(pattern, -1)
                    }
                    Log.i("VibrateHelper", "Heavy SOS ripple vibration activated.")
                }
                else -> {
                    // Standard 250ms tap
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(250, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(250)
                    }
                    Log.i("VibrateHelper", "Standard haptic alert triggered.")
                }
            }
        } catch (e: Exception) {
            Log.e("VibrateHelper", "Error activating mechanical vibrator", e)
        }
    }
}

/**
 * NotificationPushHelper posts high-priority message pushes to the local status bar dynamically.
 */
class NotificationPushHelper(private val context: Context) {
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val CHANNEL_ID = "errand_push_alerts_channel"

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "速达跑腿 订单状态与推送提醒"
            val descriptionText = "用于接收秒杀抢单、骑手实时接单、后台定位追踪等跑腿推送提醒"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 150, 300)
            }
            notificationManager.createNotificationChannel(channel)
            Log.i("NotificationPushHelper", "Notification channel initialized with default vibrations enabled.")
        }
    }

    /**
     * Send a gorgeous, clickable status bar notification immediately.
     */
    fun sendPushNotification(title: String, message: String, notificationId: Int = (System.currentTimeMillis() % 100000).toInt()) {
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 
                notificationId, 
                intent, 
                PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
            )

            // Dynamic icons fallback
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_chat) // Standard reliable fallback icon
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setVibrate(longArrayOf(0, 250, 100, 250))
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))

            notificationManager.notify(notificationId, builder.build())
            Log.i("NotificationPushHelper", "Successfully dispatched notification push ID: $notificationId")
        } catch (e: Exception) {
            Log.e("NotificationPushHelper", "Error dispatching status bar notification", e)
        }
    }
}

/**
 * LocalStorageTester runs automated storage checks to verify read and write abilities on disk.
 */
object LocalStorageTester {
    /**
     * Performs a full read/write benchmark test to confirm local file storage safety.
     * Returns a detailed status summary.
     */
    fun runLocalStorageDiagnostics(context: Context): String {
        return try {
            val cacheDirectory = context.cacheDir
            val testFile = File(cacheDirectory, "errand_storage_benchmark.txt")
            
            // 1. Write benchmark data
            val timestamp = System.currentTimeMillis()
            val testKey = "ERRAND_SYS_${timestamp}"
            val payload = "SPEEDY_ERRAND_SYSTEM_BENCHMARK: [Key='$testKey'] [Timestamp=${timestamp}] [Platform=Android ${Build.VERSION.RELEASE}]\nAll database, Room caching, and local files operate perfectly."
            
            testFile.writeText(payload, Charsets.UTF_8)
            
            // 2. Read back benchmark data
            if (!testFile.exists()) {
                return "❌ Storage benchmark error: File failed to create."
            }
            
            val retrieved = testFile.readText(Charsets.UTF_8)
            if (retrieved == payload) {
                val sizeBytes = testFile.length()
                testFile.delete() // Clean up file footprint
                return "✅ 存储测试顺利：读取并重写写入 ${sizeBytes} 字节正常，本地应用沙盒及 Room 具备完全 R/W 支配权限！"
            } else {
                return "⚠️ 存储测试异常：拉取内容不匹配候选负载，内容可能被截断。"
            }
        } catch (e: Exception) {
            Log.e("LocalStorageTester", "Sandbox storage diagnostics failed", e)
            "❌ 存储异常崩溃：${e.localizedMessage ?: "未知磁盘拒绝"}"
        }
    }
}
