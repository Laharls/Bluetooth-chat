package com.example.bluetoothchat.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import java.io.IOException
import java.util.UUID

class BluetoothConnectionManager(private val activity: ComponentActivity) {

    private val context: Context = activity
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    // Demandeur de permissions (NOUVEAU)
    private val permissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        onPermissionResult?.invoke(permissions.all { it.value })
    }

    // Liste des appareils détectés (simplement leurs noms ou objets)
    var chatSession: BluetoothChatSession? = null

    private val appUuid: UUID = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66")

    // Modifier la signature de startServer pour passer le socket à onConnected
    fun startServer(
        onWaiting: () -> Unit,
        onConnected: () -> Unit,
        onMessageReceived: (String) -> Unit
    ) {
        val serverSocket = bluetoothAdapter
            ?.listenUsingRfcommWithServiceRecord("BluetoothChat", appUuid)

        if (serverSocket == null) {
            // Si on n'a pas de Bluetooth ou problème d'initialisation
            // Préviens l'UI (à adapter, peut être via un callback d'erreur)
            return
        }

        onWaiting()

        Thread {
            try {
                val socket = serverSocket.accept() // Bloquant, attend une connexion
                socket?.let {
                    serverSocket.close()

                    chatSession = BluetoothChatSession(it, onMessageReceived)

                    // Appelle le callback dans le thread UI
                    activity.runOnUiThread {
                        onConnected()
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
                // Ici tu peux aussi appeler un callback d'erreur sur UI si tu veux
            }
        }.start()
    }

    fun connectToDevice(
        device: BluetoothDevice,
        onConnected: () -> Unit,
        onMessageReceived: (String) -> Unit
    ) {
        Thread {
            try {
                val socket = device.createRfcommSocketToServiceRecord(appUuid)
                bluetoothAdapter?.cancelDiscovery() // Toujours annuler le scan avant de se connecter
                socket.connect()

                // Une fois connecté → démarrer la session de chat
                chatSession = BluetoothChatSession(socket, onMessageReceived)

                onConnected()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }.start()
    }




    val devicesFound = mutableListOf<BluetoothDevice>()

    // Callback qui prévient quand on trouve un appareil (à assigner depuis l'UI)
    var onDeviceFound: ((BluetoothDevice) -> Unit)? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == BluetoothDevice.ACTION_FOUND) {
                val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                device?.let {
                    device?.let {
                        if (!devicesFound.contains(it)) {
                            devicesFound.add(it)
                            onDeviceFound?.invoke(it)  // prévient l’UI
                            Log.d("BluetoothScan", "Appareil trouvé : ${it.name} - ${it.address}")
                        }
                    }

                }
            }
        }
    }

    fun enregistrerReceiver() {
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        context.registerReceiver(receiver, filter)
    }

    fun nettoyerReceiver() {
        try {
            context.unregisterReceiver(receiver)
        } catch (e: IllegalArgumentException) {
            Log.w("BluetoothScan", "Receiver déjà désenregistré")
        }
    }
//-------------------------------------------------------------------------------------


    var onPermissionResult: ((Boolean) -> Unit)? = null

    fun isBluetoothSupported(): Boolean {
        return bluetoothAdapter != null
    }

    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled ?: false
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun startScan(): Pair<Boolean, String> {
        if (!isBluetoothEnabled()) {
            return Pair(false, "Bluetooth désactivé")
        }

        if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN)) {
            return Pair(false, "Permission BLUETOOTH_SCAN manquante")
        }

        devicesFound.clear()

        return try {
            enregistrerReceiver()
            val success = bluetoothAdapter?.startDiscovery() ?: false
            if (success) {
                Pair(true, "Scan démarré avec succès")
            } else {
                Pair(false, "Échec du démarrage du scan")
            }
        } catch (e: SecurityException) {
            Pair(false, "Erreur de sécurité: ${e.message}")
        }
    }

    fun requestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        permissionLauncher.launch(permissions)
    }

    fun hasAllPermissions(): Boolean {
        return hasPermission(Manifest.permission.BLUETOOTH_SCAN) &&
                hasPermission(Manifest.permission.BLUETOOTH_CONNECT) &&
                hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    }
}