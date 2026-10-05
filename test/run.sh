#!/usr/bin/env bash
# Chay toan bo bo kiem thu.
#
#   ./test/run.sh
#
# Bien moi truong duoc tu dong nhan:
#   JAVAC / JAVA   duong dan tien trinh (mac dinh: lay tu PATH, neu khong co
#                  thi dung /mnt/Nigga/Code/jdk17/bin)
#   CLIENT_FRAME   ten lop khung client, mac dinh MailClientFrame
#   SERVER_FRAME   ten lop khung server, mac dinh MailServerFrame
set -u

cd "$(dirname "$0")/.."
ROOT=$(pwd)

if [ -x "${JAVAC:-}" ]; then JAVAC="$JAVAC"
elif command -v javac >/dev/null 2>&1 && javac -version 2>&1 | grep -q ' 17'; then JAVAC=javac
else JAVAC=/mnt/Nigga/Code/jdk17/bin/javac
fi
if [ -x "${JAVA:-}" ]; then JAVA="$JAVA"
elif [ -x "${JAVAC%/javac}/java" ]; then JAVA="${JAVAC%/javac}/java"
else JAVA=java
fi

CLIENT_FRAME="${CLIENT_FRAME:-MailClientFrame}"
SERVER_FRAME="${SERVER_FRAME:-MailServerFrame}"

BUILD="$ROOT/build"
TESTBUILD="$ROOT/build-test"

echo "JDK  : $("$JAVAC" -version 2>&1)"
echo "Build sach..."
rm -rf "$BUILD" "$TESTBUILD"
mkdir -p "$BUILD" "$TESTBUILD"
"$JAVAC" -encoding UTF-8 -d "$BUILD" src/*.java || exit 1
"$JAVAC" -encoding UTF-8 -cp "$BUILD" -d "$TESTBUILD" test/*.java || exit 1
echo "  $(ls "$BUILD"/*.class | wc -l) class san pham, $(ls "$TESTBUILD"/*.class | wc -l) class kiem thu"
echo

CP="$BUILD:$TESTBUILD"

fail=0
OUT=$(mktemp)
run() {
    local label="$1"; shift
    echo "=== $label ==="
    if timeout 180 "$JAVA" -Dfile.encoding=UTF-8 -cp "$CP" "$@" >"$OUT" 2>&1; then
        tail -n "${TAIL:-4}" "$OUT" | sed 's/^/  /'
    else
        tail -n 25 "$OUT" | sed 's/^/  /'
        echo "  >>> FAIL: $label"
        fail=1
    fi
    echo
}

# Kiem thu tien trinh lau -> chi can dong ket qua, khong in 72 dong PASS
TAIL=3 run "E2E (protocol + realtime + GUI)" E2E

TAIL=2 run "InkCheck server  (1000x780)"   InkCheck "$SERVER_FRAME" 1000 780
TAIL=2 run "InkCheck client  (1180x740)"  InkCheck "$CLIENT_FRAME" 1180 740
rm -rf /tmp/ink_data
TAIL=3 run "InkCheck client sau dang nhap" InkCheck "$CLIENT_FRAME" 1180 740 login

for wh in 1000x640 1100x700 1180x740 1280x820; do
    w=${wh%x*}; h=${wh#*x}
    TAIL=1 run "GeoCheck $wh"          GeoCheck "$CLIENT_FRAME" "$w" "$h"
done
rm -rf /tmp/geo_data
for wh in 1000x640 1180x740 1280x820; do
    w=${wh%x*}; h=${wh#*x}
    TAIL=1 run "GeoCheck $wh (sau dn)" GeoCheck "$CLIENT_FRAME" "$w" "$h" login
done
TAIL=1 run "GeoCheck server (1000x780)" GeoCheck "$SERVER_FRAME" 1000 780

rm -rf /tmp/e2e_data /tmp/ink_data /tmp/geo_data
rm -f "$OUT"
echo "=============================="
[ $fail -eq 0 ] && echo "TAT CA KIEM THU PASS" || echo "CO KIEM THU FAIL"
exit $fail