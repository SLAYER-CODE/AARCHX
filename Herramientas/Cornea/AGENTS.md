# Cornea — Device Intelligence & Vulnerability Scanner

Derivación de **Iris** (`Herramientas/Iris/`). Reutiliza `libiris_static.a` para
el overlay Android pero reemplaza el pipeline de cámara por un motor de
reconocimiento visual de dispositivos: OCR (Tesseract) + YOLOv8 (ncnn) + Template Matching (logos).

## Arquitectura

```
Android CameraFrameSender ──[cam-*]──→ Cornea Engine
                                              │
                                     ┌────────▼────────┐
                                     │  Analysis Engine │
                                     │  ┌─────────────┐ │
                                     │  │  VisualDet.  │ │  YOLOv8n (ncnn): detección de dispositivos
                                     │  │     OCR      │ │  Tesseract: leer etiquetas
                                     │  │ LogoDetector │ │  Template match: identificar marca
                                     │  │   VulnDB     │ │  SQLite: vulnerabilidades
                                     │  │ Diagnostics  │ │  Overlay panels + bounding boxes
                                     │  └─────────────┘ │
                                     └────────┬────────┘
                                              │
                                     ┌────────▼────────┐
                                     │ Overlay Renderer │
                                     │  terminal stdout │
                                     │  canvas-display  │
                                     └────────┬────────┘
                                              │
                        present_overlay() ────→ CanvasSocketServer (Android)
```

### Relación con Iris

| Componente | Iris | Cornea |
|------------|------|--------|
| `libiris_static.a` | Canvas, drawing, socket overlay | **Reutiliza** |
| `CameraCanvas` | Recibe frames de cámara | **Usa** desde `libiris_static.a` |
| `camera.cpp` | Captura V4L2 directa | **No usa** (usa socket cam-* de Android) |
| Fuente de datos | Cámaras V4L2 / socket cam-* | Cámaras Android + OCR + YOLO + logos |
| Procesamiento | Rotación de frames | OCR + YOLO + Template Matching + DB lookup |

**Cornea usa `CameraCanvas` de `libiris_static.a`.** Escucha en el socket `cam-*`
mediante `CameraCanvas::listen()` y recibe frames con `CameraCanvas::recv_frame()`.

## Chroot y ubicación del binario

Cornea se ejecuta dentro del chroot AArchDroid en el dispositivo Android:

```
Dispositivo Android
└── /data/local/aarchdroid/          ← chroot base
    ├── aarchrun.sh                   ← script para ejecutar dentro del chroot
    └── root/cornea/                  ← directorio de Cornea en el chroot
        ├── cornea                    ← binario (9.3 MB, aarch64)
        ├── tessdata/
        │   └── eng.traineddata       ← Tesseract data
        ├── yolov8n.param             ← YOLO grafo ncnn
        ├── yolov8n.bin               ← YOLO pesos ncnn
        └── cornea.db                 ← SQLite vulnerabilidades
```

El flujo es: **cross-compilar en host → adb push → dd al chroot → ejecutar con aarchrun.sh**

```sh
# Ejecutar (ya dentro del dispositivo con adb shell)
su -c 'aarchrun.sh cornea --tesseract-data=/data/local/aarchdroid/root/cornea/tessdata'
```

## Módulos

| Módulo | Dependencia | Estado | Función |
|--------|-------------|--------|---------|
| `ocr` | Tesseract OCR (static) | **Funcional** | Leer etiquetas, modelo, serial, firmware |
| `visual_detector` | ncnn + YOLOv8n | **Funcional** | Detección visual de dispositivos (cajas azules) |
| `logo_detector` | OpenCV (ORB) | Stub | Identificar fabricante por logo |
| `vulndb` | SQLite3 | **Funcional** | CVEs conocidas + credenciales por defecto |
| `diagnostics` | Ninguna | **Funcional** | FPS counter, bounding boxes (azules YOLO + verdes OCR) |
| `device_classifier` | Ninguna | **Funcional** | Clasificación por heurísticas + mapeo de marcas |
| `discovery` | Ninguna | Stub | Descubrimiento de dispositivos en red |
| `fingerprint` | Ninguna | Stub | Fingerprinting de dispositivos |

## Build & Deploy

### Build rápido

```sh
cd Herramientas/Cornea && ./build.sh
# → build/cornea_stripped (9.3 MB, aarch64)
```

### Deploy al chroot Android

```sh
cd Herramientas/Cornea && ./build.sh --deploy
```

Esto ejecuta:
```sh
adb push /tmp/cornea_deploy/cornea /data/local/tmp/cornea
adb push /tmp/cornea_deploy/tessdata/eng.traineddata /data/local/tmp/eng.traineddata
adb shell "su -c 'dd if=/data/local/tmp/cornea of=/data/local/aarchdroid/root/cornea/cornea bs=1M'"
adb shell "su -c 'mkdir -p /data/local/aarchdroid/root/cornea/tessdata'"
adb shell "su -c 'dd if=/data/local/tmp/eng.traineddata of=/data/local/aarchdroid/root/cornea/tessdata/eng.traineddata bs=1M'"
# YOLO model:
adb push /tmp/cornea_deploy/yolov8n.param /data/local/tmp/
adb push /tmp/cornea_deploy/yolov8n.bin /data/local/tmp/
adb shell "su -c 'dd if=/data/local/tmp/yolov8n.param of=/data/local/aarchdroid/root/cornea/yolov8n.param bs=1M'"
adb shell "su -c 'dd if=/data/local/tmp/yolov8n.bin of=/data/local/aarchdroid/root/cornea/yolov8n.bin bs=1M'"
```

### Ejecutar en el chroot

```sh
su -c 'aarchrun.sh cornea --tesseract-data=/data/local/aarchdroid/root/cornea/tessdata'
```

### Build manual (sin build.sh)

```sh
# 1. Compilar libiris_static.a (si no existe)
cd app/src/main/cpp/lib && ./build.sh

# 2. Compilar Cornea
cd Herramientas/Cornea
mkdir -p build && cd build
cmake .. \
    -DCMAKE_C_COMPILER=aarch64-linux-gnu-gcc \
    -DCMAKE_CXX_COMPILER=aarch64-linux-gnu-g++ \
    -DCMAKE_BUILD_TYPE=Release \
    -DTESSERACT_STATIC_DIR=$(pwd)/../deps/tesseract_build/install \
    -DSHARED_IRIS_LIB_DIR=$(pwd)/../../app/src/main/cpp/lib/build_aarch64
make -j$(nproc)
```

## Flags

| Flag | Efecto |
|------|--------|
| `-c, --camera SOCKET` | Socket de cámara (default: cam-0) |
| `-s, --size WxH` | Resolución (default: 640x480) |
| `-r, --rotate GRADOS` | Rotar frame (default: 0) |
| `-m, --modules LIST` | Módulos habilitados (comma-separated) |
| `--overlay` / `--no-overlay` | Habilitar/deshabilitar overlay Android |
| `--display SOCKET` | Socket de display (default: canvas-display) |
| `--tesseract-data PATH` | Directorio de datos Tesseract |
| `--template-dir PATH` | Directorio de templates de logos |
| `--db PATH` | Ruta a la DB de vulnerabilidades |
| `--no-ansi` | Deshabilitar códigos de color ANSI |
| `--no-color` | Sinónimo de --no-ansi |
| `-v, --verbose` | Log detallado |
| `-h, --help` | Ayuda |

## Base de datos de vulnerabilidades

Ubicación en el chroot: `/data/local/aarchdroid/root/cornea/cornea.db`

### Tablas

| Tabla | Descripción |
|-------|-------------|
| `vendors` | Fabricantes (TP-Link, Hikvision, etc.) |
| `devices` | Modelos con patrones de identificación |
| `vulnerabilities` | CVEs conocidos |
| `default_credentials` | Credenciales por defecto por servicio |
| `logo_templates` | Rutas a templates de logos |
| `ocr_patterns` | Patrones regex para OCR |

### Datos iniciales (seed.sql)

- **23 vendors**: TP-Link, Hikvision, Dahua, Huawei, Xiaomi, Samsung, Apple, etc.
- **17 CVEs**: CVEs reales de dispositivos IoT
- **50+ credenciales**: admin:admin, ubnt:ubnt, root:root, etc.

## Estructura de archivos

```
Cornea/
├── CMakeLists.txt
├── build.sh                          # Build + deploy (--deploy)
├── build_tesseract_static.sh         # Cross-compile Tesseract aarch64
├── AGENTS.md                         # Este archivo
├── .gitignore                        # Excluye build/, ncnn-src, tesseract src
├── include/cornea/
│   ├── types.h                       # DeviceInfo, TextBlock, LogoMatch, VisualDetection
│   ├── module.h                      # Interfaz AnalysisModule
│   ├── engine.h                      # Pipeline de procesamiento (async futures)
│   ├── config.h                      # CLI parser
│   ├── overlay_renderer.h            # Output dual (terminal + canvas)
│   └── modules/
│       ├── ocr.h                     # Tesseract OCR wrapper
│       ├── visual_detector.h         # YOLOv8n ncnn inference
│       ├── logo_detector.h           # ORB template matching
│       ├── vulndb.h                  # SQLite vulnerability lookup
│       ├── device_classifier.h       # Heuristic device type detection
│       ├── diagnostics.h             # Overlay panels + bounding boxes
│       ├── discovery.h               # Network discovery (stub)
│       └── fingerprint.h             # Device fingerprinting (stub)
├── src/
│   ├── engine.cpp                    # Main pipeline + camera socket
│   ├── config.cpp                    # CLI parser
│   ├── overlay_renderer.cpp          # Drawing + terminal output
│   └── modules/
│       ├── ocr.cpp                   # Tesseract: BGRA→gray→threshold→OCR
│       ├── visual_detector.cpp       # YOLOv8 ncnn: preprocess + NMS + postprocess
│       ├── logo_detector.cpp         # Logo detection (stub)
│       ├── vulndb.cpp                # SQLite lookup
│       ├── device_classifier.cpp     # Heuristic classification
│       ├── diagnostics.cpp           # FPS, blue+green boxes, device info
│       ├── discovery.cpp             # Network discovery (stub)
│       └── fingerprint.cpp           # Fingerprinting (stub)
├── examples/
│   ├── cornea.cpp                    # Entry point principal
│   ├── ocr_demo.cpp                  # Demo OCR standalone
│   └── text_overlay_demo.cpp         # Demo overlay de texto
├── db/
│   ├── schema.sql                    # 6 tablas
│   └── seed.sql                      # Datos iniciales
├── deps/                             # Dependencias persistentes
│   ├── ncnn-install/                 # ncnn cross-compiled headers + libncnn.a
│   ├── ncnn-src/                     # ncnn source (gitignored, 76MB)
│   ├── tesseract_build/
│   │   ├── install/                  # Tesseract/Leptonica headers + libs
│   │   ├── tessdata/eng.traineddata  # Tesseract trained data
│   │   └── src/                      # Source (gitignored, 189MB)
│   ├── yolov8n.param                 # YOLOv8n ncnn model (exportado con pnnx)
│   ├── yolov8n.bin                   # YOLOv8n ncnn weights
│   └── yolov8n.onnx                  # YOLOv8n ONNX model (referencia)
└── build/                            # Output de build (gitignored)
    ├── cornea                        # Binario (12 MB)
    └── cornea_stripped               # Binario strippado (9.3 MB)
```

## Exportar modelo YOLOv8n a ncnn

**⚠️ CRÍTICO: NUNCA usar `onnx2ncnn` para convertir YOLOv8.** Genera una cadena DFL rota
con pesos desalineados. Resultado: detecciones falsas (pizza, etc.) o sin detecciones.

**Usar SIEMPRE ultralytics con export ncnn:**

```sh
cd Herramientas/Cornea/deps
python3 -c "
from ultralytics import YOLO
model = YOLO('yolov8n.pt')  # descarga automáticamente si no existe
model.export(format='ncnn', imgsz=640)
"
# Genera: yolov8n_ncnn_model/model.ncnn.param + model.ncnn.bin
cp yolov8n_ncnn_model/model.ncnn.param yolov8n.param
cp yolov8n_ncnn_model/model.ncnn.bin yolov8n.bin
```

Esto produce un modelo ncnn con:
- DFL decode + dist2bbox + stride multiplication **integrados en el grafo**
- Input name: `in0` (no `images`)
- Output name: `out0` (no `output0`)
- Output shape: `(w=8400, h=84)` — bbox decodificado [0:4] + scores [4:84]
- bbox en **píxeles** (espacio del modelo 640×640), no normalizado
- scores con **sigmoid ya aplicado**

### Input/Output names del modelo pnnx

```cpp
ex.input("in0", input);      // NO "images"
ex.extract("out0", output);  // NO "output0"
```

### Output format (ncnn Mat dims=2, w=8400, h=84)

| Rows | Contenido |
|------|-----------|
| 0 | cx (centro x, pixeles) |
| 1 | cy (centro y, pixeles) |
| 2 | w (ancho, pixeles) |
| 3 | h (alto, pixeles) |
| 4-83 | class scores (sigmoid, 0-1) |

**NO necesita DFL decode en C++.** El grafo ncnn ya lo hizo.

### Re-exportar si cambia la versión de ultralytics

Si cambias la versión de ultralytics o pytorch, re-exporta para asegurar compatibilidad.
El modelo NCNN generado es dependiente de la versión de pnnx que trae ultralytics.

## Rebuild de dependencias (si se dañan)

Si `deps/` se corrompe o necesitas rebuild desde cero, aquí está el proceso completo.

### libncnn.a — cross-compile desde source

ncnn source está en `deps/ncnn-src/` (gitignored). Si se pierde, reclonar:
```sh
cd deps
git clone https://github.com/Tencent/ncnn.git ncnn-src
cd ncnn-src
git checkout 20240724  # versión usada actualmente
```

Compilar para aarch64 Linux (NO Android NDK):
```sh
cd deps/ncnn-src
mkdir -p build && cd build
cmake .. \
    -DCMAKE_C_COMPILER=aarch64-linux-gnu-gcc \
    -DCMAKE_CXX_COMPILER=aarch64-linux-gnu-g++ \
    -DCMAKE_BUILD_TYPE=Release \
    -DNCNN_BUILD_EXAMPLES=OFF \
    -DNCNN_BUILD_TOOLS=OFF \
    -DNCNN_BUILD_BENCHMARK=OFF \
    -DNCNN_VULKAN=OFF \
    -DNCNN_OPENMP=ON \
    -DCMAKE_INSTALL_PREFIX=../../ncnn-install
make -j$(nproc)
make install
```

Esto produce:
- `deps/ncnn-install/lib/libncnn.a` (7.5 MB)
- `deps/ncnn-install/include/ncnn/` (headers)

Flags importantes:
| Flag | Razón |
|------|-------|
| `-DNCNN_VULKAN=OFF` | No hay GPU Vulkan en el chroot |
| `-DNCNN_OPENMP=ON` | Paralelismo en CPU big.LITTLE |
| `-DNCNN_BUILD_EXAMPLES=OFF` | No necesitamos demos |

### Modelo YOLOv8 (.param + .bin) — export desde .pt

**NUNCA usar `onnx2ncnn`.** El binario `deps/onnx2ncnn` existe solo como referencia
pero produce un grafo DFL roto (detecciones falsas). Usar SIEMPRE ultralytics.

Los archivos en `deps/`:
| Archivo | Tamaño | Propósito | Git |
|---------|--------|-----------|-----|
| `yolov8n.pt` | 6.3 MB | Pesos PyTorch originales (source of truth) | No (`.gitignore`) |
| `yolov8n.onnx` | 13 MB | ONNX intermedio (referencia) | Sí |
| `yolov8n_ncnn_model/` | — | Output de ultralytics export | No (`.gitignore`) |
| `yolov8n.param` | 17 KB | Grafo ncnn (copia de `model.ncnn.param`) | Sí (persistente) |
| `yolov8n.bin` | 13 MB | Pesos ncnn (copia de `model.ncnn.bin`) | Sí (persistente) |

Export comando completo:
```sh
cd deps
python3 -c "
from ultralytics import YOLO
model = YOLO('yolov8n.pt')  # descarga auto si no existe
model.export(format='ncnn', imgsz=640)
"
# ultralytics genera: yolov8n_ncnn_model/model.ncnn.param + model.ncnn.bin
# Copiar a deps/ con nombres fijos:
cp yolov8n_ncnn_model/model.ncnn.param yolov8n.param
cp yolov8n_ncnn_model/model.ncnn.bin yolov8n.bin
```

Output del modelo (ncnn Mat dims=2, w=8400, h=84):
| Rows | Contenido |
|------|-----------|
| 0 | cx (centro x, pixeles, normalizado [0,1]) |
| 1 | cy (centro y, pixeles, normalizado [0,1]) |
| 2 | w (ancho, pixeles, normalizado [0,1]) |
| 3 | h (alto, pixeles, normalizado [0,1]) |
| 4-83 | class scores (sigmoid, 0-1) |

⚠️ **CRÍTICO**: El output trae coordenadas normalizadas [0,1]. En C++ hay que
multiplicar por `input_size_` (640) **antes** del letterbox unpadding.
Ver "Historical fixes — lecciones aprendidas" fix #6.

### Resumen: qué se necesita para un rebuild completo

Si `deps/` se pierde por completo:
```
1. git clone ncnn → cross-compile libncnn.a  (~10 min)
2. build_tesseract_static.sh                  (~30 min, descarga todo)
3. python3 ultralytics export → yolov8n.param + .bin  (~2 min, descarga .pt)
4. ./build.sh                                  (~1 min)
```

## Dependencias

| Dependencia | Tipo | Ubicación | Descripción |
|-------------|------|-----------|-------------|
| `libiris_static.a` | Static | `app/src/main/cpp/lib/` | Canvas, CameraCanvas, drawing, socket overlay |
| ncnn | Static | `deps/ncnn-install/lib/libncnn.a` | YOLOv8 inference engine |
| Tesseract OCR 5.x | Static | `deps/tesseract_build/install/` | OCR engine |
| Leptonica | Static | `deps/tesseract_build/install/` | Imagen processing (viene con Tesseract) |
| libjpeg-turbo | Static | `deps/tesseract_build/install/` | JPEG codec |
| libpng | Static | `deps/tesseract_build/install/` | PNG codec |
| libtiff | Static | `deps/tesseract_build/install/` | TIFF codec |
| zlib | Static | `deps/tesseract_build/install/` | Compresión |
| SQLite3 | System | — | Base de datos de vulnerabilidades |
| libstdc++ | Dynamic | — | Runtime C++ standard library |
| libgomp | Dynamic | — | OpenMP (usado por Tesseract) |
| ultralytics + pnnx | Python | host | Exportar YOLOv8 a ncnn (host only) |

### Nota sobre deps/

Todas las dependencias están en `deps/` (directorio persistente, sobrevive reboot).
**NO se usa `/tmp/` para nada.** Todo descargado/compilado vive en esta carpeta.

Los source trees (`deps/ncnn-src/`, `deps/tesseract_build/src/`) están en `.gitignore`
pero se mantienen en disco para rebuilds. Solo se commitean headers + libs + modelos.

Regla: si hace falta descargar algo nuevo, hacerlo dentro de `deps/`, nunca en `/tmp/`.

## Protocolos de socket

Reutiliza los protocolos de Iris/Mandela:

### canvas-display (Cornea → Android)
```
[magic:"MNDL":u32][frame_id:u32][width:u32][height:u32][scale_denom:u32] + BGRA pixels
```
- Header: 20 bytes
- `magic` = `0x4D4E444C`
- CanvasSocketServer.kt es multi-cliente

### cam-* (Android → Cornea)
```
[frame_id:u32][width:u32][height:u32] + BGRA pixels
```
- Header: 12 bytes, sin magic
- Socket dedicado por cámara (cam-0, cam-1, etc)

## Variables de entorno

| Variable | Descripción |
|----------|-------------|
| `CANVAS_SOCKET` | Override del socket de display |
| `CORNEA_DB` | Ruta a la DB de vulnerabilidades |

## Estado actual

### Funcional
- **YOLOv8n** (ncnn): detección visual de dispositivos con bounding boxes azules
- **OCR** (Tesseract static): reconocimiento de texto con bounding boxes verdes
- **Async pipeline**: `std::future` para OCR + VisualDetector en paralelo
- **DiagnosticsModule**: Fusiona detecciones YOLO (azul) + OCR (verde) en overlay
- Conexión a socket cam-* de Android
- Recepción de frames BGRA
- OCR: preprocessamiento (grayscale → Otsu threshold → median filter)
- OCR: búsqueda de patrones (modelo, serial, firmware, vendor)
- Device Classifier: heurísticas + mapeo de marcas → tipo de dispositivo
- VulnDB: esquema SQLite + 23 vendors + 17 CVEs + 50+ credenciales
- Terminal stdout: imprime device info, vulns, credenciales
- Build + deploy automatizado (`./build.sh --deploy`)
- Todos los deps en `deps/` (persistente, no `/tmp/`)

### Pendiente (TODO)
- `rotate_frame()` — no implementado
- Overlay Android — canvas-display connection comentada
- Logo detection — requiere OpenCV ORB
- Network discovery — stub
- Device fingerprinting — stub

## Próximos pasos

1. **Overlay Android** — Conectar `present()` a canvas-display socket
2. **Logo templates** — Crear colección de logos de fabricantes
3. **VulnDB real** — Poblar con datos reales de dispositivos
4. **Testing** — Probar con dispositivos reales (routers, cámaras, teléfonos)
5. **Discovery** — Integrar nmap/arp scan del chroot
6. **Fingerprint** — HTTP header analysis, SNMP, mDNS

## Known issues

- Tesseract data files se deployan aparte del binario
- OCR en etiquetas pequeñas puede ser impreciso
- `draw_text()` en diagnostics usa draw_text de iris::draw
- Logo detection requiere OpenCV (~15MB) — no compilado actualmente
- Device classifier siempre retorna "Router" (bug conocido)
- YOLO confidence threshold bajo (0.25) para evitar falsos negativos

## Historical fixes — lecciones aprendidas

### 1. `free()` vs `delete[]` en Tesseract OCR (commit d5cdd65)

**Problema**: `Tesseract::GetUTF8Text()` retorna memoria allocada con `new[]`, no `malloc()`. Usar `free()` corrompe el heap gradualmente, crasheando al detectar muchas palabras.

**Lección**: Siempre usar `delete[]` para liberar `char*` retornado por APIs de Tesseract. Lo mismo aplica a `GetUTF8Text(RIL_WORD)` y `GetUTF8Text(RIL_TEXTLINE)` en iteradores.

**Fix**: `free(full_text)` → `delete[] full_text`, `free(word)` → `delete[] word`.

### 2. `pixMedianFilter` crash con imágenes 1-bit (commit 1620dcb)

**Problema**: `pixOtsuAdaptiveThreshold` puede retornar una imagen de 1 bpp. `pixMedianFilter` crashea si recibe una imagen que no es 8 bpp.

**Lección**: Siempre verificar profundidad después de thresholding y convertir a 8 bpp con `pixConvertTo8()` antes de `pixMedianFilter`. También añadir null-check después de `pixOtsuAdaptiveThreshold`.

**Patrón correcto**:
```cpp
Pix* thresh = nullptr;
pixOtsuAdaptiveThreshold(gray, 200, 200, 0, 0, 0.0, nullptr, &thresh);
if (!thresh) return src;
Pix* thresh8 = thresh;
if (pixGetDepth(thresh) != 8) thresh8 = pixConvertTo8(thresh, 0);
Pix* denoised = pixMedianFilter(thresh8, 3, 3);
```

### 3. Bounds clamping en draw_rect (commit 29858ca)

**Problema**: `draw_rect()` sin clamping causaba SIGSEGV cuando las bounding boxes se salían de la imagen (coords negativas o mayores a w/h).

**Lección**: Siempre clamp x, y con `std::max(0, x)` y x+rw, y+rh con `std::min(w, x+rw)` antes de acceder al pixel buffer. Esto aplica a cualquier operación de dibujo directo sobre `uint32_t* pixels`.

### 4. Reusar Iris drawing primitives (commit 29858ca)

**Problema**: `draw_text()` y `draw_rect_filled()` tenían stubs vacíos o implementaciones inline manuales que no funcionaban correctamente, causando overlay invisible o flickering.

**Lección**: `iris::draw::draw_text()` y `iris::draw::fill_rect()` ya existen en `libiris_static.a`. No reinventarlos. Simplemente delegar:
```cpp
void draw_text(...) { iris::draw::draw_text(pixels, w, h, x, y, text, color, size); }
void draw_rect_filled(...) { iris::draw::fill_rect(pixels, w, h, x, y, rw, rh, color); }
```

### 5. Orden de callbacks en pipeline (commit 29858ca)

**Problema**: `raw_frame_callback_` (envío al overlay) se llamaba antes de ejecutar los módulos de diagnóstico. El overlay Android recibía el frame sin bounding boxes ni anotaciones.

**Lección**: El orden correcto es: (1) ejecutar módulos (que dibujan sobre pixels), (2) `frame_callback_` (terminal), (3) `raw_frame_callback_` (overlay con frame ya anotado).

### 6. YOLOv8 ncnn — coordenadas normalizadas [0,1] (commit 2efd326)

**Problema**: El modelo YOLOv8 exportado con ultralytics+pnnx tiene DFL decode integrado en el grafo ncnn, pero las coordenadas del bbox salen normalizadas [0,1], no en píxeles. No multiplicarlas por `input_size_` (640) produce bounding boxes invisibles o mal ubicadas.

**Lección**: Multiplicar cx, cy, bw, bh por `input_size_` antes de aplicar letterbox unpadding:
```cpp
cx *= input_size_; cy *= input_size_; bw *= input_size_; bh *= input_size_;
```
Además: `inv_scale` debe ser `1/scale`, no `img_h/input_size_`.

### 7. YOLOv8 ncnn — acceso a output Mat (commit 2efd326)

**Problema**: Acceso directo al `float*` del output Mat sin usar `channel(0)` y sin verificar `extract()` puede causar crashes o datos corruptos.

**Lección**: Usar `output.channel(0)` para acceso seguro. Verificar retorno de `extract()` y validar `output.dims`, `output.w`, `output.h` antes de procesar.

### 8. YOLOv8 ncnn — `result.visual_detections` no poblado (commit 2efd326)

**Problema**: El vector `detections_` se computaba pero nunca se asignaba a `result.visual_detections`, por lo que ningún módulo downstream veía las detecciones.

**Lección**: Siempre verificar que los resultados del pipeline se propaguen a `FrameResult`:
```cpp
detections_ = detect(pixels, w, h);
result.visual_detections = detections_;
```

### 9. Pre-inicialización de módulos en Engine::start() (commit 1620dcb)

**Problema**: Los módulos no se inicializaban explícitamente. OCRModule necesitaba `set_data_path()` y `init()` antes de usar, causando fallos silenciosos.

**Lección**: En `Engine::start()`, iterar sobre todos los módulos, configurar paths específicos (OCR data path desde CLI), y llamar `module->init()` con verificación de error.

### 10. Reinit de Canvas en dimension change (commit 1620dcb)

**Problema**: Si la cámara Android cambia de resolución, el canvas overlay mantiene el tamaño anterior, distorsionando la imagen.

**Lección**: En `present()`, comparar w/h actuales con los del canvas. Si cambian, llamar `canvas_->init(w, h)` antes de `load_frame()`.

### 11. RIL_WORD vs RIL_TEXTLINE (commit 954800c)

**Problema**: Usar `RIL_WORD` fraccionaba el texto en palabras individuales, dificultando el matching de patrones regex (modelo, serial, firmware).

**Lección**: Usar `RIL_TEXTLINE` para obtener líneas completas. Trim whitespace con `find_first_not_of`/`find_last_not_of` en vez de filtrar ruido carácter por carácter.

### 12. Migración de deps a directorio persistente (commit c3cfff2)

**Problema**: Dependencias en `/tmp/` se pierden al reiniciar el host.

**Lección**: Usar `deps/` dentro del repositorio (gitignorando solo source trees pesados, no headers/libs). Así sobrevive reboots y rebuilds.

### 13. Deploy con `rm -rf` previo (commit 1620dcb)

**Problema**: `dd` sobre archivos existentes en el chroot podía dejar residuos si el binario nuevo era más pequeño.

**Lección**: Antes de deployar, ejecutar `rm -rf /data/local/aarchdroid/root/cornea && mkdir -p` para limpiar estado anterior.
