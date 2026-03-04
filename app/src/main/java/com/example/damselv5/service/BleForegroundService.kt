package com.example.damselv5.service

import android.annotation.SuppressLint
import android.app.*
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.*
import android.telecom.TelecomManager
import android.telephony.PhoneNumberUtils
import android.util.Log
import android.widget.Toast
import androidx.core.content.edit
import com.example.damselv5.MainActivity
import com.example.damselv5.R
import com.example.damselv5.ble.BleManager
import com.example.damselv5.ble.BleStatus
import com.example.damselv5.data.AppDatabase
import com.example.damselv5.ui.EmergencyCallActivity
import com.example.damselv5.util.LocationHelper
import com.example.damselv5.util.PanicManager
import com.example.damselv5.util.SmsHelper
import kotlinx.coroutines.*

class BleForegroundService : Service() {

    private lateinit var bleManager: BleManager
    private lateinit var panicManager: PanicManager
    private lateinit var smsHelper: SmsHelper
    private lateinit var locationHelper: LocationHelper
    private lateinit var audioManager: AudioManager
    private var mediaPlayer: MediaPlayer? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private var wakeLock: PowerManager.WakeLock? = null
    private val originalVolumes = mutableMapOf<Int, Int>()
    private val streamsToManage = intArrayOf(
        AudioManager.STREAM_RING,
        AudioManager.STREAM_NOTIFICATION,
        AudioManager.STREAM_MUSIC,
        AudioManager.STREAM_SYSTEM,
        AudioManager.STREAM_ALARM
    )

    private var connectedDeviceName: String = "None"
    private var connectedDeviceAddress: String? = null
    private var connectionStatus: String = "Disconnected"
    private var currentCountdown: Int = -1
    
    private var isStoppingIntentionally = false
    private var connectionLostJob: Job? = null
    private var hasStableConnectionEstablished = false

    private val handler = Handler(Looper.getMainLooper())
    private val reconnectRunnable = object : Runnable {
        override fun run() {
            if (connectionStatus != "Connected" && connectedDeviceAddress != null) {
                Log.d("BleService", "Auto-reconnecting...")
                updateStatus("Reconnecting...")
                bleManager.connect(connectedDeviceAddress!!)
                handler.postDelayed(this, 5000)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        bleManager = BleManager(this)
        smsHelper = SmsHelper(this)
        locationHelper = LocationHelper(this)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        panicManager = PanicManager(this, 
            onCountdownUpdate = { seconds ->
                currentCountdown = seconds
                if (seconds > 0) {
                    muteDevice()
                } else if (seconds == -1) {
                    restoreVolume()
                }
                // Update global state for UI sync
                BleStatus.updateCountdown(seconds)
                updateStatus(connectionStatus)
            },
            onTriggerEmergency = {
                triggerEmergencyAction()
            }
        )

        bleManager.onConnectionStateChanged = { status, newState ->
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        val wasDisconnected = connectionStatus != "Connected"
                        connectionStatus = "Connected"
                        if (wasDisconnected) {
                            stopSiren()
                            connectionLostJob?.cancel() // Prevents "Lost" SMS if recovered quickly
                            if (hasStableConnectionEstablished) {
                                sendReconnectedSms()
                            }
                            hasStableConnectionEstablished = true
                            Log.d("BleService", "Device Connected successfully. Stable connection marked.")
                        }
                        handler.removeCallbacks(reconnectRunnable)
                        updateStatus("Connected")
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    if (connectionStatus == "Connected" && !isStoppingIntentionally) {
                        if (hasStableConnectionEstablished) {
                            showDisconnectAlert()
                            startSiren()
                            connectionLostJob = sendConnectionLostSms()
                        }
                    }
                    connectionStatus = "Disconnected"
                    updateStatus("Reconnecting...")
                    handler.removeCallbacks(reconnectRunnable)
                    handler.postDelayed(reconnectRunnable, 5000)
                }
            }
        }

        bleManager.onDataReceived = { data ->
            if (data.contains("#")) {
                acquireWakeLock() 
                panicManager.handlePanicSignal()
            }
        }
    }

    private fun startSiren() {
        try {
            maximizeAllVolumes()

            if (mediaPlayer == null) {
                mediaPlayer = MediaPlayer.create(this, R.raw.siren)?.apply {
                    isLooping = true
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                }
            }
            
            mediaPlayer?.let { mp ->
                if (!mp.isPlaying) {
                    mp.start()
                    Log.d("BleService", "Siren started.")
                }
            } ?: Log.e("BleService", "Failed to create MediaPlayer for siren.")
            
        } catch (e: Exception) {
            Log.e("BleService", "Error starting siren: ${e.message}")
        }
    }

    private fun stopSiren() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
                Log.d("BleService", "Siren stopped.")
            }
            restoreVolume()
        } catch (e: Exception) {
            Log.e("BleService", "Error stopping siren: ${e.message}")
        } finally {
            mediaPlayer = null
        }
    }

    private fun maximizeAllVolumes() {
        if (originalVolumes.isEmpty()) {
            for (stream in streamsToManage) {
                try {
                    originalVolumes[stream] = audioManager.getStreamVolume(stream)
                    val maxVol = audioManager.getStreamMaxVolume(stream)
                    audioManager.setStreamVolume(stream, maxVol, 0)
                } catch (e: Exception) {
                    Log.e("BleService", "Could not maximize stream $stream: ${e.message}")
                }
            }
            Log.d("BleService", "All volumes maximized for siren.")
        }
    }

    private fun muteDevice() {
        if (originalVolumes.isEmpty()) {
            val streamsToMute = intArrayOf(
                AudioManager.STREAM_RING,
                AudioManager.STREAM_NOTIFICATION,
                AudioManager.STREAM_MUSIC,
                AudioManager.STREAM_SYSTEM
            )
            for (stream in streamsToMute) {
                try {
                    originalVolumes[stream] = audioManager.getStreamVolume(stream)
                    audioManager.setStreamVolume(stream, 0, 0)
                } catch (e: Exception) {
                    Log.e("BleService", "Could not mute stream $stream: ${e.message}")
                }
            }
            Log.d("BleService", "Device muted for emergency countdown.")
        }
    }

    private fun restoreVolume() {
        if (originalVolumes.isNotEmpty()) {
            for ((stream, volume) in originalVolumes) {
                try {
                    audioManager.setStreamVolume(stream, volume, 0)
                } catch (e: Exception) {
                    Log.e("BleService", "Could not restore stream $stream: ${e.message}")
                }
            }
            originalVolumes.clear()
            Log.d("BleService", "Volumes restored.")
        }
    }

    private fun sendConnectionLostSms(): Job {
        return serviceScope.launch {
            val locationUrl = locationHelper.getLastLocation()
            val message = "CRITICAL ALERT: Panic Button Connection LOST! Last known location:\n$locationUrl"
            sendSmsToAllContacts(message)
        }
    }

    private fun sendReconnectedSms() {
        serviceScope.launch {
            val message = "DamselV5: Device reconnected. Protection is active."
            sendSmsToAllContacts(message)
        }
    }

    private suspend fun sendSmsToAllContacts(message: String) {
        withContext(Dispatchers.IO) {
            try {
                val database = AppDatabase.getDatabase(applicationContext)
                val contacts = database.contactDao().getAllContactsSync()
                val prefs = getSharedPreferences("emergency_prefs", MODE_PRIVATE)
                val primaryNumber = prefs.getString("primary_number", "")
                
                val recipients = mutableSetOf<String>()
                contacts.forEach { recipients.add(it.phoneNumber) }
                
                if (!primaryNumber.isNullOrBlank()) {
                    val alreadyInList = contacts.any { 
                        PhoneNumberUtils.compare(it.phoneNumber, primaryNumber)
                    }
                    if (!alreadyInList) {
                        recipients.add(primaryNumber)
                    }
                }

                recipients.forEach { number ->
                    smsHelper.sendSms(number, message)
                }
                Log.d("BleService", "SMS notification sent to all recipients.")
            } catch (e: Exception) {
                Log.e("BleService", "Failed to send SMS notification: ${e.message}")
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val address = intent?.getStringExtra("DEVICE_ADDRESS")
        val name = intent?.getStringExtra("DEVICE_NAME")

        when (intent?.action) {
            ACTION_STOP_SERVICE -> {
                Log.d("BleService", "ACTION_STOP_SERVICE received. Status: $connectionStatus")
                isStoppingIntentionally = true
                
                // Use a separate background thread for SMS to avoid blocking/crashing during stopSelf
                Thread {
                    if (connectionStatus != "Connected" && hasStableConnectionEstablished) {
                        Log.d("BleService", "Sending intentional disconnect SMS...")
                        runBlocking {
                            connectionLostJob?.join()
                            sendSmsToAllContacts("Device was disconnected by the user while the app was trying to restore connection to BLE device.")
                        }
                    }
                    
                    // Final cleanup from background thread
                    handler.post {
                        handler.removeCallbacks(reconnectRunnable)
                        stopSiren()
                        restoreVolume()
                        releaseWakeLock()
                        
                        getSharedPreferences("ble_prefs", MODE_PRIVATE).edit { clear() }
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }.start()
                
                return START_NOT_STICKY
            }
            ACTION_SIMULATE_PANIC -> {
                Log.d("BleService", "Simulated panic signal received.")
                acquireWakeLock()
                panicManager.handlePanicSignal()
                return START_STICKY
            }
        }

        if (address != null) {
            isStoppingIntentionally = false
            hasStableConnectionEstablished = false // Reset for new device connection
            getSharedPreferences("ble_prefs", MODE_PRIVATE).edit {
                putString("last_address", address)
                putString("last_name", name)
            }
            connectedDeviceAddress = address
            connectedDeviceName = name ?: "Unknown"
            connectionStatus = "Connecting..."
            updateStatus("Connecting...")
            bleManager.connect(address)
        } else {
            val prefs = getSharedPreferences("ble_prefs", MODE_PRIVATE)
            val savedAddress = prefs.getString("last_address", null)
            if (savedAddress != null) {
                isStoppingIntentionally = false
                connectedDeviceAddress = savedAddress
                connectedDeviceName = prefs.getString("last_name", "Unknown") ?: "Unknown"
                if (connectionStatus != "Connected") {
                    connectionStatus = "Disconnected"
                    updateStatus("Reconnecting...")
                    bleManager.connect(savedAddress)
                }
            }
        }

        return START_STICKY
    }

    private fun updateStatus(status: String) {
        BleStatus.updateState(status)
        BleStatus.updateDeviceName(connectedDeviceName)

        val notification = createLiveNotification(status)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun showDisconnectAlert() {
        handler.post {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(1000)
            }

            try {
                val notificationUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val r = RingtoneManager.getRingtone(applicationContext, notificationUri)
                r.play()
            } catch (e: Exception) {
                Log.e("BleService", "Sound alert failed: ${e.message}")
            }

            Toast.makeText(this, "CRITICAL: Panic Button Disconnected!", Toast.LENGTH_LONG).show()
        }
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DamselV5::PanicWakeLock")
        }
        if (wakeLock?.isHeld == false) {
            wakeLock?.acquire(30000) 
            Log.d("BleService", "WakeLock acquired.")
        }
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
            Log.d("BleService", "WakeLock released.")
        }
    }

    @SuppressLint("MissingPermission")
    private fun triggerEmergencyAction() {
        initiateEmergencyCall()

        serviceScope.launch {
            val locationUrl = locationHelper.getLastLocation()
            val prefs = getSharedPreferences("emergency_prefs", MODE_PRIVATE)
            val primaryNumber = prefs.getString("primary_number", "")
            
            try {
                withContext(Dispatchers.IO) {
                    val database = AppDatabase.getDatabase(applicationContext)
                    val contacts = database.contactDao().getAllContactsSync()
                    val message = "EMERGENCY! I may be in danger. Please help immediately.\n$locationUrl"
                    
                    val recipients = mutableSetOf<String>()
                    contacts.forEach { recipients.add(it.phoneNumber) }
                    
                    if (!primaryNumber.isNullOrBlank()) {
                        val alreadyInList = contacts.any { 
                            PhoneNumberUtils.compare(it.phoneNumber, primaryNumber)
                        }
                        if (!alreadyInList) {
                            recipients.add(primaryNumber)
                        }
                    }
                    
                    recipients.forEach { number ->
                        smsHelper.sendSms(number, message)
                    }
                }
            } catch (e: Exception) {
                Log.e("BleService", "Emergency SMS failed: ${e.message}")
            } finally {
                currentCountdown = -1
                // Update global state for UI sync
                BleStatus.updateCountdown(-1)
                updateStatus(connectionStatus)
                restoreVolume()
                handler.postDelayed({ releaseWakeLock() }, 5000)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun initiateEmergencyCall() {
        val prefs = getSharedPreferences("emergency_prefs", MODE_PRIVATE)
        val primaryNumber = prefs.getString("primary_number", "")
        
        if (!primaryNumber.isNullOrBlank()) {
            Log.d("BleService", "Triggering Emergency Call Activity for $primaryNumber")
            val callIntent = Intent(this, EmergencyCallActivity::class.java).apply {
                putExtra("PHONE_NUMBER", primaryNumber)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION)
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }
            
            try {
                startActivity(callIntent)
            } catch (e: Exception) {
                Log.e("BleService", "Direct Activity start failed, relying on FullScreenIntent: ${e.message}")
            }
        }
    }

    private fun createLiveNotification(status: String): Notification {
        val channelId = "damsel_live_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(channelId) == null) {
                val channel = NotificationChannel(channelId, "DamselV5 Protection", NotificationManager.IMPORTANCE_HIGH).apply {
                    setSound(null, null)
                    enableVibration(false)
                }
                manager.createNotificationChannel(channel)
            }
        }

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val title = if (currentCountdown > 0) "EMERGENCY IN $currentCountdown SECONDS!" 
                    else if (currentCountdown == 0) "CALLING PRIMARY CONTACT..."
                    else "DamselV5 Protection: $status"

        val builder = Notification.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(if (currentCountdown >= 0) "Press button again to CANCEL" else "Connected to: $connectedDeviceName")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setCategory(if (currentCountdown >= 0) Notification.CATEGORY_CALL else Notification.CATEGORY_SERVICE)
            .setPriority(if (currentCountdown >= 0) Notification.PRIORITY_MAX else Notification.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)

        if (currentCountdown >= 0) {
            val prefs = getSharedPreferences("emergency_prefs", MODE_PRIVATE)
            val callIntent = Intent(this, EmergencyCallActivity::class.java).apply {
                putExtra("PHONE_NUMBER", prefs.getString("primary_number", ""))
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val fullScreenPendingIntent = PendingIntent.getActivity(this, 1, callIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.setFullScreenIntent(fullScreenPendingIntent, true)
        }

        if (Build.VERSION.SDK_INT >= 36) {
            val shortText = if (currentCountdown >= 0) "$currentCountdown" else status.take(4)
            try {
                val ongoingContentClass = Class.forName("android.app.Notification\$OngoingUpdateStyle\$OngoingContent")
                val textContentClass = Class.forName("android.app.Notification\$OngoingUpdateStyle\$OngoingContent\$Text")
                val textContent = textContentClass.getConstructor(CharSequence::class.java).newInstance(title)
                val styleBuilderClass = Class.forName("android.app.Notification\$OngoingUpdateStyle\$Builder")
                val styleBuilder = styleBuilderClass.getConstructor(ongoingContentClass).newInstance(textContent)
                styleBuilderClass.getMethod("setShortCriticalText", CharSequence::class.java).invoke(styleBuilder, shortText)
                val style = styleBuilderClass.getMethod("build").invoke(styleBuilder)
                Notification.Builder::class.java.getMethod("setStyle", Notification.Style::class.java).invoke(builder, style)
                Notification.Builder::class.java.getMethod("setRequestPromotedOngoing", Boolean::class.javaPrimitiveType).invoke(builder, true)
            } catch (e: Exception) {
                builder.extras.putBoolean("android.requestPromotedOngoing", true)
            }
        } else {
            builder.extras.putBoolean("android.requestPromotedOngoing", true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                builder.setSubText(if (currentCountdown >= 0) "EMERGENCY" else status)
            }
        }

        return builder.build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        bleManager.disconnect()
        handler.removeCallbacks(reconnectRunnable)
        releaseWakeLock()
        restoreVolume()
        stopSiren()
        BleStatus.updateState("Disconnected")
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
        const val ACTION_STOP_SERVICE = "STOP_BLE_SERVICE"
        const val ACTION_SIMULATE_PANIC = "SIMULATE_PANIC_SIGNAL"
    }
}