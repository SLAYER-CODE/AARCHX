/*
 * ocr_demo — Demostración de OCR con bounding boxes y flechas
 *
 * Muestra cómo Tesseract detecta texto en un frame de cámara,
 * dibuja cuadros alrededor del texto y flechas señalándolo.
 *
 * Uso:
 *   ocr_demo [--camera=cam-0] [--size=640x480] [--display=canvas-display]
 *
 * Flujo:
 *   1. Recibe frame de cámara (o genera patrón de prueba)
 *   2. Tesseract detecta texto + bounding boxes
 *   3. Dibuja cuadro verde alrededor de cada palabra
 *   4. Dibuja flecha roja señalando el texto
 *   5. Envía frame anotado al overlay Android
 */

#include "iris/canvas.h"
#include "iris/camera_canvas.h"

#include <iostream>
#include <string>
#include <vector>
#include <csignal>
#include <cstring>
#include <cmath>
#include <chrono>
#include <thread>
#include <algorithm>

// Tesseract OCR
#ifdef CORNEA_HAS_TESSERACT
#include <tesseract/baseapi.h>
#include <leptonica/allheaders.h>
#endif

static std::atomic<bool> g_running{true};
static void signal_handler(int) { g_running = false; }

// ── Estructuras ──────────────────────────────────────────────────

struct TextBlock {
    std::string text;
    float confidence;
    int x, y, w, h;  // Bounding box
};

struct Config {
    std::string camera_socket = "cam-0";
    std::string display_socket = "canvas-display";
    int width = 640;
    int height = 480;
    bool demo_mode = false;  // Generar patrón de prueba sin cámara
};

// ── Dibujo en frame ──────────────────────────────────────────────

// Color constants (BGRA)
constexpr uint32_t COLOR_GREEN  = 0xFF00FF00;
constexpr uint32_t COLOR_RED    = 0xFF0000FF;
constexpr uint32_t COLOR_YELLOW = 0xFF00FFFF;
constexpr uint32_t COLOR_WHITE  = 0xFFFFFFFF;
constexpr uint32_t COLOR_BG     = 0xCC000000;

void draw_rect(uint32_t* pixels, int w, int h,
               int x, int y, int rw, int rh, uint32_t color, int thickness = 2) {
    for (int t = 0; t < thickness; t++) {
        // Horizontal lines
        for (int i = x; i < x + rw && i < w; i++) {
            if (y + t >= 0 && y + t < h) pixels[(y + t) * w + i] = color;
            if (y + rh - 1 - t >= 0 && y + rh - 1 - t < h) pixels[(y + rh - 1 - t) * w + i] = color;
        }
        // Vertical lines
        for (int j = y; j < y + rh && j < h; j++) {
            if (x + t >= 0 && x + t < w) pixels[j * w + (x + t)] = color;
            if (x + rw - 1 - t >= 0 && x + rw - 1 - t < w) pixels[j * w + (x + rw - 1 - t)] = color;
        }
    }
}

void draw_arrow(uint32_t* pixels, int w, int h,
                int x1, int y1, int x2, int y2, uint32_t color, int thickness = 2) {
    // Draw line
    int dx = abs(x2 - x1);
    int dy = abs(y2 - y1);
    int sx = x1 < x2 ? 1 : -1;
    int sy = y1 < y2 ? 1 : -1;
    int err = dx - dy;
    
    int x = x1, y = y1;
    while (true) {
        // Draw thick pixel
        for (int t = 0; t < thickness; t++) {
            if (x >= 0 && x < w && y + t >= 0 && y + t < h)
                pixels[(y + t) * w + x] = color;
            if (x >= 0 && x < w && y - t >= 0 && y - t < h)
                pixels[(y - t) * w + x] = color;
        }
        
        if (x == x2 && y == y2) break;
        
        int e2 = 2 * err;
        if (e2 > -dy) { err -= dy; x += sx; }
        if (e2 < dx) { err += dx; y += sy; }
    }
    
    // Draw arrowhead
    int head_size = 10;
    double angle = atan2(y2 - y1, x2 - x1);
    double a1 = angle + M_PI * 0.8;
    double a2 = angle - M_PI * 0.8;
    
    int hx1 = x2 + head_size * cos(a1);
    int hy1 = y2 + head_size * sin(a1);
    int hx2 = x2 + head_size * cos(a2);
    int hy2 = y2 + head_size * sin(a2);
    
    draw_arrow(pixels, w, h, x2, y2, hx1, hy1, color, thickness);
    draw_arrow(pixels, w, h, x2, y2, hx2, hy2, color, thickness);
}

void draw_text_simple(uint32_t* pixels, int w, int h,
                      int x, int y, const std::string& text, uint32_t color) {
    // Placeholder: dibuja un rectángulo por cada carácter
    // En producción usar bitmap font real
    int char_w = 8;
    for (size_t i = 0; i < text.length(); i++) {
        int cx = x + i * char_w;
        if (cx + char_w <= w && y + 12 <= h) {
            draw_rect(pixels, w, h, cx, y, char_w, 12, color, 1);
        }
    }
}

// ── Generar patrón de prueba (sin cámara) ────────────────────────

void generate_test_pattern(uint32_t* pixels, int w, int h, int frame) {
    double t = frame * 0.02;
    
    // Background
    for (int y = 0; y < h; y++) {
        for (int x = 0; x < w; x++) {
            double u = (double)x / w;
            double v = (double)y / h;
            
            // Gradiente suave
            uint8_t r = (uint8_t)(100 + 50 * sin(u * 3.0 + t));
            uint8_t g = (uint8_t)(100 + 50 * sin(v * 3.0 + t));
            uint8_t b = (uint8_t)(100 + 50 * sin((u + v) * 2.0 + t));
            
            pixels[y * w + x] = 0xFF000000 | (b << 16) | (g << 8) | r;
        }
    }
    
    // Simular "texto" en diferentes posiciones
    // En lugar de texto real, dibujamos rectángulos que simulan etiquetas
    struct FakeLabel {
        int x, y, w, h;
        const char* text;
    };
    
    FakeLabel labels[] = {
        {50, 50, 120, 30, "SAMSUNG"},
        {200, 150, 100, 25, "Galaxy S24"},
        {100, 300, 80, 20, "MADE IN KOREA"},
        {300, 250, 90, 25, "Model: SM-S921B"},
    };
    
    for (const auto& label : labels) {
        // Fondo blanco para simular etiqueta
        for (int y = label.y; y < label.y + label.h && y < h; y++) {
            for (int x = label.x; x < label.x + label.w && x < w; x++) {
                pixels[y * w + x] = 0xFFFFFFFF;
            }
        }
        
        // Texto negro (placeholder)
        draw_text_simple(pixels, w, h, label.x + 5, label.y + 5, 
                        label.text, 0xFF000000);
    }
}

// ── OCR Processing ───────────────────────────────────────────────

#ifdef CORNEA_HAS_TESSERACT
std::vector<TextBlock> detect_text_tesseract(tesseract::TessBaseAPI* api,
                                             const uint32_t* pixels, int w, int h) {
    std::vector<TextBlock> results;
    
    // Convert BGRA to grayscale for Tesseract
    Pix* image = pixCreate(w, h, 8);
    for (int y = 0; y < h; y++) {
        for (int x = 0; x < w; x++) {
            uint32_t px = pixels[y * w + x];
            uint8_t r = px & 0xFF;
            uint8_t g = (px >> 8) & 0xFF;
            uint8_t b = (px >> 16) & 0xFF;
            uint8_t gray = (uint8_t)(0.299 * r + 0.587 * g + 0.114 * b);
            pixSetPixel(image, x, y, gray);
        }
    }
    
    api->SetImage(image);
    
    // Get word-level results with bounding boxes
    api->Recognize(0);
    tesseract::ResultIterator* ri = api->GetIterator();
    
    if (ri) {
        do {
            // Get bounding box
            int x1, y1, x2, y2;
            ri->BoundingBox(tesseract::RIL_WORD, &x1, &y1, &x2, &y2);
            
            // Get text and confidence
            char* word = ri->GetUTF8Text(tesseract::RIL_WORD);
            float conf = ri->Confidence(tesseract::RIL_WORD);
            
            if (word && conf > 30.0f) {
                TextBlock block;
                block.text = word;
                block.confidence = conf / 100.0f;
                block.x = x1;
                block.y = y1;
                block.w = x2 - x1;
                block.h = y2 - y1;
                results.push_back(block);
            }
            
            delete[] word;
        } while (ri->Next(tesseract::RIL_WORD));
        
        delete ri;
    }
    
    api->Clear();
    pixDestroy(&image);
    
    return results;
}
#endif

// Demo fallback (sin Tesseract)
std::vector<TextBlock> detect_text_demo(const uint32_t* pixels, int w, int h) {
    std::vector<TextBlock> results;
    
    // Simular detección de texto en posiciones conocidas
    // (las mismas que generate_test_pattern)
    results.push_back({"SAMSUNG", 0.95f, 50, 50, 120, 30});
    results.push_back({"Galaxy S24", 0.88f, 200, 150, 100, 25});
    results.push_back({"MADE IN KOREA", 0.82f, 100, 300, 80, 20});
    results.push_back({"Model: SM-S921B", 0.79f, 300, 250, 90, 25});
    
    return results;
}

// ── Annotate frame ───────────────────────────────────────────────

void annotate_frame(uint32_t* pixels, int w, int h,
                    const std::vector<TextBlock>& blocks) {
    for (const auto& block : blocks) {
        // 1. Dibujar bounding box verde alrededor del texto
        draw_rect(pixels, w, h, 
                  block.x - 5, block.y - 5, 
                  block.w + 10, block.h + 10, 
                  COLOR_GREEN, 3);
        
        // 2. Dibujar flecha roja señalando el texto
        // Flecha desde arriba hacia el bounding box
        int arrow_x = block.x + block.w / 2;
        int arrow_start_y = block.y - 40;
        if (arrow_start_y < 0) arrow_start_y = block.y + block.h + 40;
        
        draw_arrow(pixels, w, h,
                   arrow_x, arrow_start_y,
                   arrow_x, block.y - 5,
                   COLOR_RED, 3);
        
        // 3. Dibujar etiqueta con el texto detectado
        draw_rect(pixels, w, h,
                  block.x, block.y - 25,
                  block.w, 20,
                  COLOR_BG);
        draw_text_simple(pixels, w, h,
                        block.x + 2, block.y - 22,
                        block.text, COLOR_YELLOW);
    }
}

// ── Main ─────────────────────────────────────────────────────────

int main(int argc, char** argv) {
    signal(SIGINT, signal_handler);
    signal(SIGTERM, signal_handler);
    signal(SIGPIPE, SIG_IGN);
    
    Config cfg;
    
    // Parse args
    for (int i = 1; i < argc; i++) {
        std::string arg = argv[i];
        if (arg == "--demo") cfg.demo_mode = true;
        else if (arg.rfind("--camera=", 0) == 0) cfg.camera_socket = arg.substr(9);
        else if (arg.rfind("--display=", 0) == 0) cfg.display_socket = arg.substr(10);
        else if (arg.rfind("--size=", 0) == 0) {
            sscanf(arg.c_str() + 7, "%dx%d", &cfg.width, &cfg.height);
        }
        else if (arg == "--help") {
            std::cout << "ocr_demo — OCR con bounding boxes y flechas\n\n"
                      << "Uso: ocr_demo [opciones]\n\n"
                      << "Opciones:\n"
                      << "  --camera=SOCKET    Socket de cámara (default: cam-0)\n"
                      << "  --display=SOCKET   Socket de display (default: canvas-display)\n"
                      << "  --size=WxH         Resolución (default: 640x480)\n"
                      << "  --demo             Modo demo sin cámara real\n"
                      << "  --help             Mostrar ayuda\n";
            return 0;
        }
    }
    
    std::cout << "=== OCR Demo: Bounding Boxes + Flechas ===\n\n";
    
    // Init Tesseract (if available)
#ifdef CORNEA_HAS_TESSERACT
    tesseract::TessBaseAPI* api = new tesseract::TessBaseAPI();
    if (api->Init(NULL, "eng")) {
        std::cerr << "[OCR] Tesseract init failed, using demo mode\n";
        delete api;
        api = nullptr;
        cfg.demo_mode = true;
    } else {
        std::cout << "[OCR] Tesseract initialized\n";
    }
#else
    std::cout << "[OCR] Tesseract not available, using demo mode\n";
    cfg.demo_mode = true;
#endif
    
    // Init display canvas
    iris::Canvas display;
    while (g_running && !display.connect_overlay(cfg.display_socket)) {
        std::cerr << "[OCR] Waiting for display...\n";
        std::this_thread::sleep_for(std::chrono::seconds(1));
    }
    
    if (g_running) {
        display.init(cfg.width, cfg.height);
        std::cout << "[OCR] Display connected\n";
    }
    
    // Init camera (if not demo mode)
    iris::CameraCanvas camera;
    if (!cfg.demo_mode) {
        if (!camera.listen(cfg.camera_socket)) {
            std::cerr << "[OCR] Failed to listen on " << cfg.camera_socket << "\n";
            return 1;
        }
        std::cout << "[OCR] Listening on " << cfg.camera_socket << "\n";
    }
    
    std::cout << "[OCR] Point camera at text (e.g. device label)\n";
    std::cout << "[OCR] Press Ctrl+C to stop\n\n";
    
    int frame_count = 0;
    
    while (g_running) {
        std::vector<uint32_t> frame(cfg.width * cfg.height);
        bool got_frame = false;
        
        if (cfg.demo_mode) {
            // Generate test pattern
            generate_test_pattern(frame.data(), cfg.width, cfg.height, frame_count++);
            got_frame = true;
        } else {
            // Receive from camera
            if (!camera.connected()) {
                camera.accept_client();
            }
            if (camera.recv_frame()) {
                // Copy to our buffer
                memcpy(frame.data(), camera.pixels(), 
                       cfg.width * cfg.height * 4);
                got_frame = true;
            }
        }
        
        if (!got_frame) {
            std::this_thread::sleep_for(std::chrono::milliseconds(10));
            continue;
        }
        
        // Detect text
        std::vector<TextBlock> blocks;
#ifdef CORNEA_HAS_TESSERACT
        if (api && !cfg.demo_mode) {
            blocks = detect_text_tesseract(api, frame.data(), cfg.width, cfg.height);
        } else {
            blocks = detect_text_demo(frame.data(), cfg.width, cfg.height);
        }
#else
        blocks = detect_text_demo(frame.data(), cfg.width, cfg.height);
#endif
        
        // Annotate frame with bounding boxes and arrows
        annotate_frame(frame.data(), cfg.width, cfg.height, blocks);
        
        // Print detected text
        if (!blocks.empty()) {
            std::cout << "[OCR] Detected " << blocks.size() << " text blocks:\n";
            for (const auto& block : blocks) {
                std::cout << "  \"" << block.text << "\"";
                std::cout << " @ [" << block.x << "," << block.y << "]";
                std::cout << " (" << (int)(block.confidence * 100) << "%)";
                std::cout << "\n";
            }
        }
        
        // Send to display
        display.load_frame(
            reinterpret_cast<const uint8_t*>(frame.data()),
            cfg.width * cfg.height * 4
        );
        display.present();
        
        std::this_thread::sleep_for(std::chrono::milliseconds(33));  // ~30fps
    }
    
    // Cleanup
#ifdef CORNEA_HAS_TESSERACT
    if (api) {
        api->End();
        delete api;
    }
#endif
    
    std::cout << "\n[OCR] Done.\n";
    return 0;
}

