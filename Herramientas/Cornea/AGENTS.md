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
| `CameraCanvas` | Recibe frames de cámara | **No usa** (usa socket raw) |
| `camera.cpp` | Captura V4L2 directa | **No usa** (usa socket cam-* de Android) |
| Fuente de datos | Cámaras V4L2 / socket cam-* | Cámaras Android + OCR + YOLO + logos |
| Procesamiento | Rotación de frames | OCR + YOLO + Template Matching + DB lookup |

**Cornea NO usa CameraCanvas de Iris.** Implementa su propio `connect_camera()` /
`recv_frame()` directamente sobre el socket AF_UNIX `cam-*` (mismo protocolo de 12 bytes).

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

## Dependencias

| Dependencia | Tipo | Ubicación | Descripción |
|-------------|------|-----------|-------------|
| `libiris_static.a` | Static | `app/src/main/cpp/lib/` | Canvas, drawing, socket overlay |
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
Los source trees (`deps/ncnn-src/`, `deps/tesseract_build/src/`) están en `.gitignore`
pero se mantienen en disco para rebuilds. Solo se commitean headers + libs + modelos.

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
