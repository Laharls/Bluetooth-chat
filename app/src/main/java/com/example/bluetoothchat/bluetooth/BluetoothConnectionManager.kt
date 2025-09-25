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

    // Récupère le contexte à partir de l'activité fournie en paramètre du constructeur.
    // Le contexte est utilisé pour accéder aux services système (comme le Bluetooth).
    private val context: Context = activity
    // Récupère le BluetoothManager, qui est le point d’entrée pour interagir avec le Bluetooth du système.
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    // Récupère l’adaptateur Bluetooth (le matériel Bluetooth du téléphone).
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    // Demande les permissions nécessaire au bon fonctionnement de l'application
    private val permissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        onPermissionResult?.invoke(permissions.all { it.value })
    }

    // Représente une session de communication Bluetooth (ex: pour envoyer/recevoir des messages).
    // Elle sera initialisée après une connexion réussie.
    var chatSession: BluetoothChatSession? = null

    // UUID (identifiant unique) utilisé pour identifier ce service Bluetooth entre appareils.
    // Les deux appareils doivent utiliser le même UUID pour que la connexion fonctionne.
    private val appUuid: UUID = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66")

    // Modifier la signature de startServer pour passer le socket à onConnected
    fun startServer(
        onWaiting: () -> Unit,
        onConnected: () -> Unit,
        onMessageReceived: (String) -> Unit
    ) {
        // Crée un serveur Bluetooth qui écoute les connexions entrantes avec le nom "BluetoothChat"
        // et l'UUID de l'application
        val serverSocket = bluetoothAdapter
            ?.listenUsingRfcommWithServiceRecord("BluetoothChat", appUuid)

        if (serverSocket == null) {
            // Si on n'a pas de Bluetooth ou problème d'initialisation
            // Préviens l'UI (à adapter, peut être via un callback d'erreur)
            return
        }

        // Informe l'interface que le serveur est prêt et à l'écoute d'une connexion
        onWaiting()

        Thread {
            try {
                val socket = serverSocket.accept() // Bloquant, attend une connexion
                socket?.let {
                    serverSocket.close() // Ferme le serveur, on ne veut accepter qu'une seule connexion ici

                    chatSession = BluetoothChatSession(it, onMessageReceived) // Crée une session de chat Bluetooth avec le socket connecté

                    // Appelle le callback dans le thread UI
                    activity.runOnUiThread {
                        onConnected()
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
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
                val socket = device.createRfcommSocketToServiceRecord(appUuid) // Crée un socket Bluetooth RFCOMM vers l'appareil avec l'UUID de l'application
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