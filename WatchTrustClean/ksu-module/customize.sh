#!/system/bin/sh

ui_print "*******************************"
ui_print "      WatchTrust 1.5-ipc"
ui_print "*******************************"

DEST_DIR="$MODPATH/system/priv-app/WatchTrust105"
DEST_APK="$DEST_DIR/WatchTrust.apk"
OLD_DIR="$MODPATH/system/priv-app/WatchTrust"
PAYLOAD="$MODPATH/watchtrust-phone.apk"
XML="$MODPATH/system/etc/permissions/privapp-permissions-watchtrust.xml"

ui_print "- Forcing fresh privileged APK install"

if [ ! -f "$PAYLOAD" ]; then
    abort "! Missing payload: $PAYLOAD"
fi

rm -rf "$OLD_DIR"
rm -rf "$DEST_DIR"
mkdir -p "$DEST_DIR"

cp -f "$PAYLOAD" "$DEST_APK" || abort "! Failed to copy WatchTrust APK"
rm -f "$PAYLOAD"

# Make PackageManager see a freshly written systemless file.
touch "$DEST_APK" 2>/dev/null || true

set_perm_recursive "$DEST_DIR" 0 0 0755 0644
set_perm "$DEST_APK" 0 0 0644
set_perm "$XML" 0 0 0644

ui_print "- Installed: /system/priv-app/WatchTrust105/WatchTrust.apk"
ui_print "- Reboot required"
