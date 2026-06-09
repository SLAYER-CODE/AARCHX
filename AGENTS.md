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
