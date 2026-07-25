#include "cornea/config.h"

#include <iostream>
#include <cstring>
#include <algorithm>
#include <sstream>

namespace cornea {

Config Config::parse(int argc, char** argv) {
    Config config;
    
    for (int i = 1; i < argc; i++) {
        std::string arg = argv[i];
        
        // Help
        if (arg == "-h" || arg == "--help") {
            config.help = true;
            return config;
        }
        
        // Verbose
        if (arg == "-v" || arg == "--verbose") {
            config.verbose = true;
            continue;
        }
        
        // Camera socket
        if (arg.rfind("--camera=", 0) == 0 || arg.rfind("-c=", 0) == 0) {
            size_t eq = arg.find('=');
            if (eq != std::string::npos) {
                config.camera_socket = arg.substr(eq + 1);
            }
        } else if (arg == "--camera" || arg == "-c") {
            if (i + 1 < argc) {
                config.camera_socket = argv[++i];
            }
        }
        
        // Size
        else if (arg.rfind("--size=", 0) == 0 || arg.rfind("-s=", 0) == 0) {
            size_t eq = arg.find('=');
            if (eq != std::string::npos) {
                std::string size = arg.substr(eq + 1);
                size_t x = size.find('x');
                if (x != std::string::npos) {
                    config.width = std::stoi(size.substr(0, x));
                    config.height = std::stoi(size.substr(x + 1));
                }
            }
        } else if (arg == "--size" || arg == "-s") {
            if (i + 1 < argc) {
                std::string size = argv[++i];
                size_t x = size.find('x');
                if (x != std::string::npos) {
                    config.width = std::stoi(size.substr(0, x));
                    config.height = std::stoi(size.substr(x + 1));
                }
            }
        }
        
        // Rotate
        else if (arg.rfind("--rotate=", 0) == 0 || arg.rfind("-r=", 0) == 0) {
            size_t eq = arg.find('=');
            if (eq != std::string::npos) {
                config.rotate = std::stoi(arg.substr(eq + 1));
            }
        } else if (arg == "--rotate" || arg == "-r") {
            if (i + 1 < argc) {
                config.rotate = std::stoi(argv[++i]);
            }
        }
        
        // Modules
        else if (arg.rfind("--modules=", 0) == 0 || arg.rfind("-m=", 0) == 0) {
            size_t eq = arg.find('=');
            if (eq != std::string::npos) {
                config.modules = arg.substr(eq + 1);
            }
        } else if (arg == "--modules" || arg == "-m") {
            if (i + 1 < argc) {
                config.modules = argv[++i];
            }
        }
        
        // Overlay
        else if (arg == "--overlay") {
            config.overlay_enabled = true;
        } else if (arg == "--no-overlay") {
            config.overlay_enabled = false;
        }
        
        // Display socket
        else if (arg.rfind("--display=", 0) == 0) {
            size_t eq = arg.find('=');
            if (eq != std::string::npos) {
                config.display_socket = arg.substr(eq + 1);
            }
        } else if (arg == "--display") {
            if (i + 1 < argc) {
                config.display_socket = argv[++i];
            }
        }
        
        // Tesseract data
        else if (arg.rfind("--tesseract-data=", 0) == 0) {
            size_t eq = arg.find('=');
            if (eq != std::string::npos) {
                config.tesseract_data = arg.substr(eq + 1);
            }
        } else if (arg == "--tesseract-data") {
            if (i + 1 < argc) {
                config.tesseract_data = argv[++i];
            }
        }
        
        // Template directory
        else if (arg.rfind("--template-dir=", 0) == 0) {
            size_t eq = arg.find('=');
            if (eq != std::string::npos) {
                config.template_dir = arg.substr(eq + 1);
            }
        } else if (arg == "--template-dir") {
            if (i + 1 < argc) {
                config.template_dir = argv[++i];
            }
        }
        
        // Database
        else if (arg.rfind("--db=", 0) == 0) {
            size_t eq = arg.find('=');
            if (eq != std::string::npos) {
                config.db_path = arg.substr(eq + 1);
            }
        } else if (arg == "--db") {
            if (i + 1 < argc) {
                config.db_path = argv[++i];
            }
        }
        
        // Unknown option
        else if (arg[0] == '-') {
            std::cerr << "[Config] Unknown option: " << arg << std::endl;
        }
    }
    
    // Environment variable overrides
    const char* env_display = std::getenv("CANVAS_SOCKET");
    if (env_display && env_display[0]) {
        config.display_socket = env_display;
    }
    
    const char* env_db = std::getenv("CORNEA_DB");
    if (env_db && env_db[0]) {
        config.db_path = env_db;
    }
    
    return config;
}

void Config::print_usage() {
    std::cout << R"(cornea — Device Intelligence & Vulnerability Scanner

Usage: cornea [options]

Options:
  -c, --camera SOCKET      Camera socket (default: cam-0)
  -s, --size WxH           Resolution (default: 640x480)
  -r, --rotate DEGREES     Rotate frame (default: 0)
  -m, --modules LIST       Enabled modules (comma-separated, default: all)
      --overlay            Enable Android overlay (default: on)
      --no-overlay         Disable Android overlay
      --display SOCKET     Display socket (default: canvas-display)
      --tesseract-data PATH  Tesseract data directory
      --template-dir PATH  Logo templates directory
      --db PATH            Vulnerability database path
  -v, --verbose            Verbose output
  -h, --help               Show this help

Environment Variables:
  CANVAS_SOCKET            Override display socket
  CORNEA_DB                Vulnerability database path

Examples:
  cornea --verbose
  cornea -c cam-1 -s 1280x720 -m ocr,logo_detector
  cornea --no-overlay --template-dir=/path/to/templates
)" << std::endl;
}

EngineConfig Config::to_engine_config() const {
    EngineConfig config;
    
    config.camera_socket = camera_socket;
    config.width = width;
    config.height = height;
    config.rotate = rotate;
    config.verbose = verbose;
    config.overlay_enabled = overlay_enabled;
    config.display_socket = display_socket;
    
    if (!tesseract_data.empty()) {
        config.tesseract_data = tesseract_data;
    }
    if (!template_dir.empty()) {
        config.template_dir = template_dir;
    }
    if (!db_path.empty()) {
        config.db_path = db_path;
    }
    
    // Parse modules
    if (!modules.empty()) {
        std::istringstream iss(modules);
        std::string token;
        while (std::getline(iss, token, ',')) {
            // Trim whitespace
            token.erase(0, token.find_first_not_of(" "));
            token.erase(token.find_last_not_of(" ") + 1);
            if (!token.empty()) {
                config.modules.push_back(token);
            }
        }
    }
    
    return config;
}

} // namespace cornea

