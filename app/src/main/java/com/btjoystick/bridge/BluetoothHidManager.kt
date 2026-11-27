package com.btjoystick.bridge

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppQosSettings
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothProfile
import android.content.Context
import java.util.concurrent.Executors

/**
 * Registers the Android phone as a native Bluetooth HID Gamepad
 * so Windows 10/11 recognizes it directly as a game controller.
 */
@SuppressLint("MissingPermission")
class BluetoothHidManager(
    private val context: Context,
    private val bluetoothAdapter: BluetoothAdapter?,
    private val onStatusChanged: (String) -> Unit
) {
    private val executor = Executors.newSingleThreadExecutor()
    private var hidDevice: BluetoothHidDevice? = null
    private var connectedHost: BluetoothDevice? = null
    private var pendingTarget: BluetoothDevice? = null
    private var isRegistered = false

    @Volatile
    private var lastReport: ByteArray = byteArrayOf(0, 0, 8, 0, 0, 0, 0)

    val isConnected: Boolean
        get() = connectedHost != null && hidDevice != null

    private val hidCallback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            isRegistered = registered
            if (registered) {
                onStatusChanged("HID Gamepad registered. Connecting to PC...")
                val target = pendingTarget ?: pluggedDevice
                if (target != null) {
                    hidDevice?.connect(target)
                }
            } else {
                connectedHost = null
                onStatusChanged("HID Gamepad unregistered.")
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    connectedHost = device
                    onStatusChanged("HID Connected to ${device?.name ?: "PC"}! Move OTG joystick now.")
                }
                BluetoothProfile.STATE_CONNECTING -> {
                    onStatusChanged("HID Connecting to ${device?.name ?: "PC"}...")
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    if (connectedHost == device) {
                        connectedHost = null
                    }
                    onStatusChanged("HID Disconnected from ${device?.name ?: "PC"}.")
                }
            }
        }

        override fun onGetReport(device: BluetoothDevice?, type: Byte, id: Byte, bufferSize: Int) {
            if (device != null && type == BluetoothHidDevice.REPORT_TYPE_INPUT) {
                hidDevice?.replyReport(device, type, id, lastReport)
            }
        }
    }

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                hidDevice = proxy as BluetoothHidDevice
                registerApp()
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                hidDevice = null
                connectedHost = null
                isRegistered = false
                onStatusChanged("HID service disconnected.")
            }
        }
    }

    fun connect(targetDevice: BluetoothDevice) {
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            onStatusChanged("Please turn on Bluetooth first.")
            return
        }
        pendingTarget = targetDevice
        if (hidDevice == null) {
            onStatusChanged("Starting Bluetooth HID profile...")
            adapter.getProfileProxy(context, profileListener, BluetoothProfile.HID_DEVICE)
        } else if (!isRegistered) {
            registerApp()
        } else {
            onStatusChanged("Connecting HID to ${targetDevice.name}...")
            hidDevice?.connect(targetDevice)
        }
    }

    private fun registerApp() {
        val device = hidDevice ?: return
        val sdp = BluetoothHidDeviceAppSdpSettings(
            "BT Joystick Bridge",
            "OTG USB Joystick to PC Bluetooth Gamepad",
            "BTJoystickBridge",
            BluetoothHidDevice.SUBCLASS2_GAMEPAD,
            HidGamepadDescriptor.DESCRIPTOR
        )
        val outQos = BluetoothHidDeviceAppQosSettings(
            BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT,
            800, 9, 0, 11250, BluetoothHidDeviceAppQosSettings.MAX
        )
        val ok = device.registerApp(sdp, null, outQos, executor, hidCallback)
        if (!ok) {
            onStatusChanged("HID registerApp failed. Make sure Bluetooth is ON and try again.")
        }
    }

    fun sendState(state: GamepadState) {
        val host = connectedHost ?: return
        val device = hidDevice ?: return
        val report = state.toHidReport()
        lastReport = report
        try {
            device.sendReport(host, HidGamepadDescriptor.REPORT_ID, report)
        } catch (_: Exception) {
            // Ignore transient write errors while disconnecting
        }
    }

    fun disconnect() {
        try {
            connectedHost?.let { hidDevice?.disconnect(it) }
            if (isRegistered) {
                hidDevice?.unregisterApp()
            }
            hidDevice?.let {
                bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, it)
            }
        } catch (_: Exception) {
        } finally {
            connectedHost = null
            hidDevice = null
            isRegistered = false
            pendingTarget = null
            onStatusChanged("HID Disconnected.")
        }
    }
}
