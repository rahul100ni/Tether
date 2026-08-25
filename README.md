# Tether 🟢

*Block distracting apps — the physical way.*

This is my personal app for blocking distracting apps on Android. Basically, I wanted a way to lock myself out of apps where the only way to unlock them is by physically scanning an NFC tag or a QR code that I've hidden somewhere annoying. No easy "turn off" buttons.

It was originally a clone of another project, but I've completely ripped out the UI, redesigned it to look like a sleek hacker tool (OLED blacks, neon emerald greens, Inter font), and made it my own. 

## How it works

1. Pick the apps you want to block (Instagram, YouTube, whatever).
2. Write to a cheap NFC tag using the app, or generate and print out the QR code.
3. Hit **INITIATE SESSION**.
4. To unlock, you actually have to get up, find the NFC tag or QR code, and scan it. Friction works.

### Strict Mode
If you turn this on, you can't even stop the session normally. You have to open Tether first and scan your code. Scanning on the actual block screen just gives you a temporary unlock for that specific app.

### Tasker / Automation
You can start and stop this using Tasker or MacroDroid if you enable automation in settings. Send these intents:

**Start:**
`am broadcast -n com.vakya.tether/.ScheduleReceiver -a com.vakya.tether.SCHEDULE_START`

**Stop:**
`am broadcast -n com.vakya.tether/.ScheduleReceiver -a com.vakya.tether.SCHEDULE_STOP`

## Tech Stack
- Kotlin & Jetpack Compose
- Room Database
- ZXing & Coil
- Complete OLED Dark Theme

*(Note: Still a work-in-progress personal build. UI screenshots coming whenever I feel like taking them.)*
