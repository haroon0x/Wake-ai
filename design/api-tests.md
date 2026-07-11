# Wake — Pre-event API Tests

Two throwaway tests in one scratch app. This is learning/validation code only — never presented, never copied into the hackathon repo.

- **Test 1 — Notification / MessagingStyle:** confirm a real WhatsApp notification gives per-message sender + text.
- **Test 2 — ACTION_SET_TEXT:** confirm you can insert a draft into WhatsApp's input box (with clipboard fallback).

Both run on the Realme 14 Pro+ over USB.

---

## One-time setup

Make a new empty Android Studio project (Kotlin, empty activity), package `com.wake.tests`. Add the dependency for `NotificationCompat`:

```
implementation("androidx.core:core-ktx:1.13.1")
```

Everything below goes into that one throwaway app. You watch results in logcat:

```
adb logcat -s WAKE
```

---

## Test 1 — Notification MessagingStyle

### 1a. Service code — `NotifTest.kt`

```kotlin
package com.wake.tests

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat

class NotifTest : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val n = sbn.notification ?: return
        val style = NotificationCompat.MessagingStyle
            .extractMessagingStyleFromNotification(n)
        if (style == null) {
            Log.d("WAKE", "NO MessagingStyle pkg=${sbn.packageName} title=${n.extras.getCharSequence("android.title")} text=${n.extras.getCharSequence("android.text")}")
            return
        }
        for (m in style.messages) {
            Log.d("WAKE", "MSG pkg=${sbn.packageName} sender=${m.person?.name} text=${m.text} ts=${m.timestamp}")
        }
    }
}
```

### 1b. Manifest — inside `<application>`

```xml
<service
    android:name=".NotifTest"
    android:label="WakeNotifTest"
    android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"
    android:exported="false">
    <intent-filter>
        <action android:name="android.service.notification.NotificationListenerService" />
    </intent-filter>
</service>
```

### 1c. Run it

1. Install: `adb install -r app/build/outputs/apk/debug/app-debug.apk` (or just Run from Android Studio).
2. Grant notification access (fast path):
   ```
   adb shell cmd notification allow_listener com.wake.tests/com.wake.tests.NotifTest
   ```
   (Or Settings → Notification access → enable WakeNotifTest.)
3. Start logcat: `adb logcat -s WAKE`
4. From another phone, send yourself a **real WhatsApp message.**

### 1d. Pass / fail

- **PASS:** logcat prints `MSG ... sender=<name> text=<the message>`. This is what you want — per-message sender is real on your WhatsApp version.
- **FAIL (`NO MessagingStyle`):** WhatsApp isn't populating MessagingStyle for you. Fallback: parse `android.title` (sender) and `android.text` (message) from extras, shown in the log line. Quality is lower on grouped notifications but usable.

---

## Test 2 — ACTION_SET_TEXT into WhatsApp

### 2a. Service code — `SetTextTest.kt`

```kotlin
package com.wake.tests

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class SetTextTest : AccessibilityService() {
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            val node = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (node == null) {
                Log.d("WAKE", "no focused input")
                return
            }
            val args = Bundle()
            args.putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                "hello from wake"
            )
            val ok = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            Log.d("WAKE", "setText ok=$ok editable=${node.isEditable} pkg=${node.packageName}")
            if (!ok) {
                val clip = ClipData.newPlainText("wake", "hello from wake")
                (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
                val pasted = node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
                Log.d("WAKE", "fallback paste ok=$pasted")
            }
        }
    }

    override fun onServiceConnected() {
        registerReceiver(receiver, IntentFilter("com.wake.SETTEXT"), Context.RECEIVER_EXPORTED)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onDestroy() { unregisterReceiver(receiver) }
}
```

### 2b. Config — `res/xml/accessibility_config.xml`

```xml
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeAllMask"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:accessibilityFlags="flagDefault"
    android:canRetrieveWindowContent="true"
    android:notificationTimeout="100" />
```

### 2c. Manifest — inside `<application>`

```xml
<service
    android:name=".SetTextTest"
    android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
    android:exported="false">
    <intent-filter>
        <action android:name="android.accessibilityservice.AccessibilityService" />
    </intent-filter>
    <meta-data
        android:name="android.accessibilityservice"
        android:resource="@xml/accessibility_config" />
</service>
```

### 2d. Run it

1. Reinstall / Run.
2. Enable accessibility (fast path):
   ```
   adb shell settings put secure enabled_accessibility_services com.wake.tests/com.wake.tests.SetTextTest
   adb shell settings put secure accessibility_enabled 1
   ```
   (Or Settings → Accessibility → WakeNotifTest → enable. Confirm the scary permission dialog.)
3. Start logcat: `adb logcat -s WAKE`
4. Open a WhatsApp chat, **tap the message input box so the cursor is blinking in it.**
5. Fire the trigger:
   ```
   adb shell am broadcast -a com.wake.SETTEXT
   ```

### 2e. Pass / fail

- **PASS:** "hello from wake" appears in the WhatsApp input box, logcat shows `setText ok=true`. Draft-insert works — the reply act is viable.
- **PARTIAL:** `ok=false` but `fallback paste ok=true` and text appears. Fine — use the clipboard + `ACTION_PASTE` path in the real app.
- **FAIL:** nothing appears and both are false. WhatsApp's input rejects programmatic text. Drop draft-insert from the demo; keep "resume context" (reopen app + URL) as the single agent action instead.

---

## After both tests

- [ ] Test 1 result recorded (MessagingStyle works, or use extras fallback).
- [ ] Test 2 result recorded (SET_TEXT works, paste fallback, or drop the act).
- [ ] Delete/park this scratch app. Do NOT reuse its code in the hackathon repo — rebuild fresh on the day.
