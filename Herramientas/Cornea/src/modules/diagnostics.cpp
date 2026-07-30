#include "cornea/modules/diagnostics.h"
#include "cornea/log.h"
#include "cornea/overlay_renderer.h"
#include "iris/canvas.h"

#include <iostream>
#include <cstring>
#include <algorithm>

namespace cornea {

bool DiagnosticsModule::init() {
    if (verbose_) std::cout << TAG_DIAGNOSTICS << "Initialized" << std::endl;
    return true;
}

void DiagnosticsModule::shutdown() {
    if (verbose_) std::cout << TAG_DIAGNOSTICS << "Shutdown" << std::endl;
}

void DiagnosticsModule::process_frame(uint32_t* pixels, int w, int h, 
                                       FrameResult& result) {
    if (!enabled_ || !pixels) return;
    
    if (show_fps_) render_fps_panel(pixels, w, h);
    if (show_ocr_) render_ocr_results(pixels, w, h, result);
    render_visual_detections(pixels, w, h, result);
    if (show_device_) render_device_info(pixels, w, h, result);
    if (show_modules_) render_module_status(pixels, w, h);
}

void DiagnosticsModule::update_module_status(const std::string& name, bool active) {
    for (auto& ms : module_status_) {
        if (ms.name == name) {
            ms.active = active;
            return;
        }
    }
    module_status_.push_back({name, active});
}

void DiagnosticsModule::render_fps_panel(uint32_t* pixels, int w, int h) {
    draw_text(pixels, w, h, pos_x_, pos_y_ + 14, 
              "FPS: " + std::to_string(current_fps_), OverlayRenderer::COLOR_TEXT, 12);
}

void DiagnosticsModule::render_ocr_results(uint32_t* pixels, int w, int h, 
                                            const FrameResult& result) {
    if (result.device.text_blocks.empty()) return;
    
    // Draw green bounding boxes around detected text
    for (const auto& block : result.device.text_blocks) {
        draw_bounding_box(pixels, w, h, block.x, block.y, 
                          block.width, block.height, OverlayRenderer::COLOR_OCR, 2);
    }
}

void DiagnosticsModule::render_visual_detections(uint32_t* pixels, int w, int h,
                                                  const FrameResult& result) {
    static constexpr uint32_t COLOR_YOLO   = 0xFF0000FF;  // Blue (BGRA) — raw
    static constexpr uint32_t COLOR_TRACK  = 0xFFFF00FF;  // Cyan (BGRA) — tracked

    if (verbose_) {
        static int dbg_count = 0;
        if (dbg_count < 5) {
            std::cout << TAG_DIAGNOSTICS << "tracks=" << result.tracked_objects.size()
                      << " raw=" << result.visual_detections.size() << std::endl;
            dbg_count++;
        }
    }

    // Draw tracked boxes in cyan (smooth, multi-frame)
    for (const auto& trk : result.tracked_objects) {
        int bx = (int)trk.x;
        int by = (int)trk.y;
        int bw = (int)trk.w;
        int bh = (int)trk.h;
        if (bw <= 0 || bh <= 0) continue;

        draw_bounding_box(pixels, w, h, bx, by, bw, bh, COLOR_TRACK, 3);

        std::string label = "#" + std::to_string(trk.track_id) + " "
                          + trk.class_name + " "
                          + std::to_string((int)(trk.confidence * 100)) + "%";
        draw_text(pixels, w, h, bx, by - 14, label, COLOR_TRACK, 10);
    }

    // Fallback: if no tracks yet, draw raw YOLO boxes in blue
    if (result.tracked_objects.empty()) {
        for (const auto& det : result.visual_detections) {
            int bx = (int)det.x;
            int by = (int)det.y;
            int bw = (int)det.w;
            int bh = (int)det.h;
            if (bw <= 0 || bh <= 0) continue;

            draw_bounding_box(pixels, w, h, bx, by, bw, bh, COLOR_YOLO, 2);

            std::string label = det.class_name + " " + std::to_string((int)(det.confidence * 100)) + "%";
            draw_text(pixels, w, h, bx, by - 14, label, COLOR_YOLO, 10);
        }
    }
}

void DiagnosticsModule::render_device_info(uint32_t* pixels, int w, int h, 
                                            const FrameResult& result) {
    if (!result.valid) return;
    
    // Draw device info text without background (just text)
    int y = h - 80;
    int x = 10;
    
    // Device Type (most important)
    if (!result.device.type_label.empty()) {
        draw_text(pixels, w, h, x, y, "Type: " + result.device.type_label, OverlayRenderer::COLOR_HIGH, 14);
        y += 18;
    }
    
    // Device State
    if (!result.device.state_label.empty()) {
        uint32_t state_color = OverlayRenderer::COLOR_TEXT;
        if (result.device.state_label == "ON") {
            state_color = OverlayRenderer::COLOR_LOW;  // Green
        } else if (result.device.state_label == "OFF") {
            state_color = OverlayRenderer::COLOR_CRITICAL;  // Red
        } else if (result.device.state_label == "ERROR") {
            state_color = OverlayRenderer::COLOR_CRITICAL;
        } else if (result.device.state_label == "SLEEP" || result.device.state_label == "BOOTING") {
            state_color = OverlayRenderer::COLOR_MEDIUM;  // Yellow
        }
        draw_text(pixels, w, h, x, y, "State: " + result.device.state_label, state_color, 14);
        y += 18;
    }
    
    // Vendor
    if (!result.device.vendor.empty()) {
        draw_text(pixels, w, h, x, y, "Vendor: " + result.device.vendor, OverlayRenderer::COLOR_TEXT, 12);
        y += 15;
    }
    
    // Model
    if (!result.device.model.empty()) {
        draw_text(pixels, w, h, x, y, "Model: " + result.device.model, OverlayRenderer::COLOR_TEXT, 12);
        y += 15;
    }
    
    // Confidence
    draw_text(pixels, w, h, x, y, 
              "OCR: " + std::to_string((int)(result.device.ocr_confidence * 100)) + "% " +
              "Class: " + std::to_string((int)(result.device.classification_confidence * 100)) + "%", 
              OverlayRenderer::COLOR_TEXT, 10);
    
    // Vulnerabilities count (right side)
    if (!result.device.vulns.empty()) {
        draw_text(pixels, w, h, w - 120, h - 80, 
                  "VULNS: " + std::to_string(result.device.vulns.size()), 
                  OverlayRenderer::COLOR_CRITICAL, 14);
    }
    
    // Credentials count (right side)
    if (!result.device.default_creds.empty()) {
        draw_text(pixels, w, h, w - 120, h - 60, 
                  "CREDS: " + std::to_string(result.device.default_creds.size()), 
                  OverlayRenderer::COLOR_HIGH, 14);
    }
}

void DiagnosticsModule::render_module_status(uint32_t* pixels, int w, int h) {
    int x = w - 120;
    int y = pos_y_;
    
    for (const auto& ms : module_status_) {
        uint32_t color = ms.active ? OverlayRenderer::COLOR_LOW : OverlayRenderer::COLOR_CRITICAL;
        draw_text(pixels, w, h, x, y + 12, 
                  (ms.active ? "[ON] " : "[OFF] ") + ms.name, color, 10);
        y += 15;
    }
}

// Drawing helpers
void DiagnosticsModule::draw_text(uint32_t* pixels, int w, int h,
                                  int x, int y, const std::string& text, uint32_t color, int size) {
    iris::draw::draw_text(pixels, w, h, x, y, text, color, size);
}

void DiagnosticsModule::draw_rect(uint32_t* pixels, int w, int h,
                                  int x, int y, int rw, int rh, uint32_t color) {
    int x0 = std::max(0, x);
    int y0 = std::max(0, y);
    int x1 = std::min(w, x + rw);
    int y1 = std::min(h, y + rh);
    for (int i = x0; i < x1; i++) {
        if (y0 < h) pixels[y0 * w + i] = color;
        if (y1 - 1 >= 0 && y1 - 1 < h) pixels[(y1 - 1) * w + i] = color;
    }
    for (int j = y0; j < y1; j++) {
        if (x0 < w) pixels[j * w + x0] = color;
        if (x1 - 1 >= 0 && x1 - 1 < w) pixels[j * w + (x1 - 1)] = color;
    }
}

void DiagnosticsModule::draw_rect_filled(uint32_t* pixels, int w, int h,
                                         int x, int y, int rw, int rh, uint32_t color) {
    iris::draw::fill_rect(pixels, w, h, x, y, rw, rh, color);
}

void DiagnosticsModule::draw_bounding_box(uint32_t* pixels, int w, int h,
                                          int x, int y, int bw, int bh, uint32_t color, int thickness) {
    for (int t = 0; t < thickness; t++) {
        draw_rect(pixels, w, h, x - t, y - t, bw + 2*t, bh + 2*t, color);
    }
}

} // namespace cornea

