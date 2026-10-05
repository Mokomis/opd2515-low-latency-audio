#!/system/bin/sh
# Allow chosen apps onto ColorOS's low-latency audio paths on the OPPO Pad Mini OPD2515.
# Run as root:  su -c 'sh enable.sh com.example.app [another.app ...]'
#
# Writes one file, /data/oplus/multimedia/Multimedia_Daemon_Online_List.xml: a copy of the
# built-in list with the given packages added to the two low-latency lists. The built-in
# file on the system partition is not modified. restore.sh removes the file again.
#
# OUT=/some/path sh enable.sh ...   writes the result there instead and restarts nothing.

STOCK=/system_ext/etc/Multimedia_Daemon_List.xml
DEST=/data/oplus/multimedia/Multimedia_Daemon_Online_List.xml
LISTS="aaudio-compatible-apps ull-compatible-apps"

fail() { echo "error: $1" >&2; exit 1; }

[ "$(id -u)" = "0" ] || fail "run this as root (su -c 'sh enable.sh ...')"
[ $# -ge 1 ] || fail "name at least one package, e.g. sh enable.sh com.example.app"
[ -f "$STOCK" ] || fail "$STOCK not found; this device is not supported"
for l in $LISTS; do
    [ "$(grep -c "</$l>" "$STOCK")" = "1" ] || fail "expected exactly one <$l> list in $STOCK"
done
for p in "$@"; do
    echo "$p" | grep -qE '^[A-Za-z0-9_]+(\.[A-Za-z0-9_]+)+$' || fail "'$p' is not a package name"
done

stock_ver=$(sed -n 's/.*<version>\([0-9]*\)<\/version>.*/\1/p' "$STOCK" | head -n 1)
[ -n "$stock_ver" ] || fail "no <version> found in $STOCK"
new_ver=$(date +%Y%m%d)
[ "$new_ver" -gt "$stock_ver" ] || new_ver=$((stock_ver + 1))

TARGET=${OUT:-$DEST}
TMP=$TARGET.tmp.$$
sed "s|<version>$stock_ver</version>|<version>$new_ver</version>|" "$STOCK" > "$TMP" || fail "cannot write $TMP"

for l in $LISTS; do
    for p in "$@"; do
        # Skip a package the list already names, so re-running is harmless.
        if sed -n "/<$l>/,/<\/$l>/p" "$TMP" | grep -q "<name>$p</name>"; then
            continue
        fi
        sed -i "s|^\( *\)</$l>|\1    <name>$p</name>\n\1    <attribute>0</attribute>\n\1</$l>|" "$TMP" \
            || fail "cannot edit $TMP"
    done
done

# The result must differ from stock only by the version line and the added entries.
added=$(( $(wc -l < "$TMP") - $(wc -l < "$STOCK") ))
for l in $LISTS; do
    for p in "$@"; do
        sed -n "/<$l>/,/<\/$l>/p" "$TMP" | grep -q "<name>$p</name>" \
            || { rm -f "$TMP"; fail "$p did not land in <$l>"; }
    done
done
[ "$added" -ge 0 ] && [ $((added % 2)) -eq 0 ] || { rm -f "$TMP"; fail "unexpected change in size"; }

mv "$TMP" "$TARGET" || fail "cannot move the result into place"
echo "wrote $TARGET (list version $new_ver, $added lines added)"

if [ -z "$OUT" ]; then
    chown system:system "$DEST"
    chmod 644 "$DEST"
    chcon u:object_r:oplus_multimedia_file:s0 "$DEST"
    # The audio service reads the list when it starts. Sound stops for a few seconds.
    kill "$(pidof audioserver)" && echo "audio service restarted"
fi
