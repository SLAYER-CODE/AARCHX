# Lexis — Document Analysis & OCR Scanner

Fork de **Cornea** (`Herramientas/Cornea/`). Reutiliza la infraestructura de cámara y overlay de Iris pero reemplaza el pipeline de reconocimiento de dispositivos por análisis de documentos: OCR (Tesseract) + Clasificación + Extracción de campos.

## Arquitectura

```
Android CameraFrameSender ──[cam-*]──→ Lexis Engine
                                              │
                                     ┌────────▼────────┐
                                     │  Analysis Engine │
                                     │  ┌─────────────┐ │
                                     │  │     OCR      │ │  Tesseract: leer texto
                                     │  │  Document    │ │  Clasificar tipo + extraer campos
                                     │  │  Analyzer    │ │
                                     │  │ Diagnostics  │ │  Overlay panels
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

### Relación con Cornea

| Componente | Cornea | Lexis |
|------------|--------|-------|
| OCR | Tesseract (device labels) | Tesseract (document text) |
| Análisis | Device identification | Document classification |
| Extracción | Model, serial, firmware | Date, total, vendor, fields |
| VulnDB | SQLite CVEs | **No incluido** |
| Logo Detection | Template matching | **No incluido** |
| Output | Device info + vulns | Document info + fields |

## Módulos

| Módulo | Dependencia | Estado | Función |
|--------|-------------|--------|---------|
| `ocr` | Tesseract OCR (static) | **Compilado y funcional** | Leer texto de documentos |
| `document_analyzer` | Ninguna | **Compilado y funcional** | Clasificar documento + extraer campos |
| `diagnostics` | Ninguna | **Compilado y funcional** | FPS counter, overlay panels |

### Tipos de documento soportados

| Tipo | Keywords detectados | Campos extraídos |
|------|---------------------|------------------|
| Invoice | invoice, bill, factura, payment terms | total, tax, subtotal, invoice_number, vendor, customer, date |
| Receipt | receipt, recibo, thank you, cashier | total, tax, subtotal, date, vendor |
| Label | model, serial, s/n, fcc, made in | model, serial, date |
| ID Card | name, address, date of birth | address, phone, email |
| Contract | contract, agreement, terms | date, parties |
| Letter | dear, sincerely, regards | sender, recipient |
| Form | form, fill in, signature | fields based on labels |
| Ticket | ticket, boarding, seat | date, seat, gate |

## Build & Deploy

### Build rápido

```sh
cd Herramientas/Lexis && ./build.sh
# → build/lexis_stripped (5.8 MB, aarch64)
```

### Deploy al chroot Android

```sh
cd Herramientas/Lexis && ./build.sh --deploy
```

### Ejecutar en el chroot

```sh
adb shell
su
/data/local/aarchdroid/root/lexis --tesseract-data=/data/local/aarchdroid/root/lexis/tessdata
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
| `--db PATH` | Ruta a la DB (futuro) |
| `-v, --verbose` | Log detallado |
| `-h, --help` | Ayuda |

## Estructura de archivos

```
Lexis/
├── CMakeLists.txt
├── build.sh                          # Build + deploy (--deploy)
├── build_tesseract_static.sh         # Cross-compile Tesseract aarch64
├── AGENTS.md                         # Este archivo
├── .gitignore
├── include/lexis/
│   ├── types.h                       # DocumentInfo, TextBlock, Field
│   ├── module.h                      # Interfaz AnalysisModule
│   ├── engine.h                      # Pipeline de procesamiento
│   ├── config.h                      # CLI parser
│   ├── overlay_renderer.h            # Output dual (terminal + canvas)
│   └── modules/
│       ├── ocr.h                     # Tesseract OCR wrapper
│       ├── document_analyzer.h       # Document classification + field extraction
│       └── diagnostics.h             # Overlay panels
├── include/iris/
│   ├── camera_canvas.h               # Camera socket handling (from Iris)
│   └── camera.h                      # Camera types
├── src/
│   ├── engine.cpp                    # Main pipeline + camera socket
│   ├── config.cpp                    # CLI parser
│   ├── overlay_renderer.cpp          # Drawing + terminal output
│   └── modules/
│       ├── ocr.cpp                   # Tesseract: BGRA→gray→threshold→OCR
│       ├── document_analyzer.cpp     # Classification + field extraction
│       └── diagnostics.cpp           # FPS, device info overlay
├── examples/
│   └── lexis.cpp                     # Entry point principal
├── db/                               # Futuras bases de datos
└── build/                            # Output de build (gitignored)
    ├── lexis                         # Binario (12 MB)
    └── lexis_stripped                # Binario strippado (5.8 MB)
```

## Dependencias

| Dependencia | Tipo | Descripción |
|-------------|------|-------------|
| `libiris_static.a` | Static | Canvas, drawing, socket overlay |
| Tesseract OCR 4.x | Static | Cross-compiled en `/tmp/tesseract_build/install/` |
| Leptonica | Static | Image processing |
| libstdc++ | Dynamic | Runtime C++ standard library |
| libgomp | Dynamic | OpenMP (usado por Tesseract) |

## Campos extraídos por tipo

### Invoice/Receipt
- `date` — Fecha del documento
- `total` — Monto total
- `tax` — Impuesto
- `subtotal` — Subtotal
- `invoice_number` — Número de factura
- `vendor` — Vendedor/emisor
- `customer` — Cliente/destinatario
- `phone` — Teléfono
- `email` — Email

### Label
- `model` — Modelo del producto
- `serial` — Número de serie
- `date` — Fecha de fabricación

### ID Card
- `address` — Dirección
- `phone` — Teléfono
- `email` — Email

## Próximos pasos

1. **Multi-language OCR** — Soporte para español, francés, etc.
2. **DB de documentos** — SQLite para almacenar documentos escaneados
3. **Export** — JSON, CSV, Markdown
4. **Batch processing** — Procesar múltiples imágenes
5. **Template matching** — Formularios específicos (facturas electrónicas, etc.)
6. **Barcode/QR** — Lectura de códigos de barras y QR
7. **Image enhancement** — Auto-rotate, crop, deskew
