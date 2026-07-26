# Overlay API — Conexión AArchDroid ↔ Herramientas nativas

Las herramientas C/C++ (compiladas para aarch64) se ejecutan dentro del chroot
Android en `/data/local/aarchdroid/root/` y se comunican con la app AArchDroid
exclusivamente mediante **sockets AF_UNIX abstractos**.

No hay IPC compartido, binder, ni ningún otro mecanismo. Toda la comunicación
es bidireccional por socket.

---

## Socket abstracto AF_UNIX

Los nombres de socket son **abstractos** (sun_path empieza con `\0`).
No crean archivos en el filesystem. El namespace es global al sistema Android.

```
struct sockaddr_un addr;
addr.sun_family = AF_UNIX;
addr.sun_path[0] = '\0';
memcpy(addr.sun_path + 1, "canvas-display", 14);
```

El chroot hereda el namespace de sockets del host Android — no necesita
configuración adicional. La app Android (Java) usa `LocalSocket` con
`LocalSocketAddress.Namespace.ABSTRACT`.

La app expone la variable de entorno `CANVAS_SOCKET` a los procesos del chroot
(vía `ChrootManager`) para que las herramientas sepan a qué socket conectarse.

---

## Protocolo: canvas-display (20 bytes + BGRA)

**Dirección:** Herramienta nativa → AArchDroid  
**Socket:** `canvas-display` (por defecto, configurable vía `CANVAS_SOCKET`)  
**Server:** `CanvasSocketServer.kt` (multi-cliente)

```
 byte  Offset   Size  Campo         Tipo     Descripción
 ───── ─────── ───── ───────────── ──────── ───────────────────────
  0     0       4     magic         uint32   0x4D4E444C = "MNDL"
  4     4       4     frame_id      uint32   Número de frame incremental
  8     8       4     width         uint32   Ancho en píxeles
 12    12       4     height        uint32   Alto en píxeles
 16    16       4     scale_denom   uint32   overlay_scale × 100 (100 = 1.0)
 ───── ─────── ───── ───────────── ──────── ───────────────────────
 20    20       W×H×4  BGRA pixels  uint8[]  BGRA 8-8-8-8, little-endian
```

Reglas:
- **Header fijo de 20 bytes**, little-endian.
- `scale_denom` = `overlay_scale * 100`. Valor default 100 (= 1.0).
- `CanvasSocketServer` valida magic en cada frame. Si no coincide, cierra
  la conexión tras 3 intentos inválidos consecutivos.
- El servidor es multi-cliente: cada conexión tiene su propio overlay
  independiente.
- El socket es `SOCK_STREAM`. El envío debe manejar writes parciales.

---

## Protocolo: cam-* (12 bytes + BGRA)

**Dirección:** AArchDroid (`CameraFrameSender`) → Herramienta nativa  
**Socket:** `cam-0`, `cam-1`, etc. (un socket por cámara)  
**Server:** Herramienta nativa (iris en modo --listen)

```
 byte  Offset   Size  Campo     Tipo     Descripción
 ───── ─────── ───── ───────── ──────── ───────────────────
  0     0       4     frame_id  uint32   Número de frame
  4     4       4     width     uint32   Ancho en píxeles
  8     8       4     height    uint32   Alto en píxeles
 ───── ─────── ───── ───────── ──────── ───────────────────
 12    12       W×H×4  BGRA    uint8[]  BGRA 8-8-8-8, little-endian
```

Reglas:
- **Sin magic** — socket dedicado por cámara, no necesita identificación.
- Header de 12 bytes, little-endian.
- El frame raw viene en BGRA (no YUV ni RGB).
- `CameraFrameSender` convierte YUV_420_888 → BGRA antes de enviar.

---

## Protocolo: cam-ctrl (texto ASCII)

**Dirección:** Herramienta nativa → AArchDroid  
**Socket:** `cam-ctrl` (fijo)  
**Server:** `CameraControlServer.kt`

Comandos (una línea cada uno, terminados en `\n`):

| Comando | Efecto |
|---|---|
| `camera 0` / `camera 1` | Cambiar cámara activa |
| `size WxH` | Cambiar resolución |
| `btn <x> <y>` | Mostrar botón minimizar en coordenadas (px) |
| `btn hide` | Ocultar botón minimizar |
| `stop` | Detener streaming |

---

## Flujo de datos completo

```
┌─────────────────────────────────────────────────────────────┐
│                    AArchDroid (Android)                     │
│                                                             │
│  CameraFrameSender   ──cam-*──→  [tool] recv_frame()       │
│       (Kotlin)       (12 bytes)  (C/C++ dentro del chroot)  │
│                                                             │
│  CanvasSocketServer  ←─canvas-display──  [tool] present()   │
│       (Kotlin)        (20 bytes MNDL)   (C/C++ en chroot)   │
│                                                             │
│  CameraControlServer ←─cam-ctrl──  [tool] control commands  │
│       (Kotlin)        (texto)      (C/C++ en chroot)        │
│                                                             │
│       │                                                     │
│       ▼                                                     │
│  CanvasOverlayView (SurfaceView overlay)                    │
│       │                                                     │
│       ▼                                                     │
│  LCD / SurfaceFlinger                                       │
└─────────────────────────────────────────────────────────────┘
```

El chroot (`/data/local/aarchdroid/root/`) hereda el namespace de sockets
abstractos del host. No hay forwarding, bind mounts ni proxys — las
herramientas nativas se conectan directamente a los sockets que la app
Android escucha.

---

## Reglas del overlay

1. **BGRA 8-8-8-8 little-endian** — los píxeles se interpretan como
   `uint32_t` donde el byte 0 = Blue, byte 1 = Green, byte 2 = Red,
   byte 3 = Alpha.
2. **Sin compresión** — los frames se envían raw por SOCK_STREAM.
3. **Escala** — `scale_denom` permite que el overlay Android escale el
   frame al renderizar. Valor 100 = escala 1.0 (píxel por píxel).
4. **Frame ID** — incremental. El servidor puede detectar frames perdidos
   si hay saltos.

---

## Entorno de ejecución

- Las herramientas se compilan cruzadas desde host x86_64 → aarch64
  usando `aarch64-linux-gnu-gcc`/`g++`.
- Los binarios se alojan en `/data/local/aarchdroid/root/`.
- La app exporta `CANVAS_SOCKET=canvas-display` a través de
  `ChrootManager` para que las herramientas sepan a qué socket
  conectarse sin hardcodear.
- El chroot se inicia con `chroot /data/local/aarchdroid/root/`.

---

## Crear una nueva herramienta

La forma más rápida de crear una herramienta que se comunique con el
overlay de AArchDroid es enlazar contra **`libiris_static.a`**
canónica en `app/src/main/cpp/lib/`). La librería expone:

- `iris::draw::*` — primitivas de dibujo sobre `uint32_t*` raw (clear,
  fill_rect, draw_line, draw_circle, draw_text)
- `iris::present_overlay()` — envía frame MNDL por socket sin gestionar
  headers ni writes parciales
- `iris::Canvas` — clase base con init/connect/present (opcional)

### Ejemplo mínimo

```cpp
#include "iris/canvas.h"

int main() {
    const int W = 320, H = 240;

    // Opción A — usar la clase Canvas (todo incluido)
    iris::Canvas canvas;
    if (!canvas.connect_overlay("canvas-display"))
        return 1;
    canvas.init(W, H);

    canvas.clear(0xFF1a1a2e);                      // fondo
    canvas.fill_rect(10, 10, 100, 50, 0xFFe94560); // rectángulo
    canvas.draw_text(20, 30, "Hola AArchDroid", 0xFFEEEEEE);

    canvas.present();  // envía frame MNDL al overlay Android

    // Opción B — standalone (buffer propio + draw::* + present_overlay)
    // std::vector<uint32_t> buf(W * H);
    // iris::draw::clear(buf.data(), W, H, 0xFF1a1a2e);
    // int fd = ...;  // socket AF_UNIX a canvas-display
    // iris::present_overlay(fd, buf.data(), W, H, 1.0f);

    return 0;
}
```

### CMakeLists.txt

```cmake
cmake_minimum_required(VERSION 3.16)
project(mi_herramienta LANGUAGES CXX)
set(CMAKE_CXX_STANDARD 17)

# Ruta a la shared lib (ajustar si es necesario)
set(SHARED_IRIS_LIB_DIR "${CMAKE_SOURCE_DIR}/../../app/src/main/cpp/lib/build_aarch64")
set(SHARED_IRIS_INCLUDE_DIR "${SHARED_IRIS_LIB_DIR}/../include")

add_executable(mi_herramienta main.cpp)
target_include_directories(mi_herramienta PRIVATE ${SHARED_IRIS_INCLUDE_DIR})
target_link_libraries(mi_herramienta PRIVATE "${SHARED_IRIS_LIB_DIR}/libiris_static.a")
```

### build.sh

```sh
#!/bin/bash
set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BUILD_DIR="${SCRIPT_DIR}/build"

# Compilar libiris_static.a primero
AARCHDROID_LIB_DIR="$(cd "$SCRIPT_DIR/../../app/src/main/cpp/lib" && pwd)"
SHARED_BUILD_DIR="${AARCHDROID_LIB_DIR}/build_aarch64"
mkdir -p "$SHARED_BUILD_DIR"
cd "$SHARED_BUILD_DIR"
cmake "$AARCHDROID_LIB_DIR" \
    -DCMAKE_C_COMPILER=aarch64-linux-gnu-gcc \
    -DCMAKE_CXX_COMPILER=aarch64-linux-gnu-g++ \
    -DCMAKE_BUILD_TYPE=Release \
    -DIRIS_USE_SKIA=OFF
make -j$(nproc)

# Compilar herramienta
cmake -S "$SCRIPT_DIR" -B "$BUILD_DIR" \
    -DCMAKE_C_COMPILER=aarch64-linux-gnu-gcc \
    -DCMAKE_CXX_COMPILER=aarch64-linux-gnu-g++ \
    -DCMAKE_BUILD_TYPE=Release \
    -DSHARED_IRIS_LIB_DIR="$SHARED_BUILD_DIR"
cmake --build "$BUILD_DIR" -j$(nproc)
```

### Deploy

```sh
adb push build/mi_herramienta /data/local/aarchdroid/root/mi_herramienta
adb push "$SHARED_BUILD_DIR/libiris_static.a" /data/local/aarchdroid/root/lib/
```

La app expone `CANVAS_SOCKET` dentro del chroot; la herramienta puede
usar el socket por defecto `canvas-display` o leer la variable de entorno
para entornos de desarrollo (override local).
