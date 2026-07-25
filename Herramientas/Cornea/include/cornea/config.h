#pragma once

#include "cornea/engine.h"

#include <string>

namespace cornea {

// ── CLI Parser ───────────────────────────────────────────────────
// Parsea argumentos de línea de comandos y genera EngineConfig.
//
// Uso:
//   cornea [opciones]
//
// Opciones:
//   -c, --camera SOCKET     Socket de cámara (default: cam-0)
//   -s, --size WxH          Resolución (default: 640x480)
//   -r, --rotate GRADOS     Rotar frame (default: 0)
//   -m, --modules LIST      Módulos habilitados (comma-separated)
//   --overlay               Habilitar overlay Android (default: on)
//   --no-overlay            Deshabilitar overlay Android
//   --display SOCKET        Socket de display (default: canvas-display)
//   --tesseract-data PATH   Ruta a datos Tesseract
//   --template-dir PATH     Directorio de templates de logos
//   --db PATH               Ruta a la DB de vulnerabilidades
//   -v, --verbose           Log detallado
//   -h, --help              Mostrar ayuda
//
// Variables de entorno:
//   CANVAS_SOCKET           Override del socket de display
//   CORNEA_DB               Ruta a la DB de vulnerabilidades
class Config {
public:
    // Parse CLI args
    static Config parse(int argc, char** argv);
    
    // Print usage
    static void print_usage();
    
    // Convert to EngineConfig
    EngineConfig to_engine_config() const;
    
    // Fields
    std::string camera_socket = "cam-0";    // -c, --camera
    std::string modules;                     // -m, --modules
    std::string display_socket = "canvas-display";
    std::string tesseract_data;
    std::string template_dir;
    std::string db_path;
    int width = 640;                         // -s, --size
    int height = 480;
    int rotate = 0;                          // -r, --rotate
    bool overlay_enabled = true;
    bool verbose = false;
    bool help = false;
};

} // namespace cornea

