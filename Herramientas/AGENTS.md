# Herramientas — Herramientas C/C++ para Android chroot (aarch64)

## Arquitectura

`Herramientas/` contiene herramientas independientes que se compilan para aarch64
y se instalan en el chroot Android en `/data/local/aarchdroid/root/`.

| Herramienta | Lenguaje | Directorio | Binario(s) en chroot |
|---|---|---|---|
| **WifiOneshot** | C (multi-file + Makefile) | `WifiOneshot/` | `~/OneShot-C/oneshot` |
| **Iris** | C (multi-file + Makefile) | `WifiOneshot/` | `~/OneShot-C/oneshot` |
| **Iris** | C++ (CMake) | `Iris/` | `~/iris` |
| **Mandela** | C++ (CMake) | `Mandela/` | `~/mandela` |

Todas las herramientas se comunican con la app Android (AArchDroid) mediante
sockets AF_UNIX abstractos.

---

## librería compartida: `libiris_static.a`

Todas las herramientas enlazan contra `libiris_static.a` (fuente canónica en
`app/src/main/cpp/lib/`) para drawing + socket overlay.

| Símbolo | Header | Descripción |
|---|---|---|
| `iris::Canvas` | `iris/canvas.h` | Clase base: init(pixels, w, h), connect_overlay(socket), present() |
| `iris::draw::clear/fill_rect/draw_line/draw_circle/draw_text` | `iris/canvas.h` | Standalone drawing (operan sobre raw `uint32_t*`) |
| `iris::present_overlay(fd, pixels, w, h, scale)` | `iris/canvas.h` | Envía frame MNDL por socket sin usar clase Canvas |
| `iris::CameraCanvas` (Iris-specific) | `iris/camera_canvas.h` | Subclase: listen, accept_client, recv_frame, rotate |

### Build

```sh
cd app/src/main/cpp/lib && ./build.sh
# → build_aarch64/libiris_static.a
```

Iris y Mandela compilan la librería automáticamente desde sus propios `./build.sh`.

---

## WifiOneshot — Auditor WPS WiFi (OneShot)

### Source files

| Archivo | Rol |
|---|---|
| `main.c` | Entry point, CLI parsing |
| `db.c` / `db.o` | SQLite3 DB operations |
| `pin.c` / `pin.o` | PIN generation/processing |
| `wpas.c` / `wpas.o` | wpa_supplicant IPC |
| `wps.c` / `wps.o` | WPS protocol handling |
| `scan.c` / `scan.o` | WiFi scanning |
| `bruteforce.c` / `bruteforce.o` | Online bruteforce WPS PIN |
| `ui.c` / `ui.o` | Terminal UI |
| `oneshot.h` | Header compartido |
| `oneshotfinal.c` | Fuente monolítico (mismo código, 1 archivo) |
| `onedirect.c` | Variante directa |
| `oneshot_context.md` | Contexto/documentación |
| `vulnwsc.txt` | Lista de dispositivos vulnerables |
| `Wireless Security Database - Master.csv` | DB de redes WiFi |

### Build & Deploy

```sh
cd WifiOneshot
make  # → oneshot_aarch64

adb push oneshot_aarch64 /data/local/tmp/oneshot
su -c "dd if=/data/local/tmp/oneshot of=/data/local/aarchdroid/root/OneShot-C/oneshot bs=1M"

# Source reference (opcional)
adb push oneshotfinal.c /data/local/tmp/oneshotfinal.c
su -c "dd if=/data/local/tmp/oneshotfinal.c of=/data/local/aarchdroid/root/OneShot-C/oneshot.c bs=1M"
```

### Flags

| Flag | Efecto |
|---|---|
| `-K` | PixieDust (spawnea su propio wpa_supplicant) |
| `-B` | Online bruteforce WPS PIN |
| `--pbc` | WPS Push Button |
| `-l` | Loop infinito (re-scan tras ataque) |
| `-r` | Orden inverso de scan |
| `-F` | pixiewps --force |
| `-X` | Siempre imprime comando pixiewps |
| `-m`/`--mtk-fix` | echo 1 > /dev/wmtWifi (MediaTek) |
| `--status` | Historial de redes crackeadas/fallidas |
| `--stats` | Estadísticas resumen |
| `--cracked` / `--failed` / `--all` | Filtros de resultados |
| `--limit=<N>` | Limitar resultados [10] |
| `--essid=<texto>` / `--bssid=<mac>` / `--model=<texto>` / `--psk=<texto>` | Filtros de búsqueda |
| `--import-csv=<file>` | Importar Wireless Security Database CSV |

---

## librería compartida: `libiris_static.a`

Todas las herramientas usan `libiris_static.a` (fuente canónica en
`app/src/main/cpp/lib/`) para drawing + socket overlay, eliminando código duplicado.

### Qué expone

| Símbolo | Header | Descripción |
|---|---|---|
| `iris::Canvas` | `iris/canvas.h` | Clase base: init(pixels, w, h), connect_overlay(socket), present() |
| `iris::draw::clear/fill_rect/draw_line/draw_circle/draw_text` | `iris/canvas.h` | Standalone drawing (operan sobre raw `uint32_t*`) |
| `iris::present_overlay(fd, pixels, w, h, scale)` | `iris/canvas.h` | Envía frame MNDL por socket sin usar clase Canvas |
| `iris::CameraCanvas` (Iris-specific) | `iris/camera_canvas.h` | Subclase: listen, accept_client, recv_frame, rotate |

### Build

```sh
cd app/src/main/cpp/lib && ./build.sh
# → build_aarch64/libiris_static.a
```

Iris y Mandela lo hacen automáticamente desde sus propios `./build.sh`.

---

## Iris — Cámara + relay a overlay Android

Iris captura video desde una cámara V4L2 (o recibe frames por socket `cam-*`),
los procesa (rotación, overlays) y los envía al overlay Android por el socket
`canvas-display` usando `libiris_static.a`.

**Refactorizado:** usa `CameraCanvas` (subclase de `iris::Canvas`) para el modo
servidor. No tiene su propio `canvas.cpp`/`canvas.h`/`types.h`.

### Build & Deploy

```sh
cd Iris && ./build.sh
# → build/iris (libiris_static.a se enlaza estáticamente)

adb push build/iris /data/local/aarchdroid/root/iris
```

### Flags

| Flag | Efecto |
|---|---|
| `--listen, -l [socket]` | Recibir frames de Android (default: cam-0) |
| `--size, -s WxH` | Resolución (default: 640x480) |
| `--display, -d socket` | Socket de display (default: canvas-display) |
| `--rotate, -r GRADOS` | Rotar frame (0-359, modulo 360, ±) |
| `--btn` | Habilitar botón minimizar en overlay Android |
| `--verbose, -v` | Log detallado |

### Flujo

```
CameraFrameSender (Android) ──[12-byte cam-*]──→ iris (CameraCanvas::recv_frame) ──[iris::Canvas::present()/present_overlay()]──→ CanvasSocketServer (Android)
```

---

## Mandela — Visualizador de grafos WiFi + overlay

Mandela escanea redes WiFi, construye grafos de conectividad/localización y
los renderiza en múltiples backends: escritorio (Skia+GLFW), framebuffer Linux,
terminal (stdout) o **overlay Android** (socket `canvas-display`).

**Refactorizado:** usa `iris::draw::*` para primitivas de dibujo y
`iris::present_overlay()` para enviar frames al overlay, eliminando ~200 líneas
de código duplicado contra la implementación anterior.

### Build & Deploy

```sh
cd Mandela && ./build.sh
# → build/mandela_stripped

adb push build/mandela_stripped /data/local/tmp/mandela
su -c "dd if=/data/local/tmp/mandela of=/data/local/aarchdroid/root/mandela bs=1M"
```

### Flags principales

| Flag | Efecto |
|---|---|
| `--interface, -i <name>` | Interfaz WiFi (sin arg: listar) |
| `-v, --verbose` | Verboso |
| `--desktop` | Modo GUI (GLFW, requiere Skia) |
| `--terminal` | Modo terminal overlay (stdout/socket) |
| `-s, --size WxH` | Resolución canvas (default: 450x350) |
| `--scale VALUE` | Escala overlay Android (default: 1.0) |
| `--session [<ID>]` | Explorar sesión pasada |
| `--all [graphic|detail|list|reset]` | Unificar todas las sesiones |
| `--start-wpa` | Spawnear wpa_supplicant propio |
| `--scan-interval N` | Segundos entre escaneos (default: 5) |
| `--new` | Empezar fresco (sin restaurar) |
| `--zoom VALUE` | Factor de zoom al renderizar |
| `--no-loop` | Deshabilitar loop |
| `--db` | Reporte de base de datos (sin GUI) |

**Variables de entorno:**
- `CANVAS_SOCKET` — override del socket `canvas-display`
- `MANDELA_TERMINAL=1` — fuerza modo terminal

### Backends de Canvas

1. `init_framebuffer("/dev/fb0")` — framebuffer Linux directo
2. `init_stdout()` — frames raw a stdout con escape magic
3. `init_socket()` — conexión AF_UNIX a `canvas-display` (Android)
4. `init_desktop()` — ventana GLFW (Skia)
5. `init_offscreen()` — solo software, sin salida

---

## Protocolos de socket (AF_UNIX abstracto)

Compartidos entre herramientas y la app Android.

### cam-* (CameraFrameSender → Iris)

```
[frame_id:u32][width:u32][height:u32] + BGRA pixels   →  12-byte header
```

Sin magic. Socket dedicado por cámara (cam-0, cam-1, etc).

### canvas-display (Iris/Mandela → Android)

```
[magic:"MNDL":u32][frame_id:u32][width:u32][height:u32][scale_denom:u32] + BGRA pixels
```

- Header: 20 bytes
- `magic` = `0x4D4E444C`
- `scale_denom` = `overlay_scale * 100` (default 100 = 1.0)
- `CanvasSocketServer.kt` es multi-cliente

### cam-ctrl (Iris → Android, texto)

```
camera 0|1
size WxH
btn <x> <y>
btn hide
stop
```

---

## Workflow

1. **Siempre preguntar** antes de tocar código del núcleo AArchDroid (`app/src/main/java/...`)
2. Por cada herramienta: compilar → push al chroot
3. `adb push` va directo al chroot en `/data/local/aarchdroid/root/`
4. Para Mandela/WifiOneshot que usan `dd`: push a `/data/local/tmp/` primero, luego `dd`

## Known issues (deliberados, sin fix)

- `gets()`, `strcpy()`, `sprintf()` sin bounds checking (en oneshot)
- SQLite handle sin mutex (single-thread OK)
- Android LowMemoryKiller puede matar procesos; SIGKILL → wpa huérfano + socket stale (recuperar con `svc wifi disable && svc wifi enable`)
