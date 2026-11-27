package com.btjoystick.bridge

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Runs a Bluetooth RFCOMM/SPP server on the phone so the companion Windows script
 * (pc_server/pc_xinput_bridge.py) can connect and emulate a native Xbox 360 (XInput) controller.
 */
@SuppressLint("MissingPermission")
class BluetoothSppManager(
    private val bluetoothAdapter: BluetoothAdapter?,
    private val onStatusChanged: (String) -> Unit
) {
    companion object {
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val SERVICE_NAME = "BTJoystickBridgeSPP"
    }

    private val acceptExecutor = Executors.newSingleThreadExecutor()
    private val writeExecutor = Executors.newSingleThreadExecutor()
    private val latestPayload = AtomicReference<ByteArray?>(null)
    private val writeScheduled = AtomicBoolean(false)

    @Volatile
    private var serverSocket: BluetoothServerSocket? = null

    @Volatile
    private var clientSocket: BluetoothSocket? = null

    @Volatile
    private var outputStream: OutputStream? = null

    @Volatile
    private var running = false

    val isConnected: Boolean
        get() = clientSocket?.isConnected == true && outputStream != null

    fun startServer() {
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            onStatusChanged("Please turn on Bluetooth first.")
            return
        }
        stop()
        running = true
        onStatusChanged("SPP Server waiting for PC script (pc_xinput_bridge.py)...")

        acceptExecutor.execute {
            try {
                val srv = adapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, SPP_UUID)
                serverSocket = srv
                while (running) {
                    val sock = srv.accept()
                    if (!running) {
                        sock.close()
                        break
                    }
                    closeClient()
                    clientSocket = sock
                    outputStream = sock.outputStream
                    onStatusChanged("SPP Connected to PC (${sock.remoteDevice?.name ?: "Windows PC"})! XInput ready.")
                }
            } catch (e: Exception) {
                if (running) {
                    onStatusChanged("SPP Server stopped: ${e.message}")
                }
            }
        }
    }

    fun sendState(state: GamepadState) {
        if (outputStream == null) return
        latestPayload.set(state.toSppPacket().toByteArray(Charsets.UTF_8))
        if (writeScheduled.compareAndSet(false, true)) {
            writeExecutor.execute {
                try {
                    while (true) {
                        val payload = latestPayload.getAndSet(null) ?: break
                        val os = outputStream ?: break
                        os.write(payload)
                        os.flush()
                    }
                } catch (e: Exception) {
                    closeClient()
                    if (running) {
                        onStatusChanged("PC disconnected. Waiting for pc_xinput_bridge.py to reconnect...")
                    }
                } finally {
                    writeScheduled.set(false)
                    if (latestPayload.get() != null && outputStream != null && writeScheduled.compareAndSet(false, true)) {
                        writeExecutor.execute {
                            try {
                                val payload = latestPayload.getAndSet(null)
                                val os = outputStream
                                if (payload != null && os != null) {
                                    os.write(payload)
                                    os.flush()
                                }
                            } catch (_: Exception) {
                                closeClient()
                            } finally {
                                writeScheduled.set(false)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun closeClient() {
        latestPayload.set(null)
        try {
            outputStream?.close()
        } catch (_: Exception) {
        }
        try {
            clientSocket?.close()
        } catch (_: Exception) {
        }
        outputStream = null
        clientSocket = null
    }

    fun stop() {
        running = false
        closeClient()
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverSocket = null
        onStatusChanged("SPP Server stopped.")
    }
}
