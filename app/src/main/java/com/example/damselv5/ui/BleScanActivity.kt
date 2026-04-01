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

    private lateinit var bM: BleManager


    override fun onCreate(sI: Bundle?) {
        super.onCreate(sI)
        bM = BleManager(this)

        setContent {
            DamselV5Theme {
                ScanScreen(
                    oDS = { a, n ->
                        val i = Intent(this, BleForegroundService::class.java).apply {
                            putExtra("DEVICE_ADDRESS", a)
                            putExtra("DEVICE_NAME", n)

                        }
                        startForegroundService(i)
                        finish()

                    },
                    oSS = { c -> bM.startScan(c) },
                    oStopS = { c -> bM.stopScan(c) }
                )

            }

        }

    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    oDS: (String, String) -> Unit,
    oSS: (ScanCallback) -> Unit,
    oStopS: (ScanCallback) -> Unit
) {
    val dS = remember { mutableStateListOf<ScanResult>() }
    var iS by remember { mutableStateOf(false) }


    val c = remember {
        object : ScanCallback() {

            @SuppressLint("MissingPermission")
            override fun onScanResult(cT: Int, r: ScanResult) {
                
                val n = r.device.name

                if (!n.isNullOrBlank()) {

                    if (dS.none { it.device.address == r.device.address }) {
                        dS.add(r)

                    }

                }

            }

        }

    }

    
    LaunchedEffect(Unit) {

        if (!iS) {
            oSS(c)
            iS = true

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

                            if (!iS) {
                                dS.clear()
                                oSS(c)
                                iS = true

                            } else {
                                oStopS(c)
                                iS = false

                            }

                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = CircleShape,
                        colors = if (iS) {
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
                        Text(if (iS) "Stop Scan" else "Scan for nearby devices")

                    }

                }

            }

        }
    ) { p ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(p)
        ) {
            items(dS) { r ->
                @SuppressLint("MissingPermission")
                val n = r.device.name ?: ""
                val a = r.device.address
                
                ListItem(
                    headlineContent = { Text(n) },
                    supportingContent = { Text(a) },
                    modifier = Modifier.clickable {
                        oStopS(c)
                        oDS(a, n)

                    }
                )
                HorizontalDivider()

            }

        }

    }
}