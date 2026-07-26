#!/bin/bash
# Push Iris (and optional Mandela) to Android chroot via ADB

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# Binaries to push
IRIS="${SCRIPT_DIR}/iris"
IRIS_LIB="${SCRIPT_DIR}/libiris_static.a"
MANDELLA="${SCRIPT_DIR}/mandela"

echo "[Deploy] Checking files..."
for f in "$IRIS" "$IRIS_LIB"; do
    [ -f "$f" ] && echo "  OK: $(basename $f) ($(ls -lh $f | awk '{print $5}'))" || { echo "  MISSING: $f"; exit 1; }
done
if [ -f "$MANDELLA" ]; then
    echo "  OK: mandela ($(ls -lh $MANDELLA | awk '{print $5}'))"
else
    echo "  SKIP: mandela not present"
fi

echo ""
echo "[Deploy] Pushing to device..."
adb push "$IRIS" /data/local/tmp/iris
if [ -f "$MANDELLA" ]; then
    adb push "$MANDELLA" /data/local/tmp/mandela 2>/dev/null || true
fi

echo ""
echo "[Deploy] Copying to chroot..."
# Iris binary
adb shell su -c 'dd if=/data/local/tmp/iris of=/data/local/aarchdroid/root/iris bs=1M 2>/dev/null'
adb shell su -c 'chmod 755 /data/local/aarchdroid/root/iris'

# Optional: mandela
adb shell su -c 'if [ -f /data/local/tmp/mandela ]; then dd if=/data/local/tmp/mandela of=/data/local/aarchdroid/root/mandela bs=1M 2>/dev/null; chmod 755 /data/local/aarchdroid/root/mandela; fi' 2>/dev/null || true

echo ""
echo "[Deploy] Done!"
echo ""
echo "=== Iris ==="
echo "# Single camera (modo por defecto):"
echo "  /data/local/aarchdroid/root/iris"
echo ""
echo "# Dual camera:"
echo "  /data/local/aarchdroid/root/iris --dual"
echo ""
echo "# Especificar dispositivo, resolución, fps:"
echo "  /data/local/aarchdroid/root/iris --camera=/dev/video2 --size=1280x720 --fps=15"
echo ""
echo "# Socket name (default: CANVAS_SOCKET env o 'canvas-display'):"
echo "  /data/local/aarchdroid/root/iris --socket=mi-socket"
echo "  CANVAS_SOCKET=mi-socket /data/local/aarchdroid/root/iris"
echo ""
echo "# Verbose:"
echo "  /data/local/aarchdroid/root/iris --verbose"
echo ""
echo "Nota: MANDELA_TERMINAL=1 y CANVAS_SOCKET=canvas-display"
echo "se exportan automáticamente por ChrootManager al abrir terminal."
