package com.example.damselv5.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.content.Context
import android.util.Log
import java.util.*


@SuppressLint("MissingPermission")
class BleManager(private val c: Context) {

    private val bm: BluetoothManager = c.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val ba: BluetoothAdapter? = bm.adapter
    private var g: BluetoothGatt? = null


    private val H_S = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
    private val H_C = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")
    
    private val N_S = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
    private val N_T = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")


    private val C_C_C = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")


    var oCSC: ((Int, Int) -> Unit)? = null
    var oDR: ((String) -> Unit)? = null


    fun startScan(cb: ScanCallback) {

        if (ba != null) {
            val scanner: BluetoothLeScanner? = ba.bluetoothLeScanner

            if (scanner != null) {
                scanner.startScan(cb)

            }

        }

    }


    fun stopScan(cb: ScanCallback) {

        if (ba != null) {
            val scanner: BluetoothLeScanner? = ba.bluetoothLeScanner

            if (scanner != null) {
                scanner.stopScan(cb)

            }

        }

    }


    fun connect(a: String) {

        try {
            // Close existing connection before trying a new one
            if (g != null) {
                g!!.close()
                g = null
            }

            if (ba != null) {
                val d = ba.getRemoteDevice(a)

                if (d != null) {
                    g = d.connectGatt(c, false, gCb)

                }

            }

        } catch (e: Exception) {
            // Error
        }

    }


    fun disconnect() {

        if (g != null) {
            g!!.disconnect()
            g!!.close()
            g = null

        }

    }


    private val gCb = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gt: BluetoothGatt?, s: Int, nS: Int) {

            if (oCSC != null) {
                oCSC!!.invoke(s, nS)

            }

            if (nS == BluetoothProfile.STATE_CONNECTED) {

                if (s == BluetoothGatt.GATT_SUCCESS) {

                    if (gt != null) {
                        gt.discoverServices()

                    }

                }

            }

        }


        override fun onServicesDiscovered(gt: BluetoothGatt?, s: Int) {

            if (s == BluetoothGatt.GATT_SUCCESS) {

                if (gt != null) {
                    var f = false

                    val hS = gt.getService(H_S)

                    if (hS != null) {
                        val hC = hS.getCharacteristic(H_C)

                        if (hC != null) {
                            eN(gt, hC)
                            f = true

                        }

                    }


                    if (f == false) {
                        val nS = gt.getService(N_S)

                        if (nS != null) {
                            val nT = nS.getCharacteristic(N_T)

                            if (nT != null) {
                                eN(gt, nT)
                                f = true

                            }

                        }

                    }


                    if (f == false) {
                        val services = gt.services

                        for (srv in services) {
                            val characteristics = srv.characteristics

                            for (chr in characteristics) {
                                val props = chr.properties

                                if ((props and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                                    eN(gt, chr)
                                    f = true
                                    break

                                }

                            }

                            if (f == true) {
                                break

                            }

                        }

                    }

                }

            }

        }


        private fun eN(gt: BluetoothGatt, chr: BluetoothGattCharacteristic) {
            gt.setCharacteristicNotification(chr, true)
            val d = chr.getDescriptor(C_C_C)

            if (d != null) {
                d.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gt.writeDescriptor(d)

            }

        }


        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gt: BluetoothGatt?, chr: BluetoothGattCharacteristic?) {

            if (chr != null) {
                val bytes = chr.value

                if (bytes != null) {
                    val d = String(bytes).trim()

                    if (oDR != null) {
                        oDR!!.invoke(d)

                    }

                }

            }

        }
        
        override fun onCharacteristicChanged(gt: BluetoothGatt, chr: BluetoothGattCharacteristic, v: ByteArray) {
            val d = String(v).trim()

            if (oDR != null) {
                oDR!!.invoke(d)

            }

        }

    }

}