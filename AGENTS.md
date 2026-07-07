# AArchDroid

See `../AGENTS.md` for root-level monorepo context (build, version matrix, known issues).

## NeovimEditor (módulo `neovim-editor`)

### Build
```sh
cd AArchDroid && ./gradlew :neovim-editor:assembleDebug
```
AAR: `neovim-editor/build/outputs/aar/`

### Arquitectura msgpack-RPC
- `NeovimClient` — IO thread via `Dispatchers.IO`, lee socket, parsea msgpack, llama `onRedraw` en IO.
- `NeovimEditorActivity` — recibe `onRedraw` en IO, llama `processRedrawEvent` (modifica `buffer`), toma snapshot, lanza `updateBuffer` en Main.
- `NeovimEditorView` — `onDraw` con grid con buffer propio, `onKeyDown` + IME `InputConnection` → `onInput`.
- `NeovimBuffer` — grid cells + cursor + mode.

### Pipeline de redibujo
1. IO: `onRedraw(updates)` → `processRedrawEvent(c/u)` → `takeBufferSnapshot()` → `scope.launch(Main) { updateBuffer(snapshot); updateStatusLine() }`
2. Main: `updateBuffer(snapshot)` → copia cells/cursor/mode al view buffer → `postInvalidate()`
3. Main: `onDraw` → itera cells → dibuja fondo + texto + cursor

### Thread safety
- `NeovimBuffer` usa `synchronized(lock)` en resize/setCell/clear/scroll/copySnapshot.
- `copySnapshot()`: bajo lock, crea `NeovimBuffer`, **hace `snap.cells.clear()`**, copia rows desde `this.cells`.
- Activity: `takeBufferSnapshot()` delega a `buffer.copySnapshot()`.
- View: `updateBuffer()` valida `newBuffer.cells.size == gridHeight` antes de aplicar.

### Bugs pasados clave
| Bug | Root Cause | Fix |
|-----|-----------|-----|
| Init redraw events no llegan | `notify("nvim_ui_attach")` enviaba duplicado como notificación | Quitar `notify` |
| grid_resize formato equivocado | ext_linegrid puede enviar args posicionales o array | Handler dual |
| grid_line crash (else-if dentro de if) | Merge accidental al editar `sampleChars` | Separar branches correctamente |
| copySnapshot cells duplicadas | `NeovimBuffer()` init → `resize(80,24)` poblaba cells, loop append | `snap.cells.clear()` antes del loop |
| Cursor blink 100% CPU | blink en `onDraw` | `Handler` + `Runnable` |
| Defensive resize race | `buffer.resize(80,28)` en Main vs grid_resize en IO | `synchronized` + `copySnapshot` |
| Double key dispatch | `setOnKeyListener` + `onKeyDown` | Unificar en `onKeyDown` + `sendKeyEvent` |
| grid_line trailing clear rompía Enter+wrap | while loop en cada segmento limpiaba celdas parciales | dirty-rows-on-flush: trackear maxCol por fila, limpiar en `flush` |
| Blank rows tras grid_resize (zoom-out/IME close) | grid_line solo cubre filas de ventanas; filas fuera de ventanas quedan defaultCell (espacio) en vez de `~` | En `onRedraw`, detectar `grid_resize` en el batch; tras procesar todos los eventos, llenar con `~` filas no presentes en `rowLineMaxCol` |
| Contenido viejo persiste al cargar archivo nuevo | `grid_clear` era no-op; contenido de archivo anterior se mantenía en filas sin `grid_line` | `grid_clear` rellena todas las celdas con `~`; `grid_line` sobrescribe las filas de ventanas |

### Problemas abiertos
- **Keyboard overlay**: Se cambió de `adjustResize` a `adjustNothing` + `OnApplyWindowInsetsListener`. El listener pone `paddingBottom = imeBottom` y recalcula grid con `visibleH = height - imeBottom`. ¯barra y status line deben quedar visibles.
- **Celdas sin color de highlight**: `foregroundId` se almacena pero no se resuelve a color real. Siempre usa blanco.
