/*
 * text_overlay_demo — Demo simple: cuadros + flechas sobre texto
 *
 * Genera un frame de prueba con "texto" y dibuja:
 *   - Cuadro verde alrededor del texto
 *   - Flecha roja señalando el texto
 *   - Etiqueta con el texto detectado
 *
 * Sin dependencias de Tesseract - solo dibujo puro.
 *
 * Uso: text_overlay_demo [--display=canvas-display] [--size=640x480]
 */

#include "iris/canvas.h"
#include <iostream>
#include <string>
#include <vector>
#include <csignal>
#include <cstring>
#include <cmath>
#include <chrono>
#include <thread>

static std::atomic<bool> g_running{true};
static void signal_handler(int) { g_running = false; }

// ── Colors ───────────────────────────────────────────────────────

constexpr uint32_t COLOR_GREEN  = 0xFF00FF00;   // Bounding box
constexpr uint32_t COLOR_RED    = 0xFF0000FF;   // Flecha
constexpr uint32_t COLOR_YELLOW = 0xFF00FFFF;   // Etiqueta texto
constexpr uint32_t COLOR_WHITE  = 0xFFFFFFFF;   // Texto
constexpr uint32_t COLOR_BLACK  = 0xFF000000;   // Fondo
constexpr uint32_t COLOR_BG     = 0xCC000000;   // Fondo semi-transparente

// ── Drawing primitives ───────────────────────────────────────────

void draw_rect(uint32_t* pixels, int w, int h,
               int x, int y, int rw, int rh, uint32_t color, int thickness = 2) {
    for (int t = 0; t < thickness; t++) {
        // Top/bottom
        for (int i = x; i < x + rw && i < w; i++) {
            if (y + t >= 0 && y + t < h) pixels[(y + t) * w + i] = color;
            if (y + rh - 1 - t >= 0 && y + rh - 1 - t < h) pixels[(y + rh - 1 - t) * w + i] = color;
        }
        // Left/right
        for (int j = y; j < y + rh && j < h; j++) {
            if (x + t >= 0 && x + t < w) pixels[j * w + (x + t)] = color;
            if (x + rw - 1 - t >= 0 && x + rw - 1 - t < w) pixels[j * w + (x + rw - 1 - t)] = color;
        }
    }
}

void draw_line(uint32_t* pixels, int w, int h,
               int x1, int y1, int x2, int y2, uint32_t color, int thickness = 2) {
    int dx = abs(x2 - x1);
    int dy = abs(y2 - y1);
    int sx = x1 < x2 ? 1 : -1;
    int sy = y1 < y2 ? 1 : -1;
    int err = dx - dy;
    
    while (true) {
        for (int t = 0; t < thickness; t++) {
            if (x1 >= 0 && x1 < w && y1 + t >= 0 && y1 + t < h)
                pixels[(y1 + t) * w + x1] = color;
            if (x1 >= 0 && x1 < w && y1 - t >= 0 && y1 - t < h)
                pixels[(y1 - t) * w + x1] = color;
        }
        
        if (x1 == x2 && y1 == y2) break;
        
        int e2 = 2 * err;
        if (e2 > -dy) { err -= dy; x1 += sx; }
        if (e2 < dx) { err += dx; y1 += sy; }
    }
}

void draw_arrow(uint32_t* pixels, int w, int h,
                int x1, int y1, int x2, int y2, uint32_t color, int thickness = 3) {
    // Draw main line
    draw_line(pixels, w, h, x1, y1, x2, y2, color, thickness);
    
    // Draw arrowhead
    int head_size = 15;
    double angle = atan2(y2 - y1, x2 - x1);
    double a1 = angle + M_PI * 0.8;
    double a2 = angle - M_PI * 0.8;
    
    int hx1 = x2 + head_size * cos(a1);
    int hy1 = y2 + head_size * sin(a1);
    int hx2 = x2 + head_size * cos(a2);
    int hy2 = y2 + head_size * sin(a2);
    
    draw_line(pixels, w, h, x2, y2, hx1, hy1, color, thickness);
    draw_line(pixels, w, h, x2, y2, hx2, hy2, color, thickness);
}

// ── Text rendering (simple bitmap) ───────────────────────────────

// 5x7 bitmap font for A-Z, 0-9, and some symbols
static const uint8_t FONT_5X7[][7] = {
    // A-Z
    {0x7C,0x12,0x11,0x12,0x7C,0x00,0x00}, // A
    {0x7F,0x49,0x49,0x49,0x36,0x00,0x00}, // B
    {0x3E,0x41,0x41,0x41,0x22,0x00,0x00}, // C
    {0x7F,0x41,0x41,0x41,0x3E,0x00,0x00}, // D
    {0x7F,0x49,0x49,0x49,0x41,0x00,0x00}, // E
    {0x7F,0x09,0x09,0x09,0x01,0x00,0x00}, // F
    {0x3E,0x41,0x49,0x49,0x7A,0x00,0x00}, // G
    {0x7F,0x08,0x08,0x08,0x7F,0x00,0x00}, // H
    {0x00,0x41,0x7F,0x41,0x00,0x00,0x00}, // I
    {0x20,0x40,0x41,0x3F,0x01,0x00,0x00}, // J
    {0x7F,0x08,0x14,0x22,0x41,0x00,0x00}, // K
    {0x7F,0x40,0x40,0x40,0x40,0x00,0x00}, // L
    {0x7F,0x02,0x0C,0x02,0x7F,0x00,0x00}, // M
    {0x7F,0x04,0x08,0x10,0x7F,0x00,0x00}, // N
    {0x3E,0x41,0x41,0x41,0x3E,0x00,0x00}, // O
    {0x7F,0x09,0x09,0x09,0x06,0x00,0x00}, // P
    {0x3E,0x41,0x51,0x21,0x5E,0x00,0x00}, // Q
    {0x7F,0x09,0x19,0x29,0x46,0x00,0x00}, // R
    {0x46,0x49,0x49,0x49,0x31,0x00,0x00}, // S
    {0x01,0x01,0x7F,0x01,0x01,0x00,0x00}, // T
    {0x3F,0x40,0x40,0x40,0x3F,0x00,0x00}, // U
    {0x1F,0x20,0x40,0x20,0x1F,0x00,0x00}, // V
    {0x3F,0x40,0x38,0x40,0x3F,0x00,0x00}, // W
    {0x63,0x14,0x08,0x14,0x63,0x00,0x00}, // X
    {0x07,0x08,0x70,0x08,0x07,0x00,0x00}, // Y
    {0x61,0x51,0x49,0x45,0x43,0x00,0x00}, // Z
    // 0-9
    {0x3E,0x51,0x49,0x45,0x3E,0x00,0x00}, // 0
    {0x00,0x42,0x7F,0x40,0x00,0x00,0x00}, // 1
    {0x42,0x61,0x51,0x49,0x46,0x00,0x00}, // 2
    {0x21,0x41,0x45,0x4B,0x31,0x00,0x00}, // 3
    {0x18,0x14,0x12,0x7F,0x10,0x00,0x00}, // 4
    {0x27,0x45,0x45,0x45,0x39,0x00,0x00}, // 5
    {0x3C,0x4A,0x49,0x49,0x30,0x00,0x00}, // 6
    {0x01,0x71,0x09,0x05,0x03,0x00,0x00}, // 7
    {0x36,0x49,0x49,0x49,0x36,0x00,0x00}, // 8
    {0x06,0x49,0x49,0x29,0x1E,0x00,0x00}, // 9
};

void draw_char(uint32_t* pixels, int w, int h,
               int x, int y, char c, uint32_t color) {
    int idx = -1;
    if (c >= 'A' && c <= 'Z') idx = c - 'A';
    else if (c >= '0' && c <= '9') idx = 26 + c - '0';
    else if (c >= 'a' && c <= 'z') idx = c - 'a';  // lowercase → uppercase
    
    if (idx < 0 || idx >= 36) return;
    
    for (int row = 0; row < 7; row++) {
        uint8_t bits = FONT_5X7[idx][row];
        for (int col = 0; col < 5; col++) {
            if (bits & (0x80 >> col)) {
                int px = x + col;
                int py = y + row;
                if (px >= 0 && px < w && py >= 0 && py < h) {
                    pixels[py * w + px] = color;
                }
            }
        }
    }
}

void draw_text(uint32_t* pixels, int w, int h,
               int x, int y, const std::string& text, uint32_t color) {
    for (size_t i = 0; i < text.length(); i++) {
        draw_char(pixels, w, h, x + i * 6, y, text[i], color);
    }
}

// ── Generate test frame ──────────────────────────────────────────

struct TextLabel {
    int x, y;
    std::string text;
};

void generate_test_frame(uint32_t* pixels, int w, int h, int frame) {
    // Background gradient
    for (int y = 0; y < h; y++) {
        for (int x = 0; x < w; x++) {
            double t = frame * 0.02;
            uint8_t r = (uint8_t)(60 + 40 * sin(x * 0.01 + t));
            uint8_t g = (uint8_t)(60 + 40 * sin(y * 0.01 + t));
            uint8_t b = (uint8_t)(80 + 40 * sin((x + y) * 0.005 + t));
            pixels[y * w + x] = 0xFF000000 | (b << 16) | (g << 8) | r;
        }
    }
    
    // Simulated text labels (as white rectangles with text)
    std::vector<TextLabel> labels = {
        {50, 60, "SAMSUNG"},
        {200, 180, "GALAXY S24"},
        {100, 320, "MADE IN KOREA"},
        {320, 280, "SM-S921B"},
        {150, 420, "FCC ID"},
    };
    
    for (const auto& label : labels) {
        int text_w = label.text.length() * 6;
        int text_h = 10;
        
        // White background for "label"
        for (int y = label.y - 2; y < label.y + text_h + 2 && y < h; y++) {
            for (int x = label.x - 2; x < label.x + text_w + 2 && x < w; x++) {
                if (x >= 0 && y >= 0) {
                    pixels[y * w + x] = 0xFFFFFFFF;
                }
            }
        }
        
        // Draw text
        draw_text(pixels, w, h, label.x, label.y, label.text, COLOR_BLACK);
    }
}

// ── Main ─────────────────────────────────────────────────────────

int main(int argc, char** argv) {
    signal(SIGINT, signal_handler);
    signal(SIGTERM, signal_handler);
    signal(SIGPIPE, SIG_IGN);
    
    std::string display_socket = "canvas-display";
    int width = 640, height = 480;
    
    for (int i = 1; i < argc; i++) {
        std::string arg = argv[i];
        if (arg.rfind("--display=", 0) == 0) display_socket = arg.substr(10);
        else if (arg.rfind("--size=", 0) == 0) sscanf(arg.c_str() + 7, "%dx%d", &width, &height);
        else if (arg == "--help") {
            std::cout << "text_overlay_demo — Bounding boxes + arrows on text\n\n"
                      << "Uso: text_overlay_demo [--display=canvas-display] [--size=640x480]\n";
            return 0;
        }
    }
    
    std::cout << "=== Text Overlay Demo ===\n\n";
    std::cout << "Showing:\n";
    std::cout << "  - Green bounding boxes around detected text\n";
    std::cout << "  - Red arrows pointing to each label\n";
    std::cout << "  - Yellow labels with detected text\n\n";
    
    // Connect to display
    iris::Canvas canvas;
    while (g_running && !canvas.connect_overlay(display_socket)) {
        std::cerr << "Waiting for display...\n";
        std::this_thread::sleep_for(std::chrono::seconds(1));
    }
    
    canvas.init(width, height);
    std::cout << "Display connected: " << display_socket << "\n";
    std::cout << "Press Ctrl+C to stop\n\n";
    
    // Labels to detect (simulating OCR results)
    std::vector<TextLabel> detected = {
        {50, 60, "SAMSUNG"},
        {200, 180, "GALAXY S24"},
        {100, 320, "MADE IN KOREA"},
        {320, 280, "SM-S921B"},
        {150, 420, "FCC ID"},
    };
    
    int frame = 0;
    while (g_running) {
        // Generate frame
        generate_test_frame(canvas.pixels(), width, height, frame++);
        
        // Draw overlays on each detected text
        for (const auto& label : detected) {
            int text_w = label.text.length() * 6;
            int text_h = 10;
            
            // 1. Green bounding box
            draw_rect(canvas.pixels(), width, height,
                     label.x - 8, label.y - 8,
                     text_w + 16, text_h + 16,
                     COLOR_GREEN, 3);
            
            // 2. Red arrow from above
            int arrow_x = label.x + text_w / 2;
            int arrow_y_start = label.y - 50;
            if (arrow_y_start < 10) arrow_y_start = label.y + text_h + 50;
            
            draw_arrow(canvas.pixels(), width, height,
                      arrow_x, arrow_y_start,
                      arrow_x, label.y - 8,
                      COLOR_RED, 3);
            
            // 3. Yellow label above
            int label_x = label.x;
            int label_y = label.y - 25;
            if (label_y < 5) label_y = label.y + text_h + 15;
            
            draw_rect(canvas.pixels(), width, height,
                     label_x - 2, label_y - 2,
                     text_w + 4, text_h + 4,
                     COLOR_BG);
            draw_text(canvas.pixels(), width, height,
                     label_x, label_y,
                     label.text, COLOR_YELLOW);
        }
        
        // Print detection info
        std::cout << "\r[Frame " << frame << "] Detected " << detected.size() << " labels" << std::flush;
        
        // Send to display
        canvas.present();
        
        std::this_thread::sleep_for(std::chrono::milliseconds(33));  // ~30fps
    }
    
    std::cout << "\n\nDone.\n";
    return 0;
}

