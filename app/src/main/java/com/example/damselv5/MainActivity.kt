package com.example.damselv5

import android.Manifest
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.damselv5.ble.BleStatus
import com.example.damselv5.service.BleForegroundService
import com.example.damselv5.ui.BleScanActivity
import com.example.damselv5.ui.ContactViewModel
import com.example.damselv5.ui.theme.DamselV5Theme
import kotlinx.coroutines.launch

/**
 * Main Activity handling Contact Management, Emergency Number, and Connection Status.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: ContactViewModel by viewModels()

    // Launcher to pick a contact for SMS list
    private val pickContactLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val contactUri: Uri? = result.data?.data
            contactUri?.let { uri ->
                val (name, phone) = getContactDetails(uri)
                if (name != null && phone != null) {
                    lifecycleScope.launch {
                        val rowId = viewModel.insert(name, phone)
                        if (rowId == -1L) {
                            Toast.makeText(this@MainActivity, "Duplicate contact not allowed", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Toast.makeText(this, "Failed to import contact details", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Launcher to pick primary emergency contact for calls
    private val pickPrimaryContactLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val contactUri: Uri? = result.data?.data
            contactUri?.let { uri ->
                val (_, phone) = getContactDetails(uri)
                if (phone != null) {
                    val prefs = getSharedPreferences("emergency_prefs", Context.MODE_PRIVATE)
                    prefs.edit().putString("primary_number", phone).apply()
                    Toast.makeText(this, "Primary Number Updated: $phone", Toast.LENGTH_SHORT).show()
                    recreate() 
                }
            }
        }
    }

    // Launcher for READ_CONTACTS permission for SMS list
    private val requestContactsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            openContactPicker(pickContactLauncher)
        } else {
            Toast.makeText(this, "Contacts permission is required to import", Toast.LENGTH_SHORT).show()
        }
    }

    // Launcher for READ_CONTACTS permission for Primary Number
    private val requestPrimaryContactsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            openContactPicker(pickPrimaryContactLauncher)
        } else {
            Toast.makeText(this, "Contacts permission is required to select primary number", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            DamselV5Theme {
                MainScreen(
                    viewModel = viewModel,
                    onScanClick = {
                        if (hasBlePermissions()) {
                            startActivity(Intent(this, BleScanActivity::class.java))
                        } else {
                            requestInitialPermissions()
                        }
                    },
                    onDisconnectClick = {
                        val intent = Intent(this, BleForegroundService::class.java).apply {
                            action = BleForegroundService.ACTION_STOP_SERVICE
                        }
                        stopService(intent)
                        Toast.makeText(this, "Protection Stopped", Toast.LENGTH_SHORT).show()
                    },
                    onImportClick = {
                        requestContactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                    },
                    onSimulatePanicClick = {
                        if (hasEmergencyPermissions()) {
                            val intent = Intent(this, BleForegroundService::class.java).apply {
                                action = BleForegroundService.ACTION_SIMULATE_PANIC
                            }
                            startService(intent)
                            Toast.makeText(this, "Simulating Panic Signal...", Toast.LENGTH_SHORT).show()
                        } else {
                            requestInitialPermissions()
                        }
                    },
                    onSelectPrimaryClick = {
                        requestPrimaryContactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                    }
                )
            }
        }
        
        requestInitialPermissions()
    }

    private fun hasBlePermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == android.content.pm.PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    private fun hasEmergencyPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED &&
               ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun requestInitialPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        requestPermissionLauncher.launch(permissions.toTypedArray())
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        val hasEmergency = hasEmergencyPermissions()
        val hasBle = hasBlePermissions()
        
        if (!hasEmergency) {
            Toast.makeText(this, "MANDATORY: SMS and Call permissions are required for safety.", Toast.LENGTH_LONG).show()
        } else if (!hasBle) {
            Log.w("Permissions", "Bluetooth/Location permissions not fully granted.")
        }
    }

    private fun openContactPicker(launcher: androidx.activity.result.ActivityResultLauncher<Intent>) {
        val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
        launcher.launch(intent)
    }

    private fun getContactDetails(uri: Uri): Pair<String?, String?> {
        var name: String? = null
        var phone: String? = null
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val cursor: Cursor? = contentResolver.query(uri, projection, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                name = it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME))
                phone = it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))
            }
        }
        return Pair(name, phone)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: ContactViewModel, 
    onScanClick: () -> Unit,
    onDisconnectClick: () -> Unit,
    onImportClick: () -> Unit,
    onSimulatePanicClick: () -> Unit,
    onSelectPrimaryClick: () -> Unit
) {
    val context = LocalContext.current
    val contacts by viewModel.allContacts.observeAsState(emptyList())
    val connectionState by BleStatus.connectionState.collectAsState()
    val deviceName by BleStatus.deviceName.collectAsState()
    val isDark = isSystemInDarkTheme()

    val prefs = context.getSharedPreferences("emergency_prefs", Context.MODE_PRIVATE)
    val emergencyNumber = prefs.getString("primary_number", "Not Set") ?: "Not Set"

    Scaffold(
        topBar = { TopAppBar(title = { Text("DamselV5 Panic App") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            // Status Card
            val statusBgColor = when(connectionState) {
                "Connected" -> if (isDark) Color(0xFF1B5E20) else Color(0xFFE8F5E9)
                "Reconnecting...", "Connecting..." -> if (isDark) Color(0xFFE65100) else Color(0xFFFFF3E0)
                else -> MaterialTheme.colorScheme.secondaryContainer
            }
            val statusTextColor = when(connectionState) {
                "Connected" -> if (isDark) Color(0xFFC8E6C9) else Color(0xFF1B5E20)
                "Reconnecting...", "Connecting..." -> if (isDark) Color(0xFFFFE0B2) else Color(0xFFE65100)
                else -> MaterialTheme.colorScheme.onSecondaryContainer
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = statusBgColor,
                    contentColor = statusTextColor
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Protection Status", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = connectionState,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (connectionState != "Disconnected") {
                        Text("Device: $deviceName", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // BLE Control Buttons
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val isServiceActive = connectionState != "Disconnected"
                
                Button(
                    onClick = onScanClick, 
                    modifier = Modifier.weight(1f),
                    enabled = !isServiceActive,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Text("Scan BLE")
                }
                Button(
                    onClick = onDisconnectClick, 
                    modifier = Modifier.weight(1f),
                    enabled = isServiceActive,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Text("Disconnect")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Simulated Panic Button
            Button(
                onClick = onSimulatePanicClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDark) Color(0xFFD32F2F) else Color(0xFFFF5252),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Warning, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Simulate Panic (#)", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Primary Emergency Number Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Primary Emergency Number (Will be Called)", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Phone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = emergencyNumber,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = onSelectPrimaryClick,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = MaterialTheme.colorScheme.onSecondary
                            )
                        ) {
                            Text("Select")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Integrated Emergency Contacts Box (Header + Button + List)
            Card(
                modifier = Modifier.fillMaxWidth().weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically, 
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "SMS Alert List", 
                            style = MaterialTheme.typography.titleMedium, 
                            modifier = Modifier.weight(1f)
                        )
                        // Person+ Icon Button
                        IconButton(
                            onClick = onImportClick,
                            modifier = Modifier.size(48.dp),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary,
                                contentColor = MaterialTheme.colorScheme.onTertiary
                            )
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Person, 
                                    contentDescription = "Import Contact",
                                    modifier = Modifier.size(24.dp)
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 4.dp, y = (-4).dp)
                                        .size(16.dp)
                                        .background(MaterialTheme.colorScheme.tertiary, CircleShape)
                                        .background(MaterialTheme.colorScheme.onTertiary.copy(alpha = 0.2f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("+", color = MaterialTheme.colorScheme.onTertiary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    if (contacts.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "No contacts added. Tap the icon to import.", 
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(contacts) { contact ->
                                ListItem(
                                    headlineContent = { Text(contact.name, fontWeight = FontWeight.SemiBold) },
                                    supportingContent = { Text(contact.phoneNumber) },
                                    trailingContent = {
                                        IconButton(onClick = { viewModel.delete(contact) }) {
                                            Icon(
                                                Icons.Default.Delete, 
                                                contentDescription = "Delete",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}