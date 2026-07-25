#include "cornea/overlay_renderer.h"
#include "iris/canvas.h"

#include <iostream>
#include <iomanip>
#include <sstream>
#include <thread>
#include <chrono>

namespace cornea {

OverlayRenderer::OverlayRenderer() {}

OverlayRenderer::~OverlayRenderer() {
    shutdown();
}

bool OverlayRenderer::init(int width, int height, const std::string& display_socket, bool verbose) {
    width_ = width;
    height_ = height;
    display_socket_ = display_socket;
    verbose_ = verbose;
    
    canvas_ = std::make_unique<iris::Canvas>();
    
    // Connect to display socket (retry until available)
    while (!canvas_->connect_overlay(display_socket)) {
        std::this_thread::sleep_for(std::chrono::seconds(1));
    }
    
    if (!canvas_->init(width, height)) {
        std::cerr << "[Overlay] Canvas init failed" << std::endl;
        return false;
    }
    
    connected_ = true;
    std::cout << "[Overlay] Connected to '" << display_socket << "' " << width << "x" << height << std::endl;
    return true;
}

void OverlayRenderer::shutdown() {
    connected_ = false;
    canvas_.reset();
}

bool OverlayRenderer::connect_overlay() {
    if (!canvas_) return false;
    return canvas_->connect_overlay(display_socket_);
}

bool OverlayRenderer::is_connected() const {
    return connected_;
}

bool OverlayRenderer::present(const uint32_t* pixels, int w, int h) {
    if (!canvas_ || !connected_) return false;
    if (!pixels || w <= 0 || h <= 0) return false;
    
    // Reinit canvas if frame dimensions changed (like Iris does)
    if (w != width_ || h != height_) {
        if (verbose_) {
            std::cout << "[Overlay] Reinit: " << width_ << "x" << height_ 
                      << " → " << w << "x" << h << std::endl;
        }
        width_ = w;
        height_ = h;
        if (!canvas_->init(w, h)) {
            std::cerr << "[Overlay] Canvas reinit failed" << std::endl;
            return false;
        }
    }
    
    canvas_->load_frame(
        reinterpret_cast<const uint8_t*>(pixels),
        static_cast<size_t>(w) * h * 4
    );
    
    if (!canvas_->present()) {
        std::cerr << "[Overlay] present() failed" << std::endl;
        connected_ = false;
        return false;
    }
    
    frames_rendered_++;
    return true;
}

void OverlayRenderer::print_device_info(const DeviceInfo& device) {
    print_separator();
    print_header("DEVICE RECOGNIZED");
    print_separator();
    
    // Device Type (most important)
    if (!device.type_label.empty()) {
        std::cout << "Type: " << device.type_label;
        if (device.classification_confidence > 0) {
            std::cout << " (" << (int)(device.classification_confidence * 100) << "% confidence)";
        }
        std::cout << std::endl;
    }
    
    // Device State
    if (!device.state_label.empty()) {
        std::cout << "State: " << device.state_label << std::endl;
    }
    
    if (!device.vendor.empty()) {
        std::cout << "Vendor: " << device.vendor;
        if (device.logo_confidence > 0) {
            std::cout << " (" << (int)(device.logo_confidence * 100) << "% confidence)";
        }
        std::cout << std::endl;
    }
    
    if (!device.model.empty()) {
        std::cout << "Model: " << device.model << std::endl;
    }
    
    if (!device.serial.empty()) {
        std::cout << "Serial: " << device.serial << std::endl;
    }
    
    if (!device.firmware.empty()) {
        std::cout << "Firmware: " << device.firmware << std::endl;
    }
    
    if (!device.text_blocks.empty()) {
        print_ocr_results(device.text_blocks);
    }
    
    if (!device.vulns.empty()) {
        std::cout << std::endl;
        print_header("VULNERABILITIES");
        for (const auto& vuln : device.vulns) {
            print_vulnerability(vuln);
        }
    }
    
    if (!device.default_creds.empty()) {
        std::cout << std::endl;
        print_header("DEFAULT CREDENTIALS");
        for (const auto& cred : device.default_creds) {
            print_credential(cred);
        }
    }
    
    print_separator();
}

void OverlayRenderer::print_vulnerability(const DeviceInfo::Vulnerability& vuln) {
    std::cout << "  [" << vuln.severity << "] " << vuln.title << std::endl;
    if (!vuln.id.empty()) {
        std::cout << "    ID: " << vuln.id << std::endl;
    }
    if (!vuln.detail.empty()) {
        std::cout << "    " << vuln.detail << std::endl;
    }
    if (!vuln.solution.empty()) {
        std::cout << "    Fix: " << vuln.solution << std::endl;
    }
}

void OverlayRenderer::print_credential(const DeviceInfo::Credential& cred) {
    std::cout << "  [" << cred.service << "] " 
              << cred.username << ":" << cred.password 
              << " (" << cred.source << ")" << std::endl;
}

void OverlayRenderer::print_ocr_results(const std::vector<TextBlock>& blocks) {
    std::cout << std::endl;
    print_header("OCR RESULTS");
    for (const auto& block : blocks) {
        std::cout << "  \"" << block.text << "\"";
        std::cout << " (" << (int)(block.confidence * 100) << "%)";
        std::cout << " @ [" << block.x << "," << block.y << "]";
        std::cout << std::endl;
    }
}

void OverlayRenderer::print_logo_matches(const std::vector<LogoMatch>& matches) {
    std::cout << std::endl;
    print_header("LOGO MATCHES");
    for (const auto& match : matches) {
        std::cout << "  " << match.vendor;
        std::cout << " (" << (int)(match.confidence * 100) << "%)";
        std::cout << " @ [" << match.x << "," << match.y << "]";
        std::cout << std::endl;
    }
}

void OverlayRenderer::print_header(const std::string& title) {
    std::cout << "=== " << title << " ===" << std::endl;
}

void OverlayRenderer::print_separator() {
    std::cout << "--------------------------------------------" << std::endl;
}

void OverlayRenderer::annotate_frame(uint32_t* pixels, int w, int h, const FrameResult& result) {
    if (!result.valid) return;
    
    // Draw OCR bounding boxes
    for (const auto& block : result.device.text_blocks) {
        draw_bounding_box(pixels, w, h, block.x, block.y, 
                          block.width, block.height, COLOR_OCR, 2);
    }
    
    // Draw logo bounding boxes
    for (const auto& match : result.device.logo_matches) {
        draw_bounding_box(pixels, w, h, match.x, match.y, 
                          match.width, match.height, COLOR_LOGO, 3);
    }
    
    // Draw device info overlay at bottom
    int panel_h = 60;
    int panel_y = h - panel_h;
    draw_rect_filled(pixels, w, h, 0, panel_y, w, panel_h, COLOR_BG);
    
    int y = panel_y + 10;
    if (!result.device.vendor.empty()) {
        draw_text(pixels, w, h, 10, y, result.device.vendor, COLOR_TEXT, 14);
        y += 18;
    }
    if (!result.device.model.empty()) {
        draw_text(pixels, w, h, 10, y, result.device.model, COLOR_TEXT, 14);
    }
}

// Drawing primitives
void OverlayRenderer::draw_text(uint32_t* pixels, int w, int h,
                                int x, int y, const std::string& text, uint32_t color, int size) {
    iris::draw::draw_text(pixels, w, h, x, y, text, color, size);
}

void OverlayRenderer::draw_rect(uint32_t* pixels, int w, int h,
                                int x, int y, int rw, int rh, uint32_t color) {
    // Horizontal lines
    for (int i = x; i < x + rw && i < w; i++) {
        if (y >= 0 && y < h) pixels[y * w + i] = color;
        if (y + rh - 1 >= 0 && y + rh - 1 < h) pixels[(y + rh - 1) * w + i] = color;
    }
    // Vertical lines
    for (int j = y; j < y + rh && j < h; j++) {
        if (x >= 0 && x < w) pixels[j * w + x] = color;
        if (x + rw - 1 >= 0 && x + rw - 1 < w) pixels[j * w + (x + rw - 1)] = color;
    }
}

void OverlayRenderer::draw_rect_filled(uint32_t* pixels, int w, int h,
                                       int x, int y, int rw, int rh, uint32_t color) {
    iris::draw::fill_rect(pixels, w, h, x, y, rw, rh, color);
}

void OverlayRenderer::draw_bounding_box(uint32_t* pixels, int w, int h,
                                        int x, int y, int bw, int bh, 
                                        uint32_t color, int thickness) {
    for (int t = 0; t < thickness; t++) {
        draw_rect(pixels, w, h, x - t, y - t, bw + 2*t, bh + 2*t, color);
    }
}

} // namespace cornea

