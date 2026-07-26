#include "lexis/modules/diagnostics.h"
#include "iris/canvas.h"

#include <iostream>
#include <sstream>
#include <iomanip>

namespace lexis {

DiagnosticsModule::DiagnosticsModule() {}

DiagnosticsModule::~DiagnosticsModule() {
    shutdown();
}

bool DiagnosticsModule::init() {
    std::cout << "[Diagnostics] Initialized" << std::endl;
    return true;
}

void DiagnosticsModule::shutdown() {
    std::cout << "[Diagnostics] Shutdown" << std::endl;
}

void DiagnosticsModule::process_frame(uint32_t* pixels, int w, int h,
                                      FrameResult& result) {
    if (!enabled_ || !pixels) return;
    
    // Draw FPS counter (top-right corner)
    {
        std::ostringstream oss;
        oss << "FPS: " << current_fps_;
        draw_text(pixels, w, h, w - 100, 10, oss.str(), 0xFF00FF00);  // Green
    }
    
    // Draw module status (top-left corner)
    int y_offset = 10;
    for (const auto& [name, enabled] : module_status_) {
        std::string status = enabled ? "[ON]" : "[OFF]";
        uint32_t color = enabled ? 0xFF00FF00 : 0xFF0000FF;  // Green/Red
        draw_text(pixels, w, h, 10, y_offset, name + " " + status, color);
        y_offset += 20;
    }
    
    // Draw document info if available
    if (result.valid) {
        y_offset = h - 100;  // Bottom area
        
        // Document type
        if (!result.document.type_label.empty()) {
            draw_text(pixels, w, h, 10, y_offset, 
                     "Type: " + result.document.type_label, 0xFFFFFF00);  // Yellow
            y_offset += 20;
        }
        
        // Confidence
        {
            std::ostringstream oss;
            oss << std::fixed << std::setprecision(1) 
                << "OCR: " << (result.document.ocr_confidence * 100) << "%";
            draw_text(pixels, w, h, 10, y_offset, oss.str(), 0xFF00FFFF);  // Cyan
            y_offset += 20;
        }
        
        // Field count
        {
            std::ostringstream oss;
            oss << "Fields: " << result.document.fields.size();
            draw_text(pixels, w, h, 10, y_offset, oss.str(), 0xFF00FFFF);
            y_offset += 20;
        }
        
        // Show extracted fields
        for (const auto& field : result.document.fields) {
            if (y_offset > h - 20) break;  // Don't overflow
            draw_text(pixels, w, h, 10, y_offset, 
                     field.key + ": " + field.value, 0xFFFFFFFF);  // White
            y_offset += 15;
        }
    }
}

void DiagnosticsModule::update_fps(int fps) {
    current_fps_ = fps;
}

void DiagnosticsModule::update_module_status(const std::string& module, bool enabled) {
    module_status_[module] = enabled;
}

void DiagnosticsModule::draw_text(uint32_t* pixels, int w, int h, int x, int y,
                                  const std::string& text, uint32_t color) {
    // Use Iris bitmap font for text rendering
    iris::draw::draw_text(pixels, w, h, x, y, text.c_str(), color);
}

void DiagnosticsModule::draw_rect(uint32_t* pixels, int w, int h, int x, int y,
                                  int width, int height, uint32_t color) {
    // Draw rectangle outline
    for (int i = 0; i < width; i++) {
        if (x + i >= 0 && x + i < w && y >= 0 && y < h)
            pixels[y * w + (x + i)] = color;
        if (x + i >= 0 && x + i < w && y + height - 1 >= 0 && y + height - 1 < h)
            pixels[(y + height - 1) * w + (x + i)] = color;
    }
    for (int i = 0; i < height; i++) {
        if (x >= 0 && x < w && y + i >= 0 && y + i < h)
            pixels[(y + i) * w + x] = color;
        if (x + width - 1 >= 0 && x + width - 1 < w && y + i >= 0 && y + i < h)
            pixels[(y + i) * w + (x + width - 1)] = color;
    }
}

void DiagnosticsModule::draw_rect_filled(uint32_t* pixels, int w, int h, int x, int y,
                                         int width, int height, uint32_t color) {
    // Use Iris fill rect for filled rectangle
    iris::draw::fill_rect(pixels, w, h, x, y, width, height, color);
}

} // namespace lexis
