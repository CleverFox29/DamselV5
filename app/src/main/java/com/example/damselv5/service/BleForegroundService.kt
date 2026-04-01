package com.example.damselv5.service

import android.annotation.SuppressLint
import android.app.*
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.*
import android.telephony.PhoneNumberUtils
import android.util.Log
import android.widget.Toast
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

    private lateinit var bM: BleManager
    private lateinit var pM: PanicManager
    private lateinit var sH: SmsHelper
    private lateinit var lH: LocationHelper
    private lateinit var aM: AudioManager
    private var mP: MediaPlayer? = null
    private val sS = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private var wL: PowerManager.WakeLock? = null
    private val oV = mutableMapOf<Int, Int>()
    private val sTM = intArrayOf(
        AudioManager.STREAM_RING,
        AudioManager.STREAM_NOTIFICATION,
        AudioManager.STREAM_MUSIC,
        AudioManager.STREAM_SYSTEM,
        AudioManager.STREAM_ALARM
    )

    private var cDN: String = "None"
    private var cDA: String? = null
    private var cS: String = "Disconnected"
    private var cC: Int = -1
    
    private var iSI = false
    private var cLJ: Job? = null
    private var hSCE = false


    private val h = Handler(Looper.getMainLooper())
    private val rR = object : Runnable {

        override fun run() {

            if (cS.equals("Connected") == false && cS.equals("Bluetooth Off") == false && cDA != null) {
                uS("Reconnecting...")

                if (cDA != null) {
                    bM.connect(cDA!!)

                }
                h.postDelayed(this, 5000)

            }

        }

    }


    private val bSR = object : BroadcastReceiver() {

        override fun onReceive(c: Context, i: Intent) {
            val action = i.action

            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                val s = i.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)

                if (s == BluetoothAdapter.STATE_OFF) {
                    cS = "Bluetooth Off"
                    uS("Bluetooth Off")
                    h.removeCallbacks(rR)

                } else if (s == BluetoothAdapter.STATE_ON) {

                    if (cDA != null) {
                        cS = "Disconnected"
                        uS("Reconnecting...")
                        h.removeCallbacks(rR)
                        h.post(rR)

                    }

                }

            }

        }

    }


    override fun onCreate() {
        super.onCreate()
        bM = BleManager(this)
        sH = SmsHelper(this)
        lH = LocationHelper(this)
        aM = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        pM = PanicManager(this, 
            oCU = { s: Int ->
                cC = s

                if (s > 0) {
                    mD()

                } else if (s == -1) {
                    rV()

                }
                BleStatus.updateCountdown(s)
                uS(cS)

            },
            oTE = {
                tEA()

            }
        )

        bM.oCSC = { st, nS ->

            if (nS == BluetoothProfile.STATE_CONNECTED) {

                if (st == BluetoothGatt.GATT_SUCCESS) {
                    val wD = cS.equals("Connected") == false
                    cS = "Connected"

                    if (wD == true) {
                        sSiren()

                        if (cLJ != null) {
                            cLJ!!.cancel()

                        }

                        if (hSCE == true) {
                            sRS()

                        }
                        hSCE = true

                    }
                    h.removeCallbacks(rR)
                    uS("Connected")

                }

            } else if (nS == BluetoothProfile.STATE_DISCONNECTED) {

                if (cS.equals("Connected") == true && iSI == false) {

                    if (hSCE == true) {
                        sDA()
                        stSiren()
                        cLJ = sCLS()

                    }

                }
                
                val bm = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                val ba = bm.adapter
                
                if (ba != null && ba.isEnabled == false) {
                    cS = "Bluetooth Off"
                    uS("Bluetooth Off")
                    h.removeCallbacks(rR)
                } else {
                    cS = "Disconnected"
                    uS("Reconnecting...")
                    h.removeCallbacks(rR)
                    h.postDelayed(rR, 5000)
                }

            }

        }

        bM.oDR = { d ->

            if (d.contains("#") == true) {
                aWL() 
                pM.handlePanicSignal()

            }

        }

        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        registerReceiver(bSR, filter)

    }


    private fun stSiren() {

        try {
            mAV()

            if (mP == null) {
                mP = MediaPlayer.create(this, R.raw.siren)

                if (mP != null) {
                    mP!!.isLooping = true
                    val attributes = AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    mP!!.setAudioAttributes(attributes)

                }

            }
            
            if (mP != null) {

                if (mP!!.isPlaying == false) {
                    mP!!.start()

                }

            } 
            
        } catch (e: Exception) {
            // Error handling
        }

    }


    private fun sSiren() {

        try {

            if (mP != null) {

                if (mP!!.isPlaying == true) {
                    mP!!.stop()

                }
                mP!!.release()
                mP = null

            }
            rV()

        } catch (e: Exception) {
            // Error handling
        }

    }


    private fun mAV() {

        if (oV.isEmpty() == true) {

            for (s in sTM) {

                try {
                    oV.put(s, aM.getStreamVolume(s))
                    val mV = aM.getStreamMaxVolume(s)
                    aM.setStreamVolume(s, mV, 0)

                } catch (e: Exception) {
                    // Error
                }

            }

        }

    }


    private fun mD() {

        if (oV.isEmpty() == true) {
            val sTMu = intArrayOf(
                AudioManager.STREAM_RING,
                AudioManager.STREAM_NOTIFICATION,
                AudioManager.STREAM_MUSIC,
                AudioManager.STREAM_SYSTEM
            )

            for (s in sTMu) {

                try {
                    oV.put(s, aM.getStreamVolume(s))
                    aM.setStreamVolume(s, 0, 0)

                } catch (e: Exception) {
                    // Error
                }

            }

        }

    }


    private fun rV() {

        if (oV.isEmpty() == false) {

            for (entry in oV.entries) {

                try {
                    val s = entry.key
                    val v = entry.value
                    aM.setStreamVolume(s, v, 0)

                } catch (e: Exception) {
                    // Error
                }

            }
            oV.clear()

        }

    }


    private fun sCLS(): Job {

        return sS.launch {
            val lU = lH.getLastLocation()
            val m = "CRITICAL ALERT: Panic Button Connection LOST! Last known location:\n" + lU
            sSTAC(m)

        }

    }


    private fun sRS() {

        sS.launch {
            val m = "DamselV5: Device reconnected. Protection is active."
            sSTAC(m)

        }

    }


    private suspend fun sSTAC(m: String) {

        withContext(Dispatchers.IO) {

            try {
                val db = AppDatabase.getDatabase(applicationContext)
                val cs = db.contactDao().gACS()
                val pr = getSharedPreferences("emergency_prefs", MODE_PRIVATE)
                val pN = pr.getString("primary_number", "")
                
                val rs = mutableSetOf<String>()

                for (contact in cs) {
                    rs.add(contact.phoneNumber)

                }
                
                if (pN != null && pN.equals("") == false) {
                    var aIL = false

                    for (contact in cs) {

                        if (PhoneNumberUtils.compare(contact.phoneNumber, pN) == true) {
                            aIL = true
                            break

                        }

                    }

                    if (aIL == false) {
                        rs.add(pN)

                    }

                }

                for (n in rs) {
                    sH.sendSms(n, m)

                }

            } catch (e: Exception) {
                // Error
            }

        }

    }


    override fun onStartCommand(i: Intent?, f: Int, sId: Int): Int {

        if (i != null) {
            val a = i.getStringExtra("DEVICE_ADDRESS")
            val n = i.getStringExtra("DEVICE_NAME")
            val action = i.action

            if (ACTION_STOP_SERVICE.equals(action)) {
                iSI = true
                val thread = Thread(Runnable {

                    if (cS.equals("Connected") == false && hSCE == true) {

                        runBlocking {

                            if (cLJ != null) {
                                cLJ!!.join()

                            }
                            sSTAC("Device was disconnected by the user while the app was trying to restore connection to BLE device.")

                        }

                    }
                    
                    h.post(Runnable {
                        h.removeCallbacks(rR)
                        sSiren()
                        rV()
                        rWL()
                        
                        val pr = getSharedPreferences("ble_prefs", MODE_PRIVATE)
                        val ed = pr.edit()
                        ed.clear()
                        ed.commit()
                        
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()

                    })

                })
                thread.start()
                return START_NOT_STICKY

            } else if (ACTION_SIMULATE_PANIC.equals(action)) {
                aWL()
                pM.handlePanicSignal()
                return START_STICKY

            }

            if (a != null) {
                iSI = false
                hSCE = false 
                val pr = getSharedPreferences("ble_prefs", MODE_PRIVATE)
                val ed = pr.edit()
                ed.putString("last_address", a)
                ed.putString("last_name", n)
                ed.commit()
                
                cDA = a

                if (n != null) {
                    cDN = n

                } else {
                    cDN = "Unknown"

                }
                cS = "Connecting..."
                uS("Connecting...")
                bM.connect(a)

            } else {
                val pr = getSharedPreferences("ble_prefs", MODE_PRIVATE)
                val sA = pr.getString("last_address", null)

                if (sA != null) {
                    iSI = false
                    cDA = sA
                    val savedName = pr.getString("last_name", "Unknown")

                    if (savedName != null) {
                        cDN = savedName

                    } else {
                        cDN = "Unknown"

                    }

                    if (cS.equals("Connected") == false) {
                        val bm = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                        val ba = bm.adapter
                        
                        if (ba != null && ba.isEnabled == false) {
                            cS = "Bluetooth Off"
                            uS("Bluetooth Off")
                        } else {
                            cS = "Disconnected"
                            uS("Reconnecting...")
                            bM.connect(sA)
                        }

                    }

                }

            }

        }

        return START_STICKY

    }


    private fun uS(st: String) {
        BleStatus.updateState(st)
        BleStatus.updateDeviceName(cDN)

        val notification = cLN(st)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(N_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)

        } else {
            startForeground(N_ID, notification)

        }

    }


    private fun sDA() {

        h.post(Runnable {
            val vibrator: Vibrator

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vM = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibrator = vM.defaultVibrator

            } else {
                @Suppress("DEPRECATION")
                vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE))

            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(1000)

            }

            try {
                val nU = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val r = RingtoneManager.getRingtone(applicationContext, nU)

                if (r != null) {
                    r.play()

                }

            } catch (e: Exception) {
                // Sound failed
            }

            Toast.makeText(this@BleForegroundService, "CRITICAL: Panic Button Disconnected!", Toast.LENGTH_LONG).show()

        })

    }


    @SuppressLint("WakelockTimeout")
    private fun aWL() {

        if (wL == null) {
            val pM = getSystemService(Context.POWER_SERVICE) as PowerManager
            wL = pM.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DamselV5::PanicWakeLock")

        }

        if (wL != null) {

            if (wL!!.isHeld == false) {
                wL!!.acquire(30000) 

            }

        }

    }


    private fun rWL() {

        if (wL != null) {

            if (wL!!.isHeld == true) {
                wL!!.release()

            }

        }

    }


    @SuppressLint("MissingPermission")
    private fun tEA() {
        iEC()

        sS.launch {
            val lU = lH.getLastLocation()
            val pr = getSharedPreferences("emergency_prefs", MODE_PRIVATE)
            val pN = pr.getString("primary_number", "")
            
            try {

                withContext(Dispatchers.IO) {
                    val db = AppDatabase.getDatabase(applicationContext)
                    val cs = db.contactDao().gACS()
                    val m = "EMERGENCY! I may be in danger. Please help immediately.\n" + lU
                    
                    val rs = mutableSetOf<String>()

                    for (contact in cs) {
                        rs.add(contact.phoneNumber)

                    }
                    
                    if (pN != null && pN.equals("") == false) {
                        var aIL = false

                        for (contact in cs) {

                            if (PhoneNumberUtils.compare(contact.phoneNumber, pN) == true) {
                                aIL = true
                                break

                            }

                        }

                        if (aIL == false) {
                            rs.add(pN)

                        }

                    }
                    
                    for (n in rs) {
                        sH.sendSms(n, m)

                    }

                }

            } catch (e: Exception) {
                // SMS failed
            } finally {
                cC = -1
                BleStatus.updateCountdown(-1)
                uS(cS)
                rV()
                h.postDelayed(Runnable { rWL() }, 5000)

            }

        }

    }


    @SuppressLint("MissingPermission")
    private fun iEC() {
        val pr = getSharedPreferences("emergency_prefs", MODE_PRIVATE)
        val pN = pr.getString("primary_number", "")
        
        if (pN != null && pN.equals("") == false) {
            val cI = Intent(this, EmergencyCallActivity::class.java)
            cI.putExtra("PHONE_NUMBER", pN)
            cI.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            cI.addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION)
            cI.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            
            try {
                startActivity(cI)

            } catch (e: Exception) {
                // Call failed
            }

        }

    }


    private fun cLN(st: String): Notification {
        val cI = "damsel_live_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val m = getSystemService(NotificationManager::class.java)

            if (m.getNotificationChannel(cI) == null) {
                val c = NotificationChannel(cI, "DamselV5 Protection", NotificationManager.IMPORTANCE_HIGH)
                c.setSound(null, null)
                c.enableVibration(false)
                m.createNotificationChannel(c)

            }

        }

        val i = Intent(this, MainActivity::class.java)
        val pI = PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE)

        var t = "DamselV5 Protection: " + st

        if (cC > 0) {
            t = "EMERGENCY IN " + cC + " SECONDS!"

        } else if (cC == 0) {
            t = "CALLING PRIMARY CONTACT..."

        }

        val builder = Notification.Builder(this, cI)
        builder.setContentTitle(t)

        if (cC >= 0) {
            builder.setContentText("Press button again to CANCEL")

        } else {
            builder.setContentText("Connected to: " + cDN)

        }
        builder.setSmallIcon(android.R.drawable.ic_dialog_info)
        builder.setOngoing(true)

        if (cC >= 0) {
            builder.setCategory(Notification.CATEGORY_CALL)
            builder.setPriority(Notification.PRIORITY_MAX)

        } else {
            builder.setCategory(Notification.CATEGORY_SERVICE)
            builder.setPriority(Notification.PRIORITY_DEFAULT)

        }
        builder.setContentIntent(pI)

        if (cC >= 0) {
            val pr = getSharedPreferences("emergency_prefs", MODE_PRIVATE)
            val callI = Intent(this, EmergencyCallActivity::class.java)
            callI.putExtra("PHONE_NUMBER", pr.getString("primary_number", ""))
            callI.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val fSPI = PendingIntent.getActivity(this, 1, callI, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.setFullScreenIntent(fSPI, true)

        }

        builder.extras.putBoolean("android.requestPromotedOngoing", true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {

            if (cC >= 0) {
                builder.setSubText("EMERGENCY")

            } else {
                builder.setSubText(st)

            }

        }

        return builder.build()

    }


    override fun onBind(i: Intent?): IBinder? {
        return null

    }


    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(bSR)
        sS.cancel()
        bM.disconnect()
        h.removeCallbacks(rR)
        rWL()
        rV()
        sSiren()
        BleStatus.updateState("Disconnected")

    }


    companion object {
        private const val N_ID = 1001
        const val ACTION_STOP_SERVICE = "STOP_BLE_SERVICE"
        const val ACTION_SIMULATE_PANIC = "SIMULATE_PANIC_SIGNAL"
    }
}