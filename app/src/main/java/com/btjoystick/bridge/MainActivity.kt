package com.btjoystick.bridge

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.input.InputManager
import android.os.Build
import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity(), InputManager.InputDeviceListener {

    private val gamepadState = GamepadState()
    private lateinit var inputManager: InputManager
    private var bluetoothAdapter: BluetoothAdapter? = null
    private lateinit var hidManager: BluetoothHidManager
    private lateinit var sppManager: BluetoothSppManager

    private val pairedDevices = mutableListOf<BluetoothDevice>()
    private val logLines = ArrayDeque<String>()
    private var analogL2Active = false
    private var analogR2Active = false

    private lateinit var tvOtgStatus: TextView
    private lateinit var tvLiveInput: TextView
    private lateinit var tvBtStatus: TextView
    private lateinit var tvLog: TextView
    private lateinit var spinnerDevices: Spinner
    private lateinit var rgMode: RadioGroup
    private lateinit var rbHidMode: RadioButton
    private lateinit var rbSppMode: RadioButton

    companion object {
        private const val REQ_BT_PERMISSIONS = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvOtgStatus = findViewById(R.id.tvOtgStatus)
        tvLiveInput = findViewById(R.id.tvLiveInput)
        tvBtStatus = findViewById(R.id.tvBtStatus)
        tvLog = findViewById(R.id.tvLog)
        spinnerDevices = findViewById(R.id.spinnerDevices)
        rgMode = findViewById(R.id.rgMode)
        rbHidMode = findViewById(R.id.rbHidMode)
        rbSppMode = findViewById(R.id.rbSppMode)

        inputManager = getSystemService(Context.INPUT_SERVICE) as InputManager
        val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = btManager.adapter

        hidManager = BluetoothHidManager(this, bluetoothAdapter) { msg ->
            runOnUiThread {
                tvBtStatus.text = "Status: $msg"
                appendLog(msg)
            }
        }
        sppManager = BluetoothSppManager(bluetoothAdapter) { msg ->
            runOnUiThread {
                tvBtStatus.text = "Status: $msg"
                appendLog(msg)
            }
        }

        findViewById<Button>(R.id.btnRefresh).setOnClickListener {
            refreshPairedDevices()
            refreshOtgJoysticks()
        }

        findViewById<Button>(R.id.btnConnect).setOnClickListener {
            if (!hasBluetoothPermissions()) {
                requestBluetoothPermissions()
                return@setOnClickListener
            }
            if (rbHidMode.isChecked) {
                sppManager.stop()
                val idx = spinnerDevices.selectedItemPosition
                if (idx !in pairedDevices.indices) {
                    appendLog("No paired PC selected. Pair phone with Windows PC in Bluetooth settings first.")
                    return@setOnClickListener
                }
                hidManager.connect(pairedDevices[idx])
            } else {
                hidManager.disconnect()
                sppManager.startServer()
            }
        }

        findViewById<Button>(R.id.btnDisconnect).setOnClickListener {
            hidManager.disconnect()
            sppManager.stop()
            gamepadState.reset()
            updateLiveUi()
        }

        setupOnScreenTestButtons()

        if (!hasBluetoothPermissions()) {
            requestBluetoothPermissions()
        } else {
            refreshPairedDevices()
        }
        refreshOtgJoysticks()
    }

    override fun onResume() {
        super.onResume()
        inputManager.registerInputDeviceListener(this, null)
        refreshOtgJoysticks()
    }

    override fun onPause() {
        super.onPause()
        inputManager.unregisterInputDeviceListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        hidManager.disconnect()
        sppManager.stop()
    }

    // ---- OTG USB Joystick Detection ----

    override fun onInputDeviceAdded(deviceId: Int) {
        refreshOtgJoysticks()
        val dev = InputDevice.getDevice(deviceId)
        if (dev != null && isGamepadDevice(dev)) {
            appendLog("OTG Controller connected: ${dev.name}")
        }
    }

    override fun onInputDeviceRemoved(deviceId: Int) {
        refreshOtgJoysticks()
        appendLog("USB input device removed.")
    }

    override fun onInputDeviceChanged(deviceId: Int) {
        refreshOtgJoysticks()
    }

    private fun isGamepadDevice(dev: InputDevice): Boolean {
        val sources = dev.sources
        return (sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
            (sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
    }

    private fun refreshOtgJoysticks() {
        val gamepads = InputDevice.getDeviceIds()
            .mapNotNull { InputDevice.getDevice(it) }
            .filter { isGamepadDevice(it) }

        if (gamepads.isEmpty()) {
            tvOtgStatus.text = "OTG Joystick not detected — connect Quantum joystick via OTG cable"
        } else {
            val names = gamepads.joinToString(", ") { it.name }
            tvOtgStatus.text = "Connected OTG Joystick: $names"
        }
    }

    // ---- Physical OTG Joystick Input Handling ----

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val dev = event.device
        if (dev != null && isGamepadDevice(dev)) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount > 0) {
                return true
            }
            if (event.action == KeyEvent.ACTION_DOWN || event.action == KeyEvent.ACTION_UP) {
                val pressed = event.action == KeyEvent.ACTION_DOWN
                val handled = handleGamepadKey(event.keyCode, pressed)
                if (handled) {
                    transmitState()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        val isJoystick = (event.source and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK ||
            (event.source and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD

        if (isJoystick && event.action == MotionEvent.ACTION_MOVE) {
            val lx = event.getAxisValue(MotionEvent.AXIS_X)
            val ly = event.getAxisValue(MotionEvent.AXIS_Y)

            // Generic USB DirectInput joysticks (like Quantum) often use AXIS_Z + AXIS_RZ for Right Stick
            var rx = event.getAxisValue(MotionEvent.AXIS_Z)
            var ry = event.getAxisValue(MotionEvent.AXIS_RZ)
            if (rx == 0f && ry == 0f) {
                rx = event.getAxisValue(MotionEvent.AXIS_RX)
                ry = event.getAxisValue(MotionEvent.AXIS_RY)
            }

            gamepadState.setAxes(lx, ly, rx, ry)

            val dev = event.device
            if (dev == null ||
                dev.getMotionRange(MotionEvent.AXIS_HAT_X, event.source) != null ||
                dev.getMotionRange(MotionEvent.AXIS_HAT_Y, event.source) != null
            ) {
                val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
                val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
                gamepadState.setHatFromAxes(hatX, hatY)
            }

            // Analog triggers fallback to L2/R2 button bits
            val lTrig = maxOf(
                event.getAxisValue(MotionEvent.AXIS_LTRIGGER),
                event.getAxisValue(MotionEvent.AXIS_BRAKE)
            )
            val rTrig = maxOf(
                event.getAxisValue(MotionEvent.AXIS_RTRIGGER),
                event.getAxisValue(MotionEvent.AXIS_GAS)
            )
            if (lTrig > 0.35f) {
                analogL2Active = true
                gamepadState.setButton(6, true)
            } else if (analogL2Active && lTrig < 0.2f) {
                analogL2Active = false
                gamepadState.setButton(6, false)
            }
            if (rTrig > 0.35f) {
                analogR2Active = true
                gamepadState.setButton(7, true)
            } else if (analogR2Active && rTrig < 0.2f) {
                analogR2Active = false
                gamepadState.setButton(7, false)
            }

            transmitState()
            return true
        }
        return super.dispatchGenericMotionEvent(event)
    }

    private fun handleGamepadKey(keyCode: Int, pressed: Boolean): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                gamepadState.setDpadDirection(up = pressed)
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                gamepadState.setDpadDirection(down = pressed)
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                gamepadState.setDpadDirection(left = pressed)
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                gamepadState.setDpadDirection(right = pressed)
                return true
            }
        }

        val bit = when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_1 -> 0
            KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_2 -> 1
            KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_3 -> 2
            KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_BUTTON_4 -> 3
            KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_5 -> 4
            KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_BUTTON_6 -> 5
            KeyEvent.KEYCODE_BUTTON_L2, KeyEvent.KEYCODE_BUTTON_7 -> 6
            KeyEvent.KEYCODE_BUTTON_R2, KeyEvent.KEYCODE_BUTTON_8 -> 7
            KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BUTTON_9 -> 8
            KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_BUTTON_10 -> 9
            KeyEvent.KEYCODE_BUTTON_THUMBL, KeyEvent.KEYCODE_BUTTON_11 -> 10
            KeyEvent.KEYCODE_BUTTON_THUMBR, KeyEvent.KEYCODE_BUTTON_12 -> 11
            KeyEvent.KEYCODE_BUTTON_13 -> 12
            KeyEvent.KEYCODE_BUTTON_14 -> 13
            KeyEvent.KEYCODE_BUTTON_15 -> 14
            KeyEvent.KEYCODE_BUTTON_16 -> 15
            else -> -1
        }
        if (bit >= 0) {
            gamepadState.setButton(bit, pressed)
            return true
        }
        return false
    }

    // ---- On-Screen Fallback Test Pad ----

    @SuppressLint("ClickableViewAccessibility")
    private fun setupOnScreenTestButtons() {
        fun bindBitButton(viewId: Int, bit: Int) {
            findViewById<Button>(viewId).setOnTouchListener { v: View, event: MotionEvent ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        v.isPressed = true
                        gamepadState.setButton(bit, true)
                        transmitState()
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.isPressed = false
                        gamepadState.setButton(bit, false)
                        transmitState()
                        true
                    }
                    else -> false
                }
            }
        }

        fun bindDpadButton(viewId: Int, dir: String) {
            findViewById<Button>(viewId).setOnTouchListener { v: View, event: MotionEvent ->
                val isDown = when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> true
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> false
                    else -> return@setOnTouchListener false
                }
                v.isPressed = isDown
                when (dir) {
                    "U" -> gamepadState.setDpadDirection(up = isDown)
                    "D" -> gamepadState.setDpadDirection(down = isDown)
                    "L" -> gamepadState.setDpadDirection(left = isDown)
                    "R" -> gamepadState.setDpadDirection(right = isDown)
                }
                transmitState()
                true
            }
        }

        bindDpadButton(R.id.btnDpadUp, "U")
        bindDpadButton(R.id.btnDpadDown, "D")
        bindDpadButton(R.id.btnDpadLeft, "L")
        bindDpadButton(R.id.btnDpadRight, "R")

        bindBitButton(R.id.btnA, 0)
        bindBitButton(R.id.btnB, 1)
        bindBitButton(R.id.btnX, 2)
        bindBitButton(R.id.btnY, 3)
        bindBitButton(R.id.btnL1, 4)
        bindBitButton(R.id.btnR1, 5)
        bindBitButton(R.id.btnSelect, 8)
        bindBitButton(R.id.btnStart, 9)
    }

    // ---- Transmit & UI ----

    private fun transmitState() {
        updateLiveUi()
        if (rbHidMode.isChecked && hidManager.isConnected) {
            hidManager.sendState(gamepadState)
        } else if (rbSppMode.isChecked && sppManager.isConnected) {
            sppManager.sendState(gamepadState)
        }
    }

    private fun updateLiveUi() {
        tvLiveInput.text = gamepadState.formatDebug()
    }

    private fun appendLog(message: String) {
        if (logLines.size >= 8) {
            logLines.removeFirst()
        }
        logLines.addLast("• $message")
        tvLog.text = logLines.joinToString("\n")
    }

    // ---- Bluetooth Permissions & Paired Device List ----

    private fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestBluetoothPermissions() {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        ActivityCompat.requestPermissions(this, perms, REQ_BT_PERMISSIONS)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_BT_PERMISSIONS && hasBluetoothPermissions()) {
            refreshPairedDevices()
        } else {
            appendLog("Bluetooth permission denied. Allow Nearby Devices permission in App Settings.")
        }
    }

    @SuppressLint("MissingPermission")
    private fun refreshPairedDevices() {
        if (!hasBluetoothPermissions()) return
        val adapter = bluetoothAdapter
        if (adapter == null) {
            appendLog("Bluetooth not supported on this phone.")
            return
        }
        pairedDevices.clear()
        val bonded = adapter.bondedDevices ?: emptySet()
        pairedDevices.addAll(bonded.sortedBy { it.name ?: it.address })

        val labels = if (pairedDevices.isEmpty()) {
            listOf("No paired devices — pair with PC in Bluetooth Settings")
        } else {
            pairedDevices.map { "${it.name ?: "Unknown"} (${it.address})" }
        }
        val arrAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
        spinnerDevices.adapter = arrAdapter
        appendLog("Found ${pairedDevices.size} paired Bluetooth device(s).")
    }
}
