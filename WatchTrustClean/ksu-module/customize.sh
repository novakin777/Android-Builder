#!/system/bin/sh

ui_print "*******************************"
ui_print "      WatchTrust 1.7-stable"
ui_print "*******************************"

DEST_DIR="$MODPATH/system/priv-app/WatchTrust107"
DEST_APK="$DEST_DIR/WatchTrust.apk"
OLD_DIR1="$MODPATH/system/priv-app/WatchTrust"
OLD_DIR2="$MODPATH/system/priv-app/WatchTrust105"
OLD_DIR3="$MODPATH/system/priv-app/WatchTrust106"
PAYLOAD="$MODPATH/watchtrust-phone.apk"
XML="$MODPATH/system/etc/permissions/privapp-permissions-watchtrust.xml"

ui_print "- Forcing fresh privileged APK install"

if [ ! -f "$PAYLOAD" ]; then
    abort "! Missing payload: $PAYLOAD"
fi

rm -rf "$OLD_DIR1"
rm -rf "$OLD_DIR2"
rm -rf "$OLD_DIR3"
rm -rf "$DEST_DIR"
mkdir -p "$DEST_DIR"

cp -f "$PAYLOAD" "$DEST_APK" || abort "! Failed to copy WatchTrust APK"
rm -f "$PAYLOAD"

touch "$DEST_APK" 2>/dev/null || true

set_perm_recursive "$DEST_DIR" 0 0 0755 0644
set_perm "$DEST_APK" 0 0 0644
set_perm "$XML" 0 0 0644

ui_print "- Installed: /system/priv-app/WatchTrust107/WatchTrust.apk"
ui_print "- Reboot required"
