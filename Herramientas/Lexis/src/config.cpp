#include "lexis/config.h"

#include <iostream>
#include <sstream>
#include <algorithm>

namespace lexis {

Config Config::parse(int argc, char** argv) {
    Config config;
    
    for (int i = 1; i < argc; i++) {
        std::string arg = argv[i];
        
        if (arg == "-h" || arg == "--help") {
            config.help = true;
        } else if (arg == "-v" || arg == "--verbose") {
            config.verbose = true;
        } else if (arg == "--overlay") {
            config.overlay = true;
        } else if (arg == "--no-overlay") {
            config.overlay = false;
        } else if (arg == "--terminal") {
            config.terminal = true;
        } else if (arg == "--no-terminal") {
            config.terminal = false;
        } else if (arg.find("--camera=") == 0) {
            config.camera = arg.substr(9);
        } else if (arg.find("--size=") == 0) {
            config.size = arg.substr(7);
        } else if (arg.find("--rotate=") == 0) {
            config.rotate = arg.substr(9);
        } else if (arg.find("--modules=") == 0) {
            config.modules = arg.substr(10);
        } else if (arg.find("--display=") == 0) {
            config.display = arg.substr(10);
        } else if (arg.find("--tesseract-data=") == 0) {
            config.tesseract_data = arg.substr(17);
        } else if (arg.find("--db=") == 0) {
            config.db_path = arg.substr(5);
        } else if (arg == "-c" && i + 1 < argc) {
            config.camera = argv[++i];
        } else if (arg == "-s" && i + 1 < argc) {
            config.size = argv[++i];
        } else if (arg == "-r" && i + 1 < argc) {
            config.rotate = argv[++i];
        } else if (arg == "-m" && i + 1 < argc) {
            config.modules = argv[++i];
        } else if (arg == "-d" && i + 1 < argc) {
            config.display = argv[++i];
        }
    }
    
    return config;
}

void Config::print_usage() {
    std::cout << R"(
Lexis — Document Analysis & OCR Scanner

Usage: lexis [OPTIONS]

Options:
  -c, --camera SOCKET      Camera socket (default: cam-0)
  -s, --size WxH           Resolution (default: 640x480)
  -r, --rotate DEGREES     Rotation (default: 0)
  -m, --modules LIST       Modules to enable (comma-separated)
  -d, --display SOCKET     Display socket (default: canvas-display)
  --tesseract-data PATH    Tesseract data directory
  --db PATH                Database path
  --overlay                Enable Android overlay
  --no-overlay             Disable Android overlay
  --terminal               Enable terminal output
  --no-terminal            Disable terminal output
  -v, --verbose            Verbose output
  -h, --help               Show this help

Modules:
  ocr                      Tesseract OCR text recognition
  document_analyzer        Document classification and field extraction
  diagnostics              Diagnostic overlay panels

Examples:
  lexis
  lexis --camera=cam-1 --size=1280x720
  lexis --modules=ocr,document_analyzer --no-overlay
  lexis --tesseract-data=/path/to/tessdata
)" << std::endl;
}

EngineConfig Config::to_engine_config() const {
    EngineConfig config;
    
    config.camera_socket = camera;
    config.verbose = verbose;
    config.overlay_enabled = overlay;
    config.terminal_enabled = terminal;
    config.display_socket = display;
    
    // Parse size
    size_t pos = size.find('x');
    if (pos != std::string::npos) {
        config.width = std::stoi(size.substr(0, pos));
        config.height = std::stoi(size.substr(pos + 1));
    }
    
    // Parse rotation
    config.rotate = std::stoi(rotate);
    
    // Parse modules
    std::istringstream ss(modules);
    std::string module;
    config.modules.clear();
    while (std::getline(ss, module, ',')) {
        // Trim whitespace
        size_t start = module.find_first_not_of(" \t");
        size_t end = module.find_last_not_of(" \t");
        if (start != std::string::npos) {
            config.modules.push_back(module.substr(start, end - start + 1));
        }
    }
    
    // Paths
    if (!tesseract_data.empty()) {
        config.tesseract_data = tesseract_data;
    }
    if (!db_path.empty()) {
        config.db_path = db_path;
    }
    
    return config;
}

} // namespace lexis
