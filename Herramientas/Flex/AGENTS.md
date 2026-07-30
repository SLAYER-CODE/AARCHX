# Flex — Video Player for AArchDroid Canvas

Reproduce videos (MKV, MP4, AVI, etc.) en el overlay flotante de Canvas
via `canvas-display`. Usa `ffmpeg` como backend de decodificación.

## Dependencias

- `libiris_static.a` (canvas overlay) — actualizar desde Iris/
- `ffmpeg` instalado en chroot en `/usr/bin/ffmpeg`
- `audioplay_socket_android` (Android NDK binary) en `/data/local/tmp/`

## Build

```sh
cd Flex && aarch64-linux-gnu-g++ -O3 -static-libstdc++ \
  -I../Iris/include \
  src/main.cpp ../Iris/libiris_static.a \
  -o build/flex
aarch64-linux-gnu-strip --strip-unneeded build/flex -o build/flex_stripped
```

## Build audioplay (Android NDK)

```sh
NDK=$HOME/Android/Sdk/ndk/29.0.14206865
CC=$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android30-clang
$CC -O2 -fPIE -pie audioplay_socket.c -laaudio -llog -lm \
  -o audioplay_socket_android
```

## Deploy

```sh
# Flex (chroot side)
adb push build/flex_stripped /data/local/tmp/flex
su -c "dd if=/data/local/tmp/flex of=/data/local/aarchdroid/root/Flex/flex bs=1M && chmod 755 /root/Flex/flex"

# audioplay (Android side)
adb push audioplay_socket_android /data/local/tmp/
```

## Ejecutar

```sh
# 1. Start audio server (Android side)
su -c '/data/local/tmp/audioplay_socket_android -r 44100 -c 2 -s @flex_audio &'

# 2. Start Flex (inside chroot)
su -c 'aarchrun.sh /root/Flex/flex /root/Flex/video.mp4'
```

## Audio pipeline — AAudio / DSP bypass

El pipeline de audio original (ALSA PCM `tinyplay`) **no funciona** en este
kernel. Reemplazado por:

```
chroot (ffmpeg decode PCM s16le) ──pipe──→ audio_bridge (fork)
                                              │
                                              ▼ socket (abstract AF_UNIX)
Android (audioplay_socket) ─────────────────────► AAudio → speaker
```

- `audioplay_socket_android` (Android NDK) escucha en un socket abstracto
  `@flex_audio` y reproduce PCM via AAudio API
- Flex dentro del chroot fork un proceso puente que conecta al socket y
  envía el audio PCM decodificado por ffmpeg
- Socket abstracto: accesible desde cualquier proceso en el sistema
  (no afectado por chroot)
- `--no-audio` para deshabilitar audio
- Deshabilitar con `--no-audio` para repros sin sonido

## Uso

```
flex <video> [opciones]

Opciones:
  -d, --display SOCKET       Socket de display (default: canvas-display)
  -s, --size WxH             Forzar tamaño video (default: auto detect)
  -M, --max-size WxH         Tamaño máximo del video (default: 480x480)
  -D, --display-size WxH     Tamaño del canvas/ventana para centrar el video
  -f, --fps N                Framerate (default: 24)
  -b, --ffmpeg PATH          Ruta a ffmpeg (default: /usr/bin/ffmpeg)
  -a, --audio-socket PATH    Socket abstracto audio (default: @flex_audio)
  -N, --no-audio             Deshabilitar audio
  -l, --loop                 Repetir video
  -v, --verbose              Log detallado
```

Tap en el overlay o Ctrl+C para salir.
