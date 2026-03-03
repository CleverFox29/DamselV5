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

    // Common HM-10 / generic serial BLE UUIDs
    private val serviceUuid: UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
    private val charUuid: UUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")

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
            onConnectionStateChanged?.invoke(newState)
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                gatt?.discoverServices()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt?.getService(serviceUuid)
                val characteristic = service?.getCharacteristic(charUuid)
                if (characteristic != null) {
                    gatt.setCharacteristicNotification(characteristic, true)
                    
                    // Enable notifications on the descriptor
                    val descriptor = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                    if (descriptor != null) {
                        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        gatt.writeDescriptor(descriptor)
                    }
                }
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
            characteristic?.let {
                val data = String(it.value).trim()
                onDataReceived?.invoke(data)
            }
        }
        
        // Modern API for Android 13+
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            val data = String(value).trim()
            onDataReceived?.invoke(data)
        }
    }
}