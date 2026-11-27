@echo off
cd /d "%~dp0"
title BT Joystick Bridge - Xbox 360 XInput Receiver
echo ====================================================
echo   BT Joystick Bridge (Phone OTG -> PC Xbox 360)
echo ====================================================
echo.
set /p COMPORT=Enter Bluetooth Outgoing COM Port (example COM5): 
python pc_xinput_bridge.py --port %COMPORT%
pause
