package com.btjoystick.bridge

/**
 * Standard Bluetooth HID Gamepad Descriptor (Report ID 1, 7-byte payload):
 * - 16 Buttons (2 bytes)
 * - 8-way Hat Switch / D-Pad + 4-bit padding (1 byte)
 * - 4 Analog Axes: X, Y, Z, Rz (-127..127, 4 bytes)
 */
object HidGamepadDescriptor {
    const val REPORT_ID = 1

    val DESCRIPTOR: ByteArray = byteArrayOf(
        0x05.toByte(), 0x01.toByte(),       // Usage Page (Generic Desktop)
        0x09.toByte(), 0x05.toByte(),       // Usage (Game Pad)
        0xA1.toByte(), 0x01.toByte(),       // Collection (Application)
        0x85.toByte(), REPORT_ID.toByte(),  //   Report ID (1)

        // 16 Buttons
        0x05.toByte(), 0x09.toByte(),       //   Usage Page (Button)
        0x19.toByte(), 0x01.toByte(),       //   Usage Minimum (Button 1)
        0x29.toByte(), 0x10.toByte(),       //   Usage Maximum (Button 16)
        0x15.toByte(), 0x00.toByte(),       //   Logical Minimum (0)
        0x25.toByte(), 0x01.toByte(),       //   Logical Maximum (1)
        0x75.toByte(), 0x01.toByte(),       //   Report Size (1)
        0x95.toByte(), 0x10.toByte(),       //   Report Count (16)
        0x81.toByte(), 0x02.toByte(),       //   Input (Data, Variable, Absolute)

        // Hat Switch (D-Pad) + 4-bit padding
        0x05.toByte(), 0x01.toByte(),       //   Usage Page (Generic Desktop)
        0x09.toByte(), 0x39.toByte(),       //   Usage (Hat switch)
        0x15.toByte(), 0x00.toByte(),       //   Logical Minimum (0)
        0x25.toByte(), 0x07.toByte(),       //   Logical Maximum (7)
        0x35.toByte(), 0x00.toByte(),       //   Physical Minimum (0)
        0x46.toByte(), 0x3B.toByte(), 0x01.toByte(), // Physical Maximum (315)
        0x65.toByte(), 0x14.toByte(),       //   Unit (Eng Rot: Angular Pos)
        0x75.toByte(), 0x04.toByte(),       //   Report Size (4)
        0x95.toByte(), 0x01.toByte(),       //   Report Count (1)
        0x81.toByte(), 0x42.toByte(),       //   Input (Data, Variable, Absolute, Null State)
        0x75.toByte(), 0x04.toByte(),       //   Report Size (4)
        0x95.toByte(), 0x01.toByte(),       //   Report Count (1)
        0x81.toByte(), 0x03.toByte(),       //   Input (Constant)

        // 4 Analog Axes: X, Y, Z, Rz
        0x05.toByte(), 0x01.toByte(),       //   Usage Page (Generic Desktop)
        0x09.toByte(), 0x30.toByte(),       //   Usage (X)
        0x09.toByte(), 0x31.toByte(),       //   Usage (Y)
        0x09.toByte(), 0x32.toByte(),       //   Usage (Z)
        0x09.toByte(), 0x35.toByte(),       //   Usage (Rz)
        0x15.toByte(), 0x81.toByte(),       //   Logical Minimum (-127)
        0x25.toByte(), 0x7F.toByte(),       //   Logical Maximum (127)
        0x75.toByte(), 0x08.toByte(),       //   Report Size (8)
        0x95.toByte(), 0x04.toByte(),       //   Report Count (4)
        0x81.toByte(), 0x02.toByte(),       //   Input (Data, Variable, Absolute)

        0xC0.toByte()                       // End Collection
    )
}
