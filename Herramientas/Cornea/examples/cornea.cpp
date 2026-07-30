/*
 * Cornea — Device Intelligence & Vulnerability Scanner
 *
 * Derivación de Iris. Usa la infraestructura de cámara y overlay de Iris
 * pero reemplaza el pipeline de cámara por un motor de reconocimiento
 * visual de dispositivos: OCR (Tesseract) + Template Matching (logos).
 *
 * Flujo:
 *   1. Camera frames from Android (via cam-* socket, same as Iris)
 *   2. OCR: Leer etiquetas, números de modelo, seriales
 *   3. Logo Detection: Identificar fabricante por logo (template matching)
 *   4. VulnDB: Lookup en SQLite de vulnerabilidades
 *   5. Display: Overlay Android + terminal stdout
 *
 * Uso:
 *   cornea [--camera=cam-0] [--size=640x480] [--modules=ocr,logo_detector,vulndb]
 *          [--overlay] [--verbose]
 */

#include "cornea/config.h"
#include "cornea/engine.h"
#include "cornea/log.h"
#include "cornea/overlay_renderer.h"
#include "cornea/modules/ocr.h"
#include "cornea/modules/logo_detector.h"
#include "cornea/modules/vulndb.h"
#include "cornea/modules/diagnostics.h"
#include "cornea/modules/device_classifier.h"
#include "cornea/modules/visual_detector.h"

#include <iostream>
#include <memory>
#include <signal.h>
#include <unistd.h>

static std::atomic<bool> g_running{true};

static void signal_handler(int) {
    g_running = false;
}

int main(int argc, char** argv) {
    // Signal handlers
    signal(SIGINT, signal_handler);
    signal(SIGTERM, signal_handler);
    signal(SIGPIPE, SIG_IGN);
    
    // Parse config
    auto config = cornea::Config::parse(argc, argv);
    
    if (config.help) {
        cornea::Config::print_usage();
        return 0;
    }
    
    // Banner
    std::cout << R"(
  ___ ___ _____ ___
 / __/ __|_   _|_ _|
 \__ \__ \ | | | |
 |___/___/ |_| |___|

 Device Intelligence & Vulnerability Scanner v0.1.0
)" << std::endl;
    
    // Convert to engine config
    auto engine_config = config.to_engine_config();
    
    // Initialize engine
    cornea::Engine engine;
    if (!engine.init(engine_config)) {
        std::cerr << TAG_CORNEA << "Failed to initialize engine" << std::endl;
        return 1;
    }
    
    // Register modules
    engine.register_module(std::make_unique<cornea::OCRModule>());
    engine.register_module(std::make_unique<cornea::LogoDetectorModule>());
    engine.register_module(std::make_unique<cornea::VulnDBModule>());
    engine.register_module(std::make_unique<cornea::DiagnosticsModule>());
    engine.register_module(std::make_unique<cornea::DeviceClassifierModule>());
    engine.register_module(std::make_unique<cornea::VisualDetectorModule>());
    
    // Set up overlay renderer
    cornea::OverlayRenderer renderer;
    if (engine_config.overlay_enabled) {
        renderer.init(engine_config.overlay_width, engine_config.overlay_height, 
                      engine_config.display_socket, engine_config.verbose);
    }
    
    // Set up frame callback (terminal output)
    engine.on_frame_processed([&renderer](const cornea::FrameResult& result) {
        if (result.valid) {
            renderer.print_analysis(result);
        }
    });
    
    // Set up raw frame callback — NO present here, engine handles it.
    // Only used for terminal stdout output (via frame_callback_).
    // poll_commands is also handled by the engine.
    engine.on_frame_raw([&renderer](const uint32_t* pixels, int w, int h) {
        // Intentionally empty — engine handles present + poll_commands
    });
    
    // Connect canvas + renderer to engine so the pipeline handles present + poll_commands
    engine.set_overlay_canvas(renderer.canvas());
    engine.set_overlay_renderer(&renderer);
    
    // Start engine
    engine.start();
    
    std::cout << TAG_CORNEA << "Camera: " << engine_config.camera_socket << std::endl;
    std::cout << TAG_CORNEA << "Size: " << engine_config.width << "x" << engine_config.height << std::endl;
    std::cout << TAG_CORNEA << "Modules: " << engine_config.modules.size() << std::endl;
    std::cout << TAG_CORNEA << "Overlay: " << (engine_config.overlay_enabled ? "ON" : "OFF") << std::endl;
    std::cout << TAG_CORNEA << "Point camera at a device to identify it" << std::endl;
    std::cout << TAG_CORNEA << "Press Ctrl+C to stop" << std::endl;
    std::cout << std::endl;
    
    // Main loop
    while (g_running) {
        sleep(1);
    }
    
    // Cleanup
    engine.stop();
    renderer.shutdown();
    
    std::cout << TAG_CORNEA << "Shutdown complete." << std::endl;
    return 0;
}

