# BT Joystick Bridge (OTG USB Joystick → Phone → PC via Bluetooth)

यह Android ऐप आपके **USB OTG Joystick (जैसे Quantum USB Gamepad)** को फोन से जोड़कर **Bluetooth के ज़रिए Windows PC** पर गेम कंट्रोलर के रूप में चलाता है। इसमें टेस्टिंग के लिए On-Screen टेस्ट पैड भी दिया गया है।

## इसमें 2 मोड हैं:
1. **Mode A — Native Bluetooth HID Gamepad (बिना किसी PC सॉफ्टवेयर के):**
   - आपका फोन सीधे Windows 10/11 में एक **Bluetooth Gamepad** की तरह जुड़ जाता है।
2. **Mode B — Bluetooth SPP → PC Xbox 360 (XInput) Bridge:**
   - जिन PC/Xbox गेम्स में सिर्फ **XInput (Xbox 360 Controller)** चलता है, उनके लिए `pc_server/pc_xinput_bridge.py` स्क्रिप्ट साथ में दी गई है।

---

## GitHub से APK कैसे बनाएं (बिना Android Studio के)

1. इस ZIP फ़ाइल को Extract करें।
2. GitHub पर एक नया Repository बनाएं (जैसे `bt-joystick-bridge`)।
3. Extract की गई सभी फ़ाइलें और फ़ोल्डर (`.github/`, `app/`, `build.gradle`, `settings.gradle`, `gradle.properties`) Repository के root में अपलोड करें।
4. GitHub में ऊपर **Actions** टैब पर जाएं → **Build BT Joystick Bridge APK** वर्कफ़्लो अपने आप चल जाएगा (या **Run workflow** दबाएं)।
5. 2–3 मिनट में बिल्ड पूरा होने के बाद नीचे **Artifacts** से `BTJoystickBridge-debug-apk` डाउनलोड करें और उसके अंदर से `app-debug.apk` फोन में इंस्टॉल करें।

---

## फोन और PC पर कैसे चलाएं

### Step 1: OTG Joystick जोड़ें
- अपने Quantum USB Joystick को OTG केबल से फोन में लगाएं।
- ऐप खोलते ही ऊपर **"Connected OTG Joystick"** में आपके कंट्रोलर का नाम और नीचे बटन/स्टिक का लाइव डेटा दिखने लगेगा।

### Step 2 (Mode A — Direct Bluetooth HID, कोई PC सॉफ्टवेयर नहीं)
1. Windows PC और फोन को सामान्य Bluetooth Settings से **Pair** करें।
2. ऐप में **Mode A (Native Bluetooth HID Gamepad)** चुनें।
3. Dropdown में अपना Windows PC चुनें और **Connect PC** दबाएं।
4. PC पर `Win + R` दबाकर `joy.cpl` खोलें — आपका कंट्रोलर वहाँ दिखेगा।

### Step 2 (Mode B — Xbox 360 XInput गेम्स के लिए)
1. Windows PC पर एक बार यह कमांड चलाएं:
   ```cmd
   pip install -r pc_server/requirements.txt
   ```
2. Windows Bluetooth Settings → **More Bluetooth options** → **COM Ports** → **Add** → **Outgoing** में अपना फोन चुनें और COM Port नंबर देखें (जैसे `COM5`)।
3. फोन ऐप में **Mode B (Bluetooth SPP)** चुनकर **Connect PC** दबाएं।
4. PC पर `pc_server/start_pc_bridge.bat` चलाएं और अपना COM Port (जैसे `COM5`) डालें। अब PC में **Xbox 360 (XInput)** कंट्रोलर बन जाएगा।
