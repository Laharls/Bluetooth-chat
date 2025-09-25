package com.example.bluetoothchat

import android.os.Bundle
import android.bluetooth.BluetoothDevice
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bluetoothchat.bluetooth.BluetoothConnectionManager
import com.example.bluetoothchat.ui.theme.BluetoothChatTheme

// 👇 Enum pour gérer l'état de l'UI
enum class ScreenState {
    CONNECTION, CHAT
}

class MainActivity : ComponentActivity() {

    private lateinit var bluetoothManager: BluetoothConnectionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        bluetoothManager = BluetoothConnectionManager(this)

        enableEdgeToEdge()
        setContent {
            BluetoothChatTheme {
                var currentScreen by remember { mutableStateOf(ScreenState.CONNECTION) }
                var messages by remember { mutableStateOf(listOf<String>()) }
                var deviceName by remember { mutableStateOf("") }

                when (currentScreen) {
                    ScreenState.CONNECTION -> {
                        ConnectionScreen(
                            bluetoothManager = bluetoothManager,
                            onConnected = { name ->
                                deviceName = name
                                currentScreen = ScreenState.CHAT
                            },
                            onMessageReceived = { msg ->
                                messages = messages + "🔵 Reçu : $msg"
                            }
                        )
                    }
                    ScreenState.CHAT -> {
                        ChatScreen(
                            bluetoothManager = bluetoothManager,
                            deviceName = deviceName,
                            messages = messages,
                            onMessageSent = { msg ->
                                messages = messages + "🟢 Envoyé : $msg"
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        bluetoothManager.nettoyerReceiver()
    }
}
@Composable
fun ConnectionScreen(
    bluetoothManager: BluetoothConnectionManager,
    onConnected: (String) -> Unit,
    onMessageReceived: (String) -> Unit
) {
    var resultText by remember { mutableStateOf("Appuie sur un bouton pour tester") }
    val devices = remember { mutableStateListOf<BluetoothDevice>() }

    LaunchedEffect(Unit) {
        bluetoothManager.onDeviceFound = { device ->
            if (!devices.contains(device)) devices.add(device)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Connexion Bluetooth", fontSize = 24.sp, modifier = Modifier.padding(bottom = 16.dp))

        Button(onClick = {
            if (bluetoothManager.hasAllPermissions()) {
                devices.clear()
                val (success, msg) = bluetoothManager.startScan()
                resultText = if (success) "✅ $msg" else "❌ $msg"
            } else {
                bluetoothManager.onPermissionResult = { granted ->
                    if (granted) {
                        val (success, msg) = bluetoothManager.startScan()
                        resultText = if (success) "✅ $msg" else "❌ $msg"
                    } else {
                        resultText = "❌ Permissions refusées"
                    }
                }
                bluetoothManager.requestPermissions()
                resultText = "🔄 Demande de permissions..."
            }
        }) {
            Text("Démarrer Scan")
        }

        Spacer(Modifier.height(12.dp))

        Button(onClick = {
            bluetoothManager.startServer(
                onWaiting = {
                    resultText = "🕓 Serveur démarré, en attente de connexion..."
                },
                onConnected = {
                    resultText = "✅ Appareil connecté au serveur !"
                    onConnected("Appareil distant")
                },
                onMessageReceived = onMessageReceived
            )
        }) {
            Text("Démarrer Serveur")
        }

        Text("Appareils détectés :", fontWeight = FontWeight.Bold, modifier = Modifier.padding(8.dp))

        LazyColumn(Modifier.weight(1f)) {
            items(devices) { device ->
                Text(
                    "${device.name ?: "Nom inconnu"} - ${device.address}",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .clickable {
                            bluetoothManager.connectToDevice(
                                device,
                                onConnected = {
                                    onConnected(device.name ?: device.address)
                                },
                                onMessageReceived = onMessageReceived
                            )
                        }
                )
            }
        }

        Text(resultText, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
    }
}
@Composable
fun ChatScreen(
    bluetoothManager: BluetoothConnectionManager,
    deviceName: String,
    messages: List<String>,
    onMessageSent: (String) -> Unit
) {
    var messageText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text("Discussion avec $deviceName", fontSize = 20.sp, modifier = Modifier.padding(bottom = 16.dp))

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            items(messages) { msg ->
                Text(msg, modifier = Modifier.padding(4.dp))
            }
        }

        OutlinedTextField(
            value = messageText,
            onValueChange = { messageText = it },
            label = { Text("Votre message") },
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                if (messageText.isNotBlank()) {
                    bluetoothManager.chatSession?.sendMessage(messageText)
                    onMessageSent(messageText)
                    messageText = ""
                }
            },
            modifier = Modifier.align(Alignment.End).padding(top = 8.dp)
        ) {
            Text("Envoyer")
        }
    }
}
