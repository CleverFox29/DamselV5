package com.example.damselv5

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
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
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.damselv5.ble.BleStatus
import com.example.damselv5.service.BleForegroundService
import com.example.damselv5.ui.BleScanActivity
import com.example.damselv5.ui.ContactViewModel
import com.example.damselv5.ui.theme.DamselV5Theme
import kotlinx.coroutines.launch


class MainActivity : ComponentActivity() {

    private lateinit var vm: ContactViewModel


    private val l1: ActivityResultLauncher<Intent> = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->

        if (r.resultCode == RESULT_OK) {
            val i: Intent? = r.data

            if (i != null) {
                val u: Uri? = i.data

                if (u != null) {
                    val pair: Pair<String?, String?> = gCD(u)
                    val n: String? = pair.first
                    val p: String? = pair.second

                    if (n != null && p != null) {
                        lifecycleScope.launch {
                            val id: Long = vm.insert(n, p)

                            if (id == -1L) {
                                Toast.makeText(this@MainActivity, "Duplicate contact not allowed", Toast.LENGTH_SHORT).show()

                            }

                        }

                    } else {
                        Toast.makeText(this@MainActivity, "Failed to import contact details", Toast.LENGTH_SHORT).show()

                    }

                }

            }

        }

    }


    private val l2: ActivityResultLauncher<Intent> = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->

        if (r.resultCode == RESULT_OK) {
            val i: Intent? = r.data

            if (i != null) {
                val u: Uri? = i.data

                if (u != null) {
                    val pair: Pair<String?, String?> = gCD(u)
                    val n: String? = pair.first
                    val p: String? = pair.second

                    if (p != null) {
                        val pr = getSharedPreferences("emergency_prefs", Context.MODE_PRIVATE)
                        val ed = pr.edit()

                        if (n != null) {
                            ed.putString("primary_name", n)

                        } else {
                            ed.putString("primary_name", "Emergency Contact")

                        }
                        ed.putString("primary_number", p)
                        ed.commit()

                        Toast.makeText(this@MainActivity, "Primary Contact Updated", Toast.LENGTH_SHORT).show()
                        recreate() 

                    }

                }

            }

        }

    }


    private val l3: ActivityResultLauncher<String> = registerForActivityResult(ActivityResultContracts.RequestPermission()) { g ->

        if (g == true) {
            oCP(l1)

        } else {
            Toast.makeText(this@MainActivity, "Contacts permission is required to import", Toast.LENGTH_SHORT).show()

        }

    }


    private val l4: ActivityResultLauncher<String> = registerForActivityResult(ActivityResultContracts.RequestPermission()) { g ->

        if (g == true) {
            oCP(l2)

        } else {
            Toast.makeText(this@MainActivity, "Contacts permission is required to select primary number", Toast.LENGTH_SHORT).show()

        }

    }


    private val l5: ActivityResultLauncher<String> = registerForActivityResult(ActivityResultContracts.RequestPermission()) { g ->

        if (g != true) {
            Toast.makeText(this@MainActivity, "BACKGROUND LOCATION: Please select 'Allow all the time' in settings for safety when screen is off.", Toast.LENGTH_LONG).show()

        } else {
            cAROP()

        }

    }


    private val l6: ActivityResultLauncher<Array<String>> = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { ps ->
        var ag = true

        for (entry in ps.entries) {

            if (entry.value == false) {
                ag = false
                break

            }

        }

        if (ag == true) {
            cARBL()

        } else {
            Toast.makeText(this@MainActivity, "Safety features require SMS, Call, and Location permissions.", Toast.LENGTH_LONG).show()

        }

    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        vm = ViewModelProvider(this).get(ContactViewModel::class.java)

        setContent {
            DamselV5Theme {
                MainScreen(
                    v = vm,
                    s = {

                        if (iBE() == true) {

                            if (hBP() == true) {
                                val i = Intent(this@MainActivity, BleScanActivity::class.java)
                                startActivity(i)

                            } else {
                                rIP()

                            }

                        } else {
                            Toast.makeText(this@MainActivity, "Please turn on Bluetooth to scan for devices.", Toast.LENGTH_SHORT).show()

                        }

                    },
                    d = {
                        val i = Intent(this@MainActivity, BleForegroundService::class.java)
                        i.action = BleForegroundService.ACTION_STOP_SERVICE
                        startService(i)

                        Toast.makeText(this@MainActivity, "Protection Stopped", Toast.LENGTH_SHORT).show()

                    },
                    im = {
                        l3.launch(Manifest.permission.READ_CONTACTS)

                    },
                    pa = {

                        if (hEP() == true) {
                            val i = Intent(this@MainActivity, BleForegroundService::class.java)
                            i.action = BleForegroundService.ACTION_SIMULATE_PANIC
                            startService(i)

                        } else {
                            rIP()

                        }

                    },
                    sp = {
                        l4.launch(Manifest.permission.READ_CONTACTS)

                    },
                    rp = {
                        val pr = getSharedPreferences("emergency_prefs", Context.MODE_PRIVATE)
                        val ed = pr.edit()

                        ed.remove("primary_name")
                        ed.remove("primary_number")
                        ed.commit()

                        Toast.makeText(this@MainActivity, "Primary Contact Removed", Toast.LENGTH_SHORT).show()
                        recreate()

                    }
                )

            }

        }
        
        rIP()

    }


    private fun iBE(): Boolean {
        val bm = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val ba = bm.adapter

        if (ba != null) {
            return ba.isEnabled

        }
        return false

    }


    private fun hBP(): Boolean {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val s1 = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
            val s2 = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)

            return s1 == android.content.pm.PackageManager.PERMISSION_GRANTED && s2 == android.content.pm.PackageManager.PERMISSION_GRANTED

        } else {
            val s = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)

            return s == android.content.pm.PackageManager.PERMISSION_GRANTED

        }

    }


    private fun hEP(): Boolean {
        val hs = ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val hc = ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val hf = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        var hb = true

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            hb = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED

        }
        val ho = Settings.canDrawOverlays(this)
        
        return hs && hc && hf && hb && ho

    }


    private fun rIP() {
        val p = mutableListOf<String>()
        p.add(Manifest.permission.SEND_SMS)
        p.add(Manifest.permission.CALL_PHONE)
        p.add(Manifest.permission.ACCESS_FINE_LOCATION)
        p.add(Manifest.permission.ACCESS_COARSE_LOCATION)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            p.add(Manifest.permission.BLUETOOTH_SCAN)
            p.add(Manifest.permission.BLUETOOTH_CONNECT)

        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            p.add(Manifest.permission.POST_NOTIFICATIONS)

        }

        l6.launch(p.toTypedArray())

    }


    private fun cARBL() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val hb = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED

            if (hb == false) {
                l5.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)

            } else {
                cAROP()

            }

        } else {
            cAROP()

        }

    }


    private fun cAROP() {

        if (Settings.canDrawOverlays(this) == false) {
            Toast.makeText(this, "PLEASE ENABLE: 'Display over other apps' so calls work when screen is off.", Toast.LENGTH_LONG).show()
            val i = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            i.data = Uri.parse("package:$packageName")
            startActivity(i)

        }

    }


    private fun oCP(l: ActivityResultLauncher<Intent>) {
        val i = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
        l.launch(i)

    }


    private fun gCD(uri: Uri): Pair<String?, String?> {
        var n: String? = null
        var p: String? = null
        val c: Cursor? = contentResolver.query(uri, null, null, null, null)

        if (c != null) {

            if (c.moveToFirst() == true) {
                val nI = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val aNI = c.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                val nUI = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                
                if (nI != -1) {
                    n = c.getString(nI)

                }

                if (n == null || n.equals("")) {

                    if (aNI != -1) {
                        n = c.getString(aNI)

                    }

                }

                if (nUI != -1) {
                    p = c.getString(nUI)

                }

            }
            c.close()

        }
        return Pair(n, p)

    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    v: ContactViewModel, 
    s: () -> Unit,
    d: () -> Unit,
    im: () -> Unit,
    pa: () -> Unit,
    sp: () -> Unit,
    rp: () -> Unit
) {
    val ctx = LocalContext.current
    val cs_state = v.all.observeAsState(emptyList())
    val cs = cs_state.value
    
    val st_state = BleStatus.connectionState.collectAsState()
    val st = st_state.value
    
    val dn_state = BleStatus.deviceName.collectAsState()
    val dn = dn_state.value
    
    val cd_state = BleStatus.countdown.collectAsState()
    val cd = cd_state.value
    
    val dk = isSystemInDarkTheme()


    val pr = ctx.getSharedPreferences("emergency_prefs", Context.MODE_PRIVATE)
    val en = pr.getString("primary_name", "Not Set")
    val eno = pr.getString("primary_number", "Not Set")
    val hp = eno != null && eno.equals("Not Set") == false

    val bg = if (dk) Color(0xFF000000) else Color(0xFFFFFFFF)
    val mtc = if (dk) Color.White else Color(0xFF000000)
    val sbg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)


    Surface(color = bg) {
        Scaffold(
            topBar = { 
                TopAppBar(
                    title = { Text("DamselV5", color = mtc, fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = bg)
                ) 
            },
            containerColor = bg
        ) { p1 ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(p1)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                var stbg = MaterialTheme.colorScheme.surfaceVariant
                var sttc = MaterialTheme.colorScheme.onSurfaceVariant
                
                if (st.equals("Connected")) {
                    stbg = if (dk) Color(0xFF0D2D11) else Color(0xFFE8F5E9)
                    sttc = if (dk) Color(0xFF81C784) else Color(0xFF1B5E20)

                } else if (st.equals("Reconnecting...") || st.equals("Connecting...")) {
                    stbg = if (dk) Color(0xFF331A00) else Color(0xFFFFF3E0)
                    sttc = if (dk) Color(0xFFFFB74D) else Color(0xFFE65100)

                } else if (st.equals("Bluetooth Off")) {
                    stbg = if (dk) Color(0xFF330000) else Color(0xFFFFEBEE)
                    sttc = if (dk) Color(0xFFFF5252) else Color(0xFFB71C1C)

                }


                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = stbg, contentColor = sttc)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Connection Status", style = MaterialTheme.typography.labelLarge)
                        Text(text = st, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

                        if (st.equals("Disconnected") == false) {
                            Text("Device: " + dn, style = MaterialTheme.typography.bodyMedium)

                        }

                    }

                }


                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    val isa = st.equals("Disconnected") == false
                    Button(
                        onClick = s, 
                        modifier = Modifier.weight(1f).height(50.dp),
                        enabled = isa == false,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(Icons.Default.Bluetooth, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Connect", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)

                    }
                    Button(
                        onClick = d, 
                        modifier = Modifier.weight(1f).height(50.dp),
                        enabled = isa == true,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Icon(Icons.Default.BluetoothDisabled, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Disconnect", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)

                    }

                }


                Button(
                    onClick = pa,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    var bl = "Panic button"

                    if (cd > 0) {
                        bl = "CANCEL (" + cd + "s)"

                    }
                    Text(bl, fontSize = 16.sp, fontWeight = FontWeight.Bold)

                }


                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = sbg,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Primary Emergency Number", style = MaterialTheme.typography.titleMedium)
                        Text("Will be called and notified via SMS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        
                        var dividerColor = Color.Black.copy(0.05f)

                        if (dk) {
                            dividerColor = Color.White.copy(0.1f)

                        }
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            thickness = 1.dp,
                            color = dividerColor
                        )

                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Phone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {

                                if (en != null) {
                                    Text(
                                        text = en,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        lineHeight = 18.sp
                                    )

                                }

                                if (eno != null) {
                                    Text(
                                        text = eno,
                                        style = MaterialTheme.typography.bodyMedium
                                    )

                                }

                            }
                            
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = sp, 
                                    modifier = Modifier.height(40.dp),
                                    shape = CircleShape,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    ),
                                    contentPadding = PaddingValues(horizontal = 16.dp)
                                ) {

                                    if (hp == true) {
                                        Text("Change")

                                    } else {
                                        Text("Select")

                                    }

                                }

                                if (hp == true) {
                                    FilledTonalIconButton(
                                        onClick = rp,
                                        modifier = Modifier.size(40.dp),
                                        shape = CircleShape,
                                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                                            containerColor = MaterialTheme.colorScheme.errorContainer,
                                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Remove Primary", modifier = Modifier.size(20.dp))

                                    }

                                }

                            }

                        }

                    }

                }


                Card(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = sbg,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("SMS Alert List", style = MaterialTheme.typography.titleMedium)
                                    Text("Will be notified via SMS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                                }
                                FilledTonalIconButton(
                                    onClick = im, 
                                    modifier = Modifier.size(40.dp),
                                    shape = CircleShape
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Add Contact", modifier = Modifier.size(24.dp))

                                }

                            }

                            var dividerColor = Color.Black.copy(0.05f)

                            if (dk) {
                                dividerColor = Color.White.copy(0.1f)

                            }
                            HorizontalDivider(
                                modifier = Modifier.padding(top = 12.dp),
                                thickness = 1.dp,
                                color = dividerColor
                            )

                        }


                        if (cs.isEmpty() == true) {
                            Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                                Text("No contacts added.", style = MaterialTheme.typography.bodyMedium)
                            }

                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 8.dp)
                            ) {
                                items(cs) { c ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Email,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = c.name,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.SemiBold,
                                                lineHeight = 18.sp
                                            )
                                            Text(
                                                text = c.phoneNumber,
                                                style = MaterialTheme.typography.bodyMedium
                                            )

                                        }
                                        FilledTonalIconButton(
                                            onClick = { v.delete(c) },
                                            modifier = Modifier.size(36.dp),
                                            shape = CircleShape,
                                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                        ) {
                                            Icon(
                                                Icons.Default.Delete, 
                                                contentDescription = "Delete", 
                                                modifier = Modifier.size(18.dp)
                                            )

                                        }

                                    }

                                }

                            }

                        }

                    }

                }

            }

        }

    }

}