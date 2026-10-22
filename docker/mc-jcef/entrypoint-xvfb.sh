#!/bin/bash
set -euo pipefail

# Native Mesa/JogAmp SIGSEGVs must not fill /data with multi-GB core dumps.
ulimit -c 0 || true

export DISPLAY="${DISPLAY:-:99}"
export LIBGL_ALWAYS_SOFTWARE="${LIBGL_ALWAYS_SOFTWARE:-1}"
export vblank_mode="${vblank_mode:-0}"
export GALLIUM_DRIVER="${GALLIUM_DRIVER:-llvmpipe}"
# Stub broken dri2QueryDrawable under software GLX (see glx_query_shim.c).
export LD_PRELOAD="/usr/local/lib/libglx_query_shim.so${LD_PRELOAD:+:$LD_PRELOAD}"

start_xdummy() {
  if pgrep -x Xorg >/dev/null 2>&1; then
    return 0
  fi
  rm -f /tmp/.X99-lock /tmp/.X11-unix/X99 /tmp/xorg-dummy.log 2>/dev/null || true
  # Dummy driver gives a real GLX path; Xvfb's GLX leaves glXQueryDrawable NULL.
  Xorg :99 -noreset +extension GLX +extension RANDR +extension RENDER \
    -logfile /tmp/xorg-dummy.log \
    -config /etc/X11/xorg-dummy.conf \
    -nolisten tcp \
    >/tmp/xorg-dummy.out 2>&1 &
  sleep 2
  if ! pgrep -x Xorg >/dev/null 2>&1; then
    echo "[mc-jcef] Xdummy failed, falling back to Xvfb" >&2
    cat /tmp/xorg-dummy.out 2>/dev/null || true
    cat /tmp/xorg-dummy.log 2>/dev/null || true
    return 1
  fi
  return 0
}

start_xvfb() {
  if pgrep -x Xvfb >/dev/null 2>&1; then
    return 0
  fi
  rm -f /tmp/.X99-lock /tmp/.X11-unix/X99 2>/dev/null || true
  Xvfb :99 -screen 0 1920x1080x24 -ac +extension GLX +render -noreset >/tmp/xvfb.log 2>&1 &
  sleep 1
}

if ! start_xdummy; then
  start_xvfb
fi

exec /image/scripts/start "$@"
