"""
BT Joystick Bridge - Windows PC Xbox 360 (XInput) Receiver
----------------------------------------------------------
Receives controller packets from the Android app (Mode B: Bluetooth SPP)
and emulates a native Xbox 360 (XInput) controller on Windows 10/11.

Requirements (run once on Windows PC):
    pip install vgamepad pyserial

Usage:
    1) Connect via Windows Bluetooth Outgoing COM Port:
       python pc_xinput_bridge.py --port COM5

    2) OR connect directly via Phone Bluetooth MAC address (Python 3.9+ on Windows 10):
       python pc_xinput_bridge.py --mac AA:BB:CC:DD:EE:FF
"""

import argparse
import socket
import sys
import time

try:
    import vgamepad as vg
except ImportError:
    print("ERROR: 'vgamepad' is not installed. Run: pip install vgamepad pyserial")
    sys.exit(1)

BUTTON_MAP = [
    (0, vg.XUSB_BUTTON.XUSB_GAMEPAD_A),
    (1, vg.XUSB_BUTTON.XUSB_GAMEPAD_B),
    (2, vg.XUSB_BUTTON.XUSB_GAMEPAD_X),
    (3, vg.XUSB_BUTTON.XUSB_GAMEPAD_Y),
    (4, vg.XUSB_BUTTON.XUSB_GAMEPAD_LEFT_SHOULDER),
    (5, vg.XUSB_BUTTON.XUSB_GAMEPAD_RIGHT_SHOULDER),
    (8, vg.XUSB_BUTTON.XUSB_GAMEPAD_BACK),
    (9, vg.XUSB_BUTTON.XUSB_GAMEPAD_START),
    (10, vg.XUSB_BUTTON.XUSB_GAMEPAD_LEFT_THUMB),
    (11, vg.XUSB_BUTTON.XUSB_GAMEPAD_RIGHT_THUMB),
]


def apply_state(pad: "vg.VX360Gamepad", line: str):
    parts = line.strip().split(",")
    if len(parts) != 7 or parts[0] != "G":
        return

    buttons = int(parts[1])
    hat = int(parts[2])
    lx = int(parts[3])
    ly = int(parts[4])
    rx = int(parts[5])
    ry = int(parts[6])

    pad.reset()

    for bit, xusb_btn in BUTTON_MAP:
        if buttons & (1 << bit):
            pad.press_button(button=xusb_btn)

    # L2 / R2 digital triggers (bits 6 & 7)
    pad.left_trigger(value=255 if (buttons & (1 << 6)) else 0)
    pad.right_trigger(value=255 if (buttons & (1 << 7)) else 0)

    # D-Pad Hat Switch (0=N, 1=NE, 2=E, 3=SE, 4=S, 5=SW, 6=W, 7=NW, 8=Neutral)
    if hat in (7, 0, 1):
        pad.press_button(vg.XUSB_BUTTON.XUSB_GAMEPAD_DPAD_UP)
    if hat in (3, 4, 5):
        pad.press_button(vg.XUSB_BUTTON.XUSB_GAMEPAD_DPAD_DOWN)
    if hat in (5, 6, 7):
        pad.press_button(vg.XUSB_BUTTON.XUSB_GAMEPAD_DPAD_LEFT)
    if hat in (1, 2, 3):
        pad.press_button(vg.XUSB_BUTTON.XUSB_GAMEPAD_DPAD_RIGHT)

    # Analog sticks (-127..127 -> -1.0..1.0; invert Y for XInput)
    pad.left_joystick_float(
        x_value_float=max(-1.0, min(1.0, lx / 127.0)),
        y_value_float=max(-1.0, min(1.0, -ly / 127.0)),
    )
    pad.right_joystick_float(
        x_value_float=max(-1.0, min(1.0, rx / 127.0)),
        y_value_float=max(-1.0, min(1.0, -ry / 127.0)),
    )
    pad.update()


def run_serial(port: str, baud: int, pad: "vg.VX360Gamepad"):
    import serial

    print(f"Connecting to Bluetooth COM port {port}...")
    with serial.Serial(port, baudrate=baud, timeout=1) as ser:
        print(f"Connected on {port}! Virtual Xbox 360 Controller is active.")
        while True:
            raw = ser.readline()
            if not raw:
                continue
            try:
                apply_state(pad, raw.decode("utf-8", errors="ignore"))
            except ValueError:
                pass


def run_rfcomm(mac: str, channel: int, pad: "vg.VX360Gamepad"):
    print(f"Connecting via Bluetooth RFCOMM to {mac} (channel {channel})...")
    sock = socket.socket(socket.AF_BLUETOOTH, socket.SOCK_STREAM, socket.BTPROTO_RFCOMM)
    sock.connect((mac, channel))
    print("Connected via Bluetooth RFCOMM! Virtual Xbox 360 Controller is active.")
    buf = ""
    try:
        while True:
            data = sock.recv(1024)
            if not data:
                break
            buf += data.decode("utf-8", errors="ignore")
            while "\n" in buf:
                line, buf = buf.split("\n", 1)
                try:
                    apply_state(pad, line)
                except ValueError:
                    pass
    finally:
        sock.close()


def main():
    parser = argparse.ArgumentParser(description="BT Joystick Bridge -> Windows Xbox 360 XInput")
    parser.add_argument("--port", help="Windows Bluetooth Outgoing COM port (e.g. COM5)")
    parser.add_argument("--baud", type=int, default=115200, help="Serial baud rate")
    parser.add_argument("--mac", help="Phone Bluetooth MAC address (e.g. AA:BB:CC:DD:EE:FF)")
    parser.add_argument("--channel", type=int, default=1, help="RFCOMM channel (default: 1)")
    args = parser.parse_args()

    if not args.port and not args.mac:
        print("Provide either --port COMx OR --mac AA:BB:CC:DD:EE:FF")
        sys.exit(1)

    pad = vg.VX360Gamepad()
    print("Virtual Xbox 360 (XInput) controller created! Check 'joy.cpl' in Windows.")

    while True:
        try:
            if args.port:
                run_serial(args.port, args.baud, pad)
            else:
                run_rfcomm(args.mac, args.channel, pad)
        except KeyboardInterrupt:
            print("\nStopped by user.")
            break
        except Exception as exc:
            print(f"Connection lost ({exc}). Retrying in 3 seconds...")
            time.sleep(3)


if __name__ == "__main__":
    main()
