package com.btjoystick.bridge

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Holds the unified controller state read from either:
 * 1) A physical USB OTG Joystick (e.g., Quantum USB DirectInput controller), or
 * 2) The on-screen test buttons.
 *
 * Bit mapping (16 buttons):
 *   Bit 0  (0x0001): A / Button 1
 *   Bit 1  (0x0002): B / Button 2
 *   Bit 2  (0x0004): X / Button 3
 *   Bit 3  (0x0008): Y / Button 4
 *   Bit 4  (0x0010): L1 / Button 5
 *   Bit 5  (0x0020): R1 / Button 6
 *   Bit 6  (0x0040): L2 / Button 7
 *   Bit 7  (0x0080): R2 / Button 8
 *   Bit 8  (0x0100): Select / Back / Button 9
 *   Bit 9  (0x0200): Start / Button 10
 *   Bit 10 (0x0400): L3 / ThumbL / Button 11
 *   Bit 11 (0x0800): R3 / ThumbR / Button 12
 *   Bit 12..15:      Buttons 13..16
 */
class GamepadState {
    var buttons: Int = 0
        private set

    // Hat switch: 0=N, 1=NE, 2=E, 3=SE, 4=S, 5=SW, 6=W, 7=NW, 8=Neutral
    var hat: Int = 8
        private set

    // Signed 8-bit axes: -127..127
    var lx: Int = 0
        private set
    var ly: Int = 0
        private set
    var rx: Int = 0
        private set
    var ry: Int = 0
        private set

    private var dpadUp = false
    private var dpadDown = false
    private var dpadLeft = false
    private var dpadRight = false

    fun setButton(bitIndex: Int, pressed: Boolean) {
        if (bitIndex !in 0..15) return
        buttons = if (pressed) {
            buttons or (1 shl bitIndex)
        } else {
            buttons and (1 shl bitIndex).inv()
        }
    }

    fun setDpadDirection(up: Boolean? = null, down: Boolean? = null, left: Boolean? = null, right: Boolean? = null) {
        if (up != null) dpadUp = up
        if (down != null) dpadDown = down
        if (left != null) dpadLeft = left
        if (right != null) dpadRight = right
        recomputeHat()
    }

    fun setHatFromAxes(hatX: Float, hatY: Float) {
        dpadLeft = hatX < -0.5f
        dpadRight = hatX > 0.5f
        dpadUp = hatY < -0.5f
        dpadDown = hatY > 0.5f
        recomputeHat()
    }

    private fun recomputeHat() {
        hat = when {
            dpadUp && dpadRight -> 1
            dpadDown && dpadRight -> 3
            dpadDown && dpadLeft -> 5
            dpadUp && dpadLeft -> 7
            dpadUp -> 0
            dpadRight -> 2
            dpadDown -> 4
            dpadLeft -> 6
            else -> 8
        }
    }

    fun setAxes(leftX: Float, leftY: Float, rightX: Float, rightY: Float) {
        lx = floatToAxisByte(leftX)
        ly = floatToAxisByte(leftY)
        rx = floatToAxisByte(rightX)
        ry = floatToAxisByte(rightY)
    }

    private fun floatToAxisByte(value: Float): Int {
        val deadzone = 0.08f
        val filtered = if (abs(value) < deadzone) 0f else value.coerceIn(-1f, 1f)
        return (filtered * 127f).roundToInt().coerceIn(-127, 127)
    }

    fun reset() {
        buttons = 0
        dpadUp = false
        dpadDown = false
        dpadLeft = false
        dpadRight = false
        hat = 8
        lx = 0
        ly = 0
        rx = 0
        ry = 0
    }

    /**
     * 7-byte payload matching HidGamepadDescriptor (Report ID = 1):
     *   Byte 0: buttons low byte (bits 0..7)
     *   Byte 1: buttons high byte (bits 8..15)
     *   Byte 2: hat switch (low 4 bits)
     *   Byte 3: Left Stick X (-127..127)
     *   Byte 4: Left Stick Y (-127..127)
     *   Byte 5: Right Stick X (-127..127)
     *   Byte 6: Right Stick Y (-127..127)
     */
    fun toHidReport(): ByteArray {
        return byteArrayOf(
            (buttons and 0xFF).toByte(),
            ((buttons shr 8) and 0xFF).toByte(),
            (hat and 0x0F).toByte(),
            lx.toByte(),
            ly.toByte(),
            rx.toByte(),
            ry.toByte()
        )
    }

    /**
     * Compact ASCII packet for Bluetooth SPP -> PC XInput bridge.
     */
    fun toSppPacket(): String {
        return "G,$buttons,$hat,$lx,$ly,$rx,$ry\n"
    }

    fun formatDebug(): String {
        val hexBtn = String.format("0x%04X", buttons)
        return "BTN: $hexBtn | HAT: $hat | L:($lx, $ly) | R:($rx, $ry)"
    }
}
