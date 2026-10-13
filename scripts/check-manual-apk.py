"""Check the built manual APK does not declare automatic collection capabilities."""
import subprocess
import sys

aapt, apk = sys.argv[1:]
permissions = subprocess.check_output([aapt, "dump", "permissions", apk], text=True)
manifest = subprocess.check_output([aapt, "dump", "xmltree", apk, "AndroidManifest.xml"], text=True)
for forbidden in (
    "android.permission.RECEIVE_SMS",
    "android.permission.READ_SMS",
    "android.permission.SEND_SMS",
    "android.permission.READ_CALL_LOG",
    "android.permission.READ_CONTACTS",
    "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE",
    "android.permission.BIND_ACCESSIBILITY_SERVICE",
    "SmsReceiver",
    "BankNotificationListener",
):
    if forbidden in permissions or forbidden in manifest:
        raise SystemExit("Unexpected manual APK capability: " + forbidden)
print("Manual APK verified: no SMS, notification-listener, accessibility, contact, or call-log access.")
print(permissions)
