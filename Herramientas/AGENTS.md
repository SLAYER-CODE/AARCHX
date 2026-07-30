# Herramientas — AArchDroid Toolchain (aarch64, Android chroot)

Workspace central para gestionar la compatibilidad de todas las herramientas
con el **canvas overlay** (`CanvasSocketServer`, socket AF_UNIX `canvas-display`)
y la **API de Iris** (`libiris_static.a`).

Antes de compilar/pushear, analizar el contexto de la herramienta y verificar
cambios recientes en el API de Iris/AArchDroid.

Cada herramienta (carpeta) tiene su propio `AGENTS.md` con instrucciones
específicas de modificación, compilación y prueba. Este archivo es solo
para el contexto general del workspace.

## Tools

| Tool | Base | Canvas API | Build |
|---|---|---|---|
| **Iris/** | standalone | `libiris_static.a` (cámara + overlay + draw) | `aarch64-linux-gnu-g++ -static-libstdc++` |
| **Mandela/** | Iris | overlay display + MNDL protocol | CMake, linkea libiris |
| **Cornea/** | Iris fork | YOLOv8 + OCR + análisis objetos para hacking | CMake, linkea libiris |
| **Lexis/** | Cornea fork | OCR docs + clasificación documentos | CMake, linkea libiris |
| **Retina/** | Cornea fork | (nueva derivación) | CMake, linkea libiris |
| **Cati/** | standalone | visor de imágenes en overlay | `aarch64-linux-gnu-g++ -static-libstdc++` + stb_image |
| **Flex/** | standalone | reproductor de video en overlay | `aarch64-linux-gnu-g++ -static-libstdc++` + ffmpeg |
| **Orion/** | standalone | VPN/túnel APN, no usa overlay | `gcc` con `-lcurl -lssl -ljson-c` |
| **NeoShot/** | standalone | WPS attack, no usa overlay | `aarch64-linux-gnu-gcc -lsqlite3` |

## AArchDroid chroot

Los binarios se ejecutan dentro del chroot AArchDroid en el dispositivo:

```
Dispositivo: /data/local/aarchdroid/     ← chroot base
Chroot script: /data/local/aarchrun.sh   ← ejecuta el chroot
Tools:         /data/local/aarchdroid/root/<Tool>/
```

Cada terminal (Canvas nativa del dispositivo) monta automáticamente las
particiones necesarias (`/proc`, `/dev`, `/sys`, `/tmp`) al iniciar.
Las terminales en Canvas son flotantes con extrakeys integrados.

Flujo: **compilar (cross) → adb push → copiar al chroot → ejecutar dentro del chroot**

## Build & Deploy flow

```sh
# 1. Compilar Iris (libiris_static.a) si el API cambió
cd Iris && aarch64-linux-gnu-g++ -c -O3 src/*.cpp && ar rcs libiris_static.a *.o && rm *.o && cd ..

# 2. Compilar tool (ej: Mandela)
cd Mandela && aarch64-linux-gnu-g++ -O3 -I../Iris/include src/*.cpp -L../Iris -liris -o mandela_aarch64

# 3. Push al dispositivo
adb push mandela_aarch64 /data/local/tmp/
su -c "dd if=/data/local/tmp/mandela_aarch64 of=/data/local/aarchdroid/root/Mandela/mandela bs=1M"

# 4. Ejecutar dentro del chroot
adb shell /data/local/aarchrun.sh /root/Mandela/mandela [args...]

# 5. Source opcional
adb push src/*.cpp /data/local/tmp/
su -c "dd ..."
```

## Canvas overlay API (AArchDroid)

Socket abstracto `canvas-display` (AF_UNIX). Múltiples clientes simultáneos.

| Operación | Descripción |
|---|---|
| `init(w, h)` | Inicializar canvas en memoria |
| `connect_overlay("canvas-display")` | Conectar al socket overlay |
| `present()` | Enviar frame al overlay |
| `poll_commands()` | Leer reverse-channel (resize, touch) |
| `clear(color)` | Limpiar frame |
| `load_frame(pixels, size)` | Cargar pixels externos |
| `connected()` | Estado de conexión |

Protocolo: frames serializados como `WIDTHxHEIGHT+DATA` (raw ARGB32).
Reverse-channel: comandos texto (`resize WxH`, `touch x y`, etc).

## Contexto general AArchDroid

- **App principal** (Canvas Android): gestiona terminales flotantes con
  extrakeys, el overlay API (`canvas-display`), montaje automático de
  particiones del chroot, y lanzamiento de binarios.
- **Terminales**: múltiples instancias simultáneas, cada una ejecuta su
  propio chroot AArchDroid con `/proc`, `/dev`, `/sys` montados.
- **Overlay**: todas las herramientas que renderizan en pantalla usan
  `canvas-display` (socket AF_UNIX abstracto, multi-cliente).
- **Chroot**: las herramientas se almacenan en
  `/data/local/aarchdroid/root/<Tool>/` y se ejecutan via
  `/data/local/aarchrun.sh /root/<Tool>/<binary>`.

## Cambios recientes en Iris API

- `CameraCanvas` movido a `libiris_static.a` (archivo único `lib/camera_canvas.cpp`)
- `Canvas::present_overlay()` fix: separa header de data raw correctamente
- `connected()` fix: recalcula estado real, no solo flag
- Socket reconexión automática si `canvas-display` se cae

## Recordatorios

- Si el API de Iris cambia, recompilar **todas** las tools que linkean `libiris`
- `CameraCanvas` usa thread propio con `atomic<bool> running` + `SIGINT` handler
- `libiris_static.a` debe estar actualizada antes de compilar Mandela/Cornea/Lexis
- OneShot (`oneshotfinal.c`) es independiente, compilar con `aarch64-linux-gnu-gcc -lsqlite3`
- Orion no usa canvas overlay, solo tunel/APN
