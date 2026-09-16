#!/system/bin/sh

ui_print "- WatchTrust Clean"
ui_print "- Installing privileged phone TrustAgent"

APK="$MODPATH/system/priv-app/WatchTrust/WatchTrust.apk"
XML="$MODPATH/system/etc/permissions/privapp-permissions-watchtrust.xml"

if [ ! -f "$APK" ]; then
    abort "! Missing $APK"
fi

set_perm_recursive "$MODPATH/system/priv-app/WatchTrust" 0 0 0755 0644
set_perm "$XML" 0 0 0644
