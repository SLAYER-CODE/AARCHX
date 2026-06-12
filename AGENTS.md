# AArchDroid

## Build
cd AArchDroid && ./gradlew assembleDebug
APK: AArchDroid/app/build/outputs/apk/debug/

## Estructura
- app/src/main/java/org/aarchdroid/dragonterminal/
  - floatui/ — FloatService, FloatWindowView (multi-ventana flotante)
  - ui/term/ — NeoTermActivity, NeoTermService (terminal principal)
  - ui/term/tab/ — NeoTabDecorator, TermTab (tab switcher)
  - backend/ — TerminalSession, ShellTermSession
- app/src/main/res/
  - layout/float_window.xml — overlay flotante
  - drawable/ — ic_float, ic_tab_icon, phone_close_tab_icon
- chrome-tabs/ — librería tab switcher (módulo fuente)

## Modificaciones recientes
- Multi-ventana float (FloatService soporta N ventanas + takeover)
- Botón ancla "↓" en float windows → transfiere sesión de vuelta al gestor
- Botón float "↗" en tab switcher (TextView ANSI verde)
- Close button "X" verde ANSI en tab switcher (phone_tab.xml + AbstractTabViewHolder)
- Tab icon verde (ic_tab_icon.xml vector)
- ACTION_ANCHOR en NeoTermActivity para recibir sesiones ancladas
- AArchDroidApp.transferredSession como puente entre servicios

## Convenciones
- FloatService: usa FloatWindowView + FloatSessionClient + FloatViewClient
- Sesiones transferidas via AArchDroidApp.transferredSession (nunca serializar)
- Callbacks de sesión se reemplazan al transferir (FloatService ↔ NeoTermActivity)

---

# NeovimEditor (módulo `neovim-editor`)

## Build
cd AArchDroid && ./gradlew :neovim-editor:assembleDebug
AAR: AArchDroid/neovim-editor/build/outputs/aar/

## Arquitectura msgpack-RPC
- `NeovimClient` — IO thread via `Dispatchers.IO`, lee socket, parsea msgpack, llama `onRedraw` en IO.
- `NeovimEditorActivity` — recibe `onRedraw` en IO, llama `processRedrawEvent` (modifica `buffer`), toma snapshot, lanza `updateBuffer` en Main.
- `NeovimEditorView` — `onDraw` con grid con buffer propio, `onKeyDown` + IME `InputConnection` → `onInput`.
- `NeovimBuffer` — grid cells + cursor + mode.

## Pipeline de redibujo
1. IO: `onRedraw(updates)` → `processRedrawEvent(c/u)` → `takeBufferSnapshot()` → `scope.launch(Main) { updateBuffer(snapshot); updateStatusLine() }`
2. Main: `updateBuffer(snapshot)` → copia cells/cursor/mode al view buffer → `postInvalidate()`
3. Main: `onDraw` → itera cells → dibuja fondo + texto + cursor

## Thread safety
- `NeovimBuffer` usa `synchronized(lock)` en resize/setCell/clear/scroll/copySnapshot.
- `copySnapshot()`: bajo lock, crea `NeovimBuffer`, **hace `snap.cells.clear()`**, copia rows desde `this.cells`.
- Activity: `takeBufferSnapshot()` delega a `buffer.copySnapshot()`.
- View: `updateBuffer()` valida `newBuffer.cells.size == gridHeight` antes de aplicar.

## Bugs pasados clave
| Bug | Root Cause | Fix |
|-----|-----------|-----|
| Init redraw events no llegan | `notify("nvim_ui_attach")` enviaba duplicado como notificación | Quitar `notify` |
| grid_resize formato equivocado | ext_linegrid puede enviar args posicionales o array | Handler dual |
| grid_line crash (else-if dentro de if) | Merge accidental al editar `sampleChars` | Separar branches correctamente |
| copySnapshot cells duplicadas | `NeovimBuffer()` init → `resize(80,24)` poblaba cells, loop append | `snap.cells.clear()` antes del loop |
| Cursor blink 100% CPU | blink en `onDraw` | `Handler` + `Runnable` |
| Defensive resize race | `buffer.resize(80,28)` en Main vs grid_resize en IO | `synchronized` + `copySnapshot` |
| Double key dispatch | `setOnKeyListener` + `onKeyDown` | Unificar en `onKeyDown` + `sendKeyEvent` |

## Problemas abiertos
- **Keyboard overlay**: Se cambió de `adjustResize` a `adjustNothing` + `OnApplyWindowInsetsListener`. El listener pone `paddingBottom = imeBottom` y recalcula grid con `visibleH = height - imeBottom`. ¯barra y status line deben quedar visibles.
- **Celdas sin color de highlight**: `foregroundId` se almacena pero no se resuelve a color real. Siempre usa blanco.
