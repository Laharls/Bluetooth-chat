package com.example.bluetoothchat.bluetooth

import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter

class BluetoothChatSession(
    private val socket: BluetoothSocket,
    private val onMessageReceived: (String) -> Unit
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val input = BufferedReader(InputStreamReader(socket.inputStream))
    private val output = PrintWriter(OutputStreamWriter(socket.outputStream), true)

    init {
        // Dès qu'on crée une session, on écoute les messages entrants
        scope.launch {
            try {
                while (true) {
                    val message = input.readLine() ?: break
                    onMessageReceived(message)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun sendMessage(message: String) {
        scope.launch {
            try {
                output.println(message)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun close() {
        socket.close()
    }
}
