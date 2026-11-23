# Gobbledy: putting it on your Galaxy S25 Ultra

You only need to do this once. It takes about 15 minutes, plus the model download.

You'll need:
- the file **Gobbledy.apk** (in this folder, next to this guide)
- your phone's USB-C cable
- Wi-Fi on the phone (the speech model is about 731 MB)

---

## Step 1: Copy the app to your phone

1. Plug the phone into the PC with the USB cable and unlock the phone.
2. On the phone, pull down the notification shade. Tap the **"Charging this device via USB"** notification (it may say "USB for file transfer"), then choose **Transferring files / Android Auto**.
3. On the PC, open **File Explorer**. Click **Galaxy S25 Ultra** in the left panel, then **Phone** (or **Internal storage**), then the **Download** folder.
4. Drag **Gobbledy.apk** from this folder into the phone's **Download** folder.
5. Unplug the phone.

## Step 2: Install it

1. On the phone, open the **My Files** app (it's in the Samsung folder on the home screen, or swipe up and search "My Files").
2. Tap **Downloads**, then tap **Gobbledy.apk**.
3. If you see **"For your security, your phone currently isn't allowed to install unknown apps from this source"**: tap **Settings**, turn on **Allow from this source**, then press the back button.
4. Tap **Install**. If Google Play Protect asks about it, tap **More details**, then **Install anyway**. It warns because the app wasn't installed from the Play Store, and that's expected here.
5. Tap **Open**.

## Step 3: Set it up inside the app

The app's screen has numbered sections. Work down from the top. Each line shows ✅ when it's done and ⚠️ when it still needs doing.

### 1. Setup
1. **Microphone**: tap **Allow**, then **While using the app**.
2. **Notifications**: tap **Allow**, then **Allow**.
3. **Display over other apps**: tap **Open "Display over other apps"**, turn on the switch next to **Gobbledy**, then press back.
4. **Accessibility**: Samsung blocks this at first for apps from outside the Galaxy Store, so do it in this order:
   1. Tap **Open Accessibility settings** → **Installed apps** → **Gobbledy** and try to turn on the switch. A **"Restricted setting"** box appears. Tap **OK**.
   2. Press back until you're in Gobbledy again, then tap **Open App info**.
   3. Tap the **three dots (⋮)** at the top right → **Allow restricted settings**, then confirm with your fingerprint, face or PIN.
   4. Press back to Gobbledy, tap **Open Accessibility settings** → **Installed apps** → **Gobbledy**, turn on the switch, then tap **Allow**.
5. **Battery**: tap **Open Battery settings** (or go to **Settings > Battery** yourself), then:
   **Background usage limits** → **Never sleeping apps** → **+** at the top right → tick **Gobbledy** → **Add**.

### 2. Speech model
1. Make sure the phone is on Wi-Fi.
2. Tap **Download model**. A progress bar appears, and you can leave the app while it downloads.
3. If it stops (say the Wi-Fi drops), come back and tap **Resume download**. It picks up where it left off.
4. When it says **"✅ Ready"**, the app works completely offline.

### 3. Floating button
1. Turn on **Show floating button**. A round teal button appears on the right of the screen.
2. Use the **Button size** slider if you'd like it bigger or smaller.
3. Turn on **Launch at boot** if you want the button back after a restart. Android sometimes won't allow this straight away. If so, you'll get a notification saying **"Tap to turn on Gobbledy"**. Tap it.

## Step 4: Use it

1. Open any app (Gmail, Messages, Chrome, Google Docs…) and tap in a text box so the cursor is there.
2. **Press and hold** the floating button. It buzzes and turns **red**. Speak.
3. **Let go.** It buzzes again and turns **amber** while it works, then your words appear at the cursor.
4. If it can't type into that box, it copies the text instead and shows a message. **Long-press** in the box and tap **Paste**.

To **move** the button, put your finger on it and drag straight away (a quick drag won't start a recording).

Button colours:

| Colour | Meaning |
|---|---|
| Teal | Ready |
| Red | Recording |
| Amber | Transcribing |
| Grey | Loading the model, or no model downloaded yet |
| Dark red | The model failed to load. Hold the button to retry. |

To turn the button off, pull down the notification shade and tap **Turn off** on the Gobbledy notification, or switch off **Show floating button** in the app.

## If something isn't right

- **Text goes in oddly in one app** (such as formatting lost in an email): in Gobbledy, turn on **Always paste via clipboard** under **4. Text**.
- **A spelling is wrong or missing**: tap **Edit word list** under **4. Text**. Add a line such as `aluminum > aluminium` and tap **Save**. To stop a word ever changing, add a line like `!Labor`.
- **The button vanished**: open Gobbledy and turn on **Show floating button** again. Check the **Never sleeping apps** step above.
- **"Accessibility is off, so the text is copied"**: redo the Accessibility step. Samsung sometimes turns it off after an app update.

## Updating the app later (on the PC)

Right-click **build-apk.ps1** in this folder → **Run with PowerShell**. When it finishes, copy the new **Gobbledy.apk** to the phone and install it over the top (Steps 1 and 2). Your settings and the downloaded model are kept.
