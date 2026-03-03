package com.example.damselv5.ui

import android.annotation.SuppressLint
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.damselv5.ble.BleManager
import com.example.damselv5.service.BleForegroundService
import com.example.damselv5.ui.theme.DamselV5Theme

@SuppressLint("MissingPermission")
class BleScanActivity : ComponentActivity() {

    private lateinit var bleManager: BleManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bleManager = BleManager(this)

        setContent {
            DamselV5Theme {
                ScanScreen(
                    onDeviceSelected = { deviceAddress, deviceName ->
                        val intent = Intent(this, BleForegroundService::class.java).apply {
                            putExtra("DEVICE_ADDRESS", deviceAddress)
                            putExtra("DEVICE_NAME", deviceName)
                        }
                        startForegroundService(intent)
                        finish()
                    },
                    onStartScan = { callback -> bleManager.startScan(callback) },
                    onStopScan = { callback -> bleManager.stopScan(callback) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    onDeviceSelected: (String, String) -> Unit,
    onStartScan: (ScanCallback) -> Unit,
    onStopScan: (ScanCallback) -> Unit
) {
    val devices = remember { mutableStateListOf<ScanResult>() }
    var isScanning by remember { mutableStateOf(false) }

    val callback = remember {
        object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                if (devices.none { it.device.address == result.device.address }) {
                    devices.add(result)
                }
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Scan BLE Devices") }) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            Button(
                onClick = {
                    if (!isScanning) {
                        devices.clear()
                        onStartScan(callback)
                        isScanning = true
                    } else {
                        onStopScan(callback)
                        isScanning = false
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isScanning) "Stop Scan" else "Start Scan")
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn {
                items(devices) { result ->
                    @SuppressLint("MissingPermission")
                    val name = result.device.name ?: "Unknown"
                    val address = result.device.address
                    
                    ListItem(
                        headlineContent = { Text(name) },
                        supportingContent = { Text(address) },
                        modifier = Modifier.clickable {
                            onStopScan(callback)
                            onDeviceSelected(address, name)
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}