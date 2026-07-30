# Refinamiento — Temporal Fusion + Persistencia

Estado actual de Cornea: **tracking básico implementado**. Tracking visual vía Kalman + IoU
(Tracker class) integrado en el engine loop. Frame skipping/throttling configurable vía flags.
OCR sigue siendo stateless (no temporal fusion aún) y no hay memoria persistente de dispositivos.

Estado de cada sección:
- ✅ 1. Visual Tracker — **implementado** (Kalman + IoU, predict/update, EMA confianza)
- 🟡 2. OCR con scope espacial — **pendiente**
- 🔴 3. Device Memory — **pendiente**
- 🟡 4. Pipeline Architecture — **parcial** (tracker integrado, lo demás pendiente)
- 🟡 5. Frame Rate y Métricas — **parcial** (tracker count en diagnostics, FPS pendiente)
- 🔴 6. Prioridad de Implementación — **items 1 completado**
- 🟡 7. Flags Nuevos — **parcial** (tracker/max-fps/process-every listos, ref-db y cache-db pendientes)

## Objetivo

Construir un pipeline que:

1. Acumule confianza viendo el mismo objeto en múltiples frames (tracking visual + Kalman)
2. Fusione lecturas OCR parciales o ruidosas en texto estable (votación temporal)
3. Persista dispositivos reconocidos para re-identificación instantánea
4. Reconstruya información incompleta combinando frames

---

## ✅ 1. Visual Tracker (Kalman + IoU) — IMPLEMENTADO

Clase `Tracker` en `include/cornea/tracker.h`, impl en `src/tracker.cpp`.
Corre **inline** en el loop principal (no async) para tener temporalidad frame-a-frame real.

### Algoritmo

Kalman filter con estado `[cx, cy, w, h, vx, vy]`:
- **Predict** (cada frame, aunque YOLO no haya producido detecciones):
  actualiza posición según velocidad. El bounding box se mueve suavemente incluso si YOLO
  pierde detección por 2-5 frames (blur de movimiento, oclusión parcial).
- **Update** (cuando async produce detecciones YOLO):
  corrige posición con la medición real.
- **Match** entre tracks y detecciones: IoU con threshold ~0.3.
- **Creación**: detección sin match → nuevo track.
- **Muerte**: track sin update por >5 frames se elimina.
- **Filtrado**: tracks con age < 3 frames no se reportan (elimina falsos positivos).

| Concepto | Implementación |
|----------|---------------|
| Tracking | **Kalman filter** (default). Flag `--tracker=iou` para IoU simple |
| Confianza | EMA: `conf = conf × 0.7 + new_conf × 0.3` |
| Matching | IoU entre detecciones y tracks (threshold 0.3) |
| Predicción | Kalman predict cada frame aunque no haya update |
| Timeout | 5 frames sin update → track muere |
| Startup | 3 frames antes de reportar un track |

Output: `result.tracked_objects` con `track_id`, clase estabilizada, confianza fusionada,
bounding box suavizado por Kalman.

### Estructuras

```
struct TrackedObject {
    int track_id;
    int class_id;
    string class_name;
    float confidence;           // EMA
    float x, y, w, h;          // Posición del Kalman (suavizada)
    int age;                    // Frames desde creación
    int stale_count;            // Frames sin match YOLO
    float kalman_cx, kalman_cy; // Estado interno del Kalman
    float kalman_vx, kalman_vy;
};

struct TrackerState {
    vector<TrackedObject> tracks;
    int next_id = 1;
    float iou_threshold = 0.3f;
    int max_stale = 5;
    int min_age = 3;
    bool use_kalman = true;     // false = IoU simple sin predicción
};
```

---

## 2. OCR con scope espacial (coupled OCR)

Actualmente OCR escanea todo el frame. Con `--coupled-ocr`, OCR solo se ejecuta
dentro de los bounding boxes de YOLO (o de los tracks del TrackerModule).

### Beneficios

- Área de OCR se reduce drásticamente → más rápido
- Textos encontrados se asocian automáticamente al dispositivo detectado
- Elimina OCR en fondos sin interés (carteles, personas, etc.)
- Cada texto sabe a qué `track_id` pertenece

### Pipeline con coupled OCR

```
Frame → YOLO detecta [phone, person, router]
         │
         └─→ Crops de cada bounding box
              │
              └─→ OCR sobre cada crop
                    │
                    └─→ TextBlock asociado a track_id
```

Esto hace que YOLO y OCR sean seriales (OCR espera a YOLO), pero como el área es
menor, el tiempo total es similar o mejor.

### Flag

| Flag | Efecto |
|------|--------|
| (default) | OCR escanea frame completo |
| `--coupled-ocr` | OCR solo dentro de bounding boxes YOLO/tracks |

### OCR Temporal Fusion

Buffer circular por track_id (~5-10 frames).

- Cada texto vota por su contenido, ponderado por confianza
- Si un texto aparece ≥3 de 5 frames → se promociona a "confirmado"
- Textos que aparecen 1 sola vez con confianza < 30% → ruido, se filtran
- Reconstrucción: textos de mismo track que aparecen en regiones adyacentes
  en frames consecutivos pueden concatenarse
  (ej: frame 1 "TL-WR8", frame 2 "841N" → "TL-WR841N")

### Algoritmo

```
por cada track_id:
  recuperar buffer temporal para ese track
  por cada TextBlock del crop actual:
    buscar match por contenido textual
    si match:
      agregar voto (texto, confianza, timestamp)
    si no match:
      crear nueva entrada en buffer

  promover textos con >=3 votos en últimos 5 frames a "estables"
  limpiar entradas sin update >10 frames
```

Output: `result.device.text_blocks` solo incluye textos estables y confirmados.

---

## 3. Device Memory (dos bases separadas)

Dos bases de datos SQLite con roles distintos:

### cornea_ref.db (referencia)

Contiene las marcas, modelos, patrones OCR y vendors conocidos.
ES la base que actualmente está en `db/schema.sql` + `db/seed.sql`.

**Read-only en runtime.** Se carga completa en RAM al startup como hash maps para
búsqueda O(1).

```
struct RefDB {
    unordered_map<string, Vendor> vendors;          // vendor name → vendor info
    unordered_multimap<string, DeviceModel> models; // vendor → modelos conocidos
    vector<regex> ocr_patterns;                     // patrones para extraer modelo/serial/fw
};
```

### cornea_cache.db (datos recolectados)

Guarda dispositivos detectados y sightings.

```sql
CREATE TABLE known_devices (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    vendor TEXT,
    model TEXT,
    serial TEXT,
    firmware TEXT,
    device_type TEXT,
    confidence REAL DEFAULT 0.0,
    first_seen INTEGER,
    last_seen INTEGER,
    visit_count INTEGER DEFAULT 1,
    last_track_id INTEGER,
    UNIQUE(vendor, model, serial)
);

CREATE TABLE device_sightings (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    device_id INTEGER REFERENCES known_devices(id),
    timestamp INTEGER,
    confidence REAL,
    text_found TEXT,
    detections_found TEXT
);
```

**Write policy**: solo se escribe cuando el dispositivo cambia significativamente
(nuevo vendor/model, confianza sube >20%). No se escribe por frame.

### Flujo

| Evento | Acción |
|--------|--------|
| OCR produce vendor+model+serial completo | UPSERT en `known_devices` |
| OCR produce solo vendor | Buscar en RefDB modelos conocidos de ese vendor |
| OCR produce solo serial con track_id | Buscar serial en cache DB |
| Startup | Cargar RefDB a RAM. Cargar known_devices a RAM |
| Confianza OCR 20-40% | Cruzar contra known_devices en RAM, si hay match → boost |
| Frame sin detecciones | No escribir nada |

---

## 🟡 4. Pipeline Architecture — PARCIAL (tracker predict/update implementado)

```
Loop principal (engine.cpp), por cada frame:

  1. Recibir frame de cámara (socket cam-*)
  
  2. TrackerModule::predict()  ← corre SIEMPRE, aunque no haya detecciones nuevas
     └─ Kalman predict para todos los tracks activos
     └─ bounding boxes se mueven aunque YOLO no haya respondido
  
  3. Si future anterior terminó:
     
     3a. TrackerModule::update(detecciones YOLO)
         └─ match + Kalman update + creación/muerte de tracks
      
     3b. OCR Fusion (sobre crops de tracks si --coupled-ocr, o full frame si no)
         └─ buffer temporal → textos estables
      
     3c. DeviceMemory::update(device_info)
         └─ upsert en known_devices si hay datos nuevos
      
     3d. DeviceClassifier recibe tracks + OCR fusionado
         └─ clasificación más estable
  
  4. Diagnostics dibuja bounding boxes sobre TRACKS (no detecciones raw)
     └─ boxes más estables, siguen al objeto aunque YOLO parpadee
  
  5. print_analysis() imprime resultados estables
  
  6. Present overlay a Android
  
  7. Launch nuevo async (si no hay uno pendiente)
     └─ OCR (solo si no --coupled-ocr) → VulnDB → Classifier → VisualDetector
```

### Frame skipping / throttling

Si el procesamiento async es más lento que la cámara:

| Mecanismo | Cómo |
|-----------|------|
| Skip automático | Si `ocr_future_.valid()`, dropear este frame |
| `--max-fps N` | Calcular intervalo mínimo entre procesamientos |
| `--process-every N` | Procesar 1 de cada N frames |

El tracker predict corre en TODOS los frames aunque se dropee el procesamiento.
Esto asegura tracking continuo sin importar el throttling.

### Inline vs async

| Componente | Corre | Razón |
|------------|-------|-------|
| Tracker predict | Cada frame, inline | Mantener continuidad temporal |
| Tracker update | Cuando async termina, inline | Fusionar detecciones nuevas |
| OCR Fusion | Cuando async termina, inline | Solo sobre datos nuevos |
| OCR (Tesseract) | Async | Pesado, no bloquear el loop |
| YOLO (ncnn) | Async | Pesado, no bloquear el loop |
| DeviceMemory write | Cuando hay cambios, inline | Rápido, solo cuando hay novedades |

---

## 5. Frame Rate y Métricas

Agregar mediciones al diagnostics:

- **Camera FPS**: frames recibidos del socket por segundo
- **Process FPS**: frames procesados por el pipeline async por segundo
- **Tracker tracks**: cantidad de tracks activos
- **OCR area**: % del frame escaneado por OCR

---

## 6. Prioridad de Implementación

1. ✅ **TrackerModule con Kalman** — ~200 líneas. Tracking suave, bounding boxes estables
2. 🔴 **`--coupled-ocr`** — ~100 líneas. OCR solo en crops YOLO
3. 🔴 **OCR temporal buffer por track** — ~150 líneas. Elimina ruido, estabiliza texto
4. 🔴 **Device Memory (cornea_cache.db)** — ~250 líneas. Persistencia
5. 🔴 **RefDB en RAM** — ~100 líneas. Carga reference data al startup
6. 🔴 **Reconstrucción de texto parcial** — ~100 líneas. Concatenar lecturas parciales

---

## 🟡 7. Flags Nuevos — PARCIAL

| Flag | Default | Efecto | Estado |
|------|---------|--------|--------|
| `--tracker=iou` | kalman | Algoritmo de tracking: kalman o iou | ✅ Implementado |
| `--coupled-ocr` | off | OCR solo dentro de bounding boxes YOLO | 🔴 Pendiente |
| `--max-fps N` | 0 (sin límite) | Máximo de frames procesados por segundo | ✅ Implementado |
| `--process-every N` | 1 | Procesar 1 de cada N frames | ✅ Implementado |
| `--ref-db PATH` | ./cornea_ref.db | Ruta a base de referencia | 🔴 Pendiente |
| `--cache-db PATH` | ./cornea_cache.db | Ruta a base de caché | 🔴 Pendiente |

---

## 8. Preguntas Abiertas

1. Coupled OCR: serializar YOLO+OCR puede aumentar latencia. Preferís priorizar velocidad
   (paralelo como ahora) o precisión (OCR solo en crops)?
2. Kalman filter: el estado incluye solo posición o también velocidad? Velocidad da
   mejor predicción pero necesita más tuning.
3. Reconstrucción de texto parcial: concatenar textualmente o solo boostear confianza
   de la mejor coincidencia en RefDB?
4. Device memory write: cada cuánto? Por cambio de estado o por tiempo (cada N segundos)?
5. El OCI (memory) del buffer temporal de OCR — cuántos tracks/máximo de textos queremos
   mantener?
