package com.example.damselv5.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.content.Context
import android.util.Log
import java.util.*

/**
 * Manages BLE operations: Scanning, Connecting, and Receiving Data.
 */
@SuppressLint("MissingPermission")
class BleManager(private val context: Context) {

    private val bluetoothManager: BluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private var bluetoothGatt: BluetoothGatt? = null

    // Common BLE Service/Characteristic UUIDs
    private val HM10_SERVICE = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
    private val HM10_CHAR = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")
    
    // Nordic UART Service (Common for ESP32)
    private val NUS_SERVICE = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
    private val NUS_TX_CHAR = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")

    private val CLIENT_CHARACTERISTIC_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    var onConnectionStateChanged: ((Int) -> Unit)? = null
    var onDataReceived: ((String) -> Unit)? = null

    fun startScan(callback: ScanCallback) {
        bluetoothAdapter?.bluetoothLeScanner?.startScan(callback)
    }

    fun stopScan(callback: ScanCallback) {
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(callback)
    }

    fun connect(deviceAddress: String) {
        try {
            val device = bluetoothAdapter?.getRemoteDevice(deviceAddress)
            bluetoothGatt = device?.connectGatt(context, false, gattCallback)
            Log.d("BleManager", "Connecting to $deviceAddress...")
        } catch (e: Exception) {
            Log.e("BleManager", "Connection error: ${e.message}")
        }
    }

    fun disconnect() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            Log.d("BleManager", "Connection State: $newState")
            onConnectionStateChanged?.invoke(newState)
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                gatt?.discoverServices()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("BleManager", "Services discovered. Searching for characteristic...")
                
                var found = false

                // Try HM-10
                val hmService = gatt?.getService(HM10_SERVICE)
                hmService?.getCharacteristic(HM10_CHAR)?.let {
                    enableNotification(gatt, it)
                    found = true
                    Log.d("BleManager", "Subscribed to HM-10 characteristic")
                }

                // Try NUS if not found
                if (!found) {
                    val nusService = gatt?.getService(NUS_SERVICE)
                    nusService?.getCharacteristic(NUS_TX_CHAR)?.let {
                        enableNotification(gatt, it)
                        found = true
                        Log.d("BleManager", "Subscribed to NUS TX characteristic")
                    }
                }

                // Fallback: Search for ANY notifying characteristic if specific ones fail
                if (!found) {
                    gatt?.services?.forEach { service ->
                        service.characteristics.forEach { characteristic ->
                            if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                                enableNotification(gatt, characteristic)
                                found = true
                                Log.d("BleManager", "Fallback: Subscribed to ${characteristic.uuid}")
                                return@forEach
                            }
                        }
                        if (found) return@forEach
                    }
                }
                
                if (!found) {
                    Log.e("BleManager", "No notifying characteristic found on device!")
                }
            }
        }

        private fun enableNotification(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            gatt.setCharacteristicNotification(characteristic, true)
            characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG)?.let { descriptor ->
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(descriptor)
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
            characteristic?.let {
                val data = String(it.value).trim()
                Log.d("BleManager", "Data received (Legacy): $data")
                onDataReceived?.invoke(data)
            }
        }
        
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            val data = String(value).trim()
            Log.d("BleManager", "Data received: $data")
            onDataReceived?.invoke(data)
        }
    }
}