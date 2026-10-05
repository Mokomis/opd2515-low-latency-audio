#!/system/bin/sh
# Undo enable.sh: remove the added list and restart the audio service.
# Run as root:  su -c 'sh restore.sh'

DEST=/data/oplus/multimedia/Multimedia_Daemon_Online_List.xml

[ "$(id -u)" = "0" ] || { echo "error: run this as root" >&2; exit 1; }
if [ -f "$DEST" ]; then
    rm "$DEST" && echo "removed $DEST"
else
    echo "nothing to remove; $DEST does not exist"
fi
kill "$(pidof audioserver)" && echo "audio service restarted"
