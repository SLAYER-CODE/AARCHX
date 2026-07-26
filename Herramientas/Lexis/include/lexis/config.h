#pragma once

#include "lexis/engine.h"

namespace lexis {

// ── Configuration parser ────────────────────────────────────────
// Parsea argumentos CLI y genera EngineConfig.
struct Config {
    // CLI options
    bool help = false;
    bool verbose = false;
    bool overlay = true;
    bool terminal = true;
    
    std::string camera = "cam-0";
    std::string size = "640x480";
    std::string rotate = "0";
    std::string modules = "ocr,document_analyzer,diagnostics";
    std::string display = "canvas-display";
    std::string tesseract_data = "";
    std::string db_path = "";
    
    // Parse CLI args
    static Config parse(int argc, char** argv);
    
    // Print usage
    static void print_usage();
    
    // Convert to EngineConfig
    EngineConfig to_engine_config() const;
};

} // namespace lexis
