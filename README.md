# GroupBridge (Android to Apple group chats)

An Android app for joining group chats with iPhone users. You add everyone in the
group (phone numbers, Gmail, iCloud and other email addresses) and the app:

- **Starts a group text** in your texting app (Google Messages, Samsung Messages…)
  with every phone number filled in. iPhones and Androids can all reply to it
  (MMS, or RCS on iOS 18 and later).
- **Writes a message to an iPhone member** telling them how to start a new group with
  you in it, with the full list of addresses ready to paste.
- **Emails the group** to reach members you only have an email for.
- **Copies all addresses** so you can paste them into the iPhone "To:" field.
- Shows which members you can reach and how:
  phone = group text, iCloud = iMessage only, Gmail and other emails = email.

You can add people by typing, picking from Contacts, pasting a block of text, or
sharing text to the app from another app. Groups are saved on the phone only. The
app asks for **no permissions** and sends nothing itself: your texting and email
apps do the sending.

## What's possible

Apple keeps **iMessage** groups (blue bubbles) to Apple devices. Android phones
can't join an existing iMessage group, and no app can change that. Reverse-engineered
iMessage bridges have been blocked by Apple.

What does work is a group **text**. When someone on an iPhone starts a new group that
includes an Android number, Messages makes it an MMS/RCS group that everyone can use.
GroupBridge helps you set that up from either side.

| Member is in the iMessage group as… | What happens |
| --- | --- |
| Phone number | ✅ Can join the group text |
| Gmail / other email | ✉️ Reached by email. Some carriers deliver MMS to email, but replies don't come back to the group |
| iCloud email (@icloud.com, @me.com, @mac.com) | ⚠️ iMessage only. Ask for their phone number, or email them |

## Install

1. Open the repository's **Actions** tab, select the latest **Build APK** run, and
   download the **GroupBridge-apk** artifact (a zip containing `GroupBridge.apk`).
   If you push a tag like `v1.0`, the APK is also attached to a GitHub Release.
2. Copy the APK to your phone and open it. Allow "Install unknown apps" when asked.

## Build locally

You need the Android SDK and JDK 17.

```
./gradlew testDebugUnitTest assembleRelease
# APK: app/build/outputs/apk/release/app-release.apk
```

The release APK is signed with the debug key so it can be sideloaded without setup.
To publish on the Play Store, sign it with your own key.

## Code

- `Addresses.kt`: recognises phone numbers and emails, normalises numbers, extracts
  them from pasted text, and writes the message for iPhone members (unit-tested in
  `AddressesTest.kt`)
- `GroupStore.kt`: stores groups and settings on the device
- `Launch.kt`: opens the texting, email and share apps
- `MainActivity.kt`, `GroupActivity.kt`: the screens
