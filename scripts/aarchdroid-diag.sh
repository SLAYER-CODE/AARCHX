#!/system/bin/sh
# Diagnostic script for nvim SIGWINCH testing inside AArchDroid chroot
# Run from a SECOND adb shell while nvim runs in the chroot
# Usage:  adb shell "su -c sh /data/local/tmp/aarchdroid-diag.sh <nvim_pid>"

NEO_PID=$1

if [ -z "$NEO_PID" ]; then
  echo "Usage: $0 <nvim_pid>"
  echo "Run 'ps -A | grep nvim' to find the PID"
  exit 1
fi

if [ ! -d "/proc/$NEO_PID" ]; then
  echo "ERROR: PID $NEO_PID does not exist"
  exit 1
fi

echo "=== AArchDroid nvim SIGWINCH Diag ==="
echo "PID:    $NEO_PID"
echo "PPID:   $(cat /proc/$NEO_PID/status | grep PPid | awk '{print $2}')"
echo "PGID:   $(cat /proc/$NEO_PID/stat | awk '{print $5}')"
echo "tpgid:  $(cat /proc/$NEO_PID/stat | awk '{print $8}')"
echo "TTY:    $(cat /proc/$NEO_PID/stat | awk '{print $7}')"
echo "---"

while true; do
  clear

  # Signal info
  SigCgt=$(cat /proc/$NEO_PID/status | grep SigCgt | awk '{print $2}')
  SigBlk=$(cat /proc/$NEO_PID/status | grep SigBlk | awk '{print $2}')
  SigIgn=$(cat /proc/$NEO_PID/status | grep SigIgn | awk '{print $2}')

  echo "=== $(date) ==="
  echo "SigCgt: $SigCgt  $( ((0x${SigCgt} >> 27 & 1)) && echo '(WINCH caught)' || echo 'WINCH MISSING!')"
  echo "SigBlk: $SigBlk  $(( 0x${SigBlk} != 0 && echo '(sig blocked!)' || echo '(none blocked)'))"
  echo "SigIgn: $SigIgn  $( ((0x${SigIgn} >> 27 & 1)) && echo '(WINCH ignored!)' || echo '')"

  # PTY winsize
  TTY_NR=$(cat /proc/$NEO_PID/stat | awk '{print $7}')
  MAJ=$(( (TTY_NR >> 8) & 0xFF ))
  MIN=$(( TTY_NR & 0xFF ))
  PTS="/dev/pts/$MIN"
  echo "PTY: $PTS (${MAJ}:${MIN})"

  # Try to read TIOCGWINSZ via a small C program or python
  # Fallback: check if /dev/pts/$MIN exists
  if [ -c "$PTS" ]; then
    echo "Device exists: YES"
  else
    echo "Device exists: NO (trying chroot path)"
    ls -la /data/local/aarchdroid$PTS 2>/dev/null && echo "Chroot PTY exists" || echo "Chroot PTY: not found"
  fi

  echo ""
  echo "Commands:"
  echo "  Kill SIGWINCH now:  'k'"
  echo "  Quit:               'q'"
  echo ""
  echo -n "> "

  read -t 5 key
  case "$key" in
    k)
      kill -WINCH $NEO_PID
      echo "Sent SIGWINCH to PID $NEO_PID"
      ;;
    q)
      echo "Exiting."
      exit 0
      ;;
  esac
done
