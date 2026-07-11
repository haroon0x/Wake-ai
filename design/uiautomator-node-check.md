# UIAutomator Node-Tree Check

This check verifies whether Chrome, WhatsApp, and Gmail expose useful visible text through Android's accessibility/UI node tree on the actual phone intended for the Wake demo.

This is not an Android Studio coding task. Android Studio is optional. The required setup is:

- The real demo phone
- Developer options and USB debugging enabled
- `adb` available on the laptop
- Chrome, WhatsApp, and Gmail installed and logged in

An emulator may be used for an initial check, but it does not validate the actual phone's Android version, OEM build, or app versions. The final check must be performed on the real demo phone.

## 1. Confirm the Android target

Run these commands from the laptop terminal:

```bash
adb devices
```

One device should be listed with status `device`. Do not continue if the status is `unauthorized` or `offline`.

Record the device build if useful:

```bash
adb shell getprop ro.product.model
adb shell getprop ro.build.version.release
adb shell getprop ro.build.version.incremental
```

## 2. Check Chrome

On the phone:

1. Open Chrome.
2. Navigate to a page containing deliberately recognizable text, such as `WAKE_TEST_CODE_7741`, a visible heading, and a visible paragraph.
3. Leave that page visibly open.

On the laptop:

```bash
adb shell uiautomator dump /sdcard/wake-chrome.xml
adb pull /sdcard/wake-chrome.xml ./wake-chrome.xml
```

Inspect the dump:

```bash
less wake-chrome.xml
```

Search for useful attributes and the test string:

```bash
rg 'WAKE_TEST_CODE_7741|text=|content-desc=|resource-id=' wake-chrome.xml
```

Chrome passes if the dump contains the page heading, paragraph text, and test code, along with useful node attributes. It should not expose only a generic web-view node.

## 3. Check WhatsApp

Use a controlled test conversation rather than private personal messages.

On the phone:

1. Open the test WhatsApp conversation.
2. Make sure the contact name is visible.
3. Make sure at least two message texts are visible.
4. Make sure the message composer is visible.

On the laptop:

```bash
adb shell uiautomator dump /sdcard/wake-whatsapp.xml
adb pull /sdcard/wake-whatsapp.xml ./wake-whatsapp.xml
```

Inspect the dump:

```bash
less wake-whatsapp.xml
```

Search for useful attributes:

```bash
rg 'conversation_contact_name|text=|content-desc=|resource-id=' wake-whatsapp.xml
```

WhatsApp passes if the dump exposes useful visible content such as the contact or conversation name, visible message text, message-related accessibility labels, and the composer/input field.

## 4. Check Gmail

Use a test email where possible.

On the phone:

1. Open Gmail.
2. Show an inbox containing test mail, or open one test email.
3. Ensure sender, subject, and body text are visible.

On the laptop:

```bash
adb shell uiautomator dump /sdcard/wake-gmail.xml
adb pull /sdcard/wake-gmail.xml ./wake-gmail.xml
```

Inspect the dump:

```bash
less wake-gmail.xml
```

Search for useful attributes:

```bash
rg 'text=|content-desc=|resource-id=' wake-gmail.xml
```

Gmail passes if the dump exposes useful sender, subject, and visible body text.

## What “rich text nodes” means

The check is successful when the accessibility tree contains meaningful visible content that Wake can capture through `AccessibilityService`, for example:

```xml
<node text="WAKE_TEST_CODE_7741" ... />
<node text="Registration instructions" ... />
<node text="Message from Test Contact" ... />
```

The check fails if the tree contains only generic containers or a blank web-view node, for example:

```xml
<node class="android.webkit.WebView" text="" ... />
```

The dump does not need to contain every pixel or hidden element. It must contain enough meaningful visible text for Wake to reconstruct what the user was doing.

## Important limitation

Run the dump separately while each app is in the foreground. `uiautomator dump` captures the currently visible UI tree; it does not inspect the entire app or historical screens.

Record the result:

```text
Chrome: PASS/FAIL — useful page text exposed
WhatsApp: PASS/FAIL — contact and message text exposed
Gmail: PASS/FAIL — sender, subject, and body exposed
Phone model/build: ...
Android version: ...
App versions: ...
```

If an app fails on the real phone, do not assume the research statement applies to that build. Treat that app as unreliable for the demo and redesign the demo around the apps that passed.
