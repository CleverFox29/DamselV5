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
import androidx.compose.foundation.shape.CircleShape
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
            @SuppressLint("MissingPermission")
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                // Filter out devices with no name or blank names
                val name = result.device.name
                if (!name.isNullOrBlank()) {
                    if (devices.none { it.device.address == result.device.address }) {
                        devices.add(result)
                    }
                }
            }
        }
    }

    // Automatically start scanning when the screen opens
    LaunchedEffect(Unit) {
        if (!isScanning) {
            onStartScan(callback)
            isScanning = true
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Scan BLE Devices") }) },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 3.dp
            ) {
                Column(
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 16.dp, vertical = 24.dp)
                ) {
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
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = CircleShape,
                        colors = if (isScanning) {
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        } else {
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    ) {
                        Text(if (isScanning) "Stop Scan" else "Scan for nearby devices")
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            items(devices) { result ->
                @SuppressLint("MissingPermission")
                val name = result.device.name ?: ""
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