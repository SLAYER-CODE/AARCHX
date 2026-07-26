/*
 * Lexis — Document Analysis & OCR Scanner
 *
 * Fork de Cornea. Reutiliza la infraestructura de cámara y overlay
 * pero reemplaza el pipeline de reconocimiento de dispositivos por
 * análisis de documentos: OCR (Tesseract) + Clasificación + Extracción.
 *
 * Flujo:
 *   1. Camera frames from Android (via cam-* socket, same as Iris/Cornea)
 *   2. OCR: Reconocer texto del documento
 *   3. Document Analyzer: Clasificar tipo de documento y extraer campos
 *   4. Display: Overlay Android + terminal stdout
 *
 * Uso:
 *   lexis [--camera=cam-0] [--size=640x480] [--modules=ocr,document_analyzer]
 *         [--overlay] [--verbose]
 */

#include "lexis/config.h"
#include "lexis/engine.h"
#include "lexis/overlay_renderer.h"
#include "lexis/modules/ocr.h"
#include "lexis/modules/document_analyzer.h"
#include "lexis/modules/diagnostics.h"

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
    auto config = lexis::Config::parse(argc, argv);
    
    if (config.help) {
        lexis::Config::print_usage();
        return 0;
    }
    
    // Banner
    std::cout << R"(
    __
   / /__  _____ ___
  / //_/ |/ / _ (_-<
 /__,__/\__/\___/___/
 
 Document Analysis & OCR Scanner v0.1.0
)" << std::endl;
    
    // Convert to engine config
    auto engine_config = config.to_engine_config();
    
    // Initialize engine
    lexis::Engine engine;
    if (!engine.init(engine_config)) {
        std::cerr << "[Lexis] Failed to initialize engine" << std::endl;
        return 1;
    }
    
    // Register modules
    engine.register_module(std::make_unique<lexis::OCRModule>());
    engine.register_module(std::make_unique<lexis::DocumentAnalyzerModule>());
    engine.register_module(std::make_unique<lexis::DiagnosticsModule>());
    
    // Set up overlay renderer
    lexis::OverlayRenderer renderer;
    if (engine_config.overlay_enabled) {
        renderer.init(engine_config.overlay_width, engine_config.overlay_height, 
                      engine_config.display_socket, engine_config.verbose);
    }
    
    // Set up frame callback (terminal output)
    engine.on_frame_processed([&renderer](const lexis::FrameResult& result) {
        if (result.valid) {
            renderer.print_document_info(result.document);
        }
    });
    
    // Set up raw frame callback (overlay)
    engine.on_frame_raw([&renderer](const uint32_t* pixels, int w, int h) {
        if (renderer.is_connected()) {
            renderer.present(pixels, w, h);
        }
    });
    
    // Start engine
    engine.start();
    
    std::cout << "[Lexis] Camera: " << engine_config.camera_socket << std::endl;
    std::cout << "[Lexis] Size: " << engine_config.width << "x" << engine_config.height << std::endl;
    std::cout << "[Lexis] Modules: " << engine_config.modules.size() << std::endl;
    std::cout << "[Lexis] Overlay: " << (engine_config.overlay_enabled ? "ON" : "OFF") << std::endl;
    std::cout << "[Lexis] Point camera at a document to analyze it" << std::endl;
    std::cout << "[Lexis] Press Ctrl+C to stop" << std::endl;
    std::cout << std::endl;
    
    // Main loop
    while (g_running) {
        sleep(1);
    }
    
    // Cleanup
    engine.stop();
    renderer.shutdown();
    
    std::cout << "\n[Lexis] Shutdown complete." << std::endl;
    return 0;
}
