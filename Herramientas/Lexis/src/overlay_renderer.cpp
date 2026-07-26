#include "lexis/overlay_renderer.h"
#include "iris/canvas.h"

#include <iostream>
#include <sstream>
#include <iomanip>
#include <cstring>

#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>

namespace lexis {

OverlayRenderer::OverlayRenderer() {}

OverlayRenderer::~OverlayRenderer() {
    shutdown();
}

bool OverlayRenderer::init(int width, int height, const std::string& display_socket, bool verbose) {
    width_ = width;
    height_ = height;
    verbose_ = verbose;
    
    // Connect to display socket
    canvas_fd_ = socket(AF_UNIX, SOCK_STREAM, 0);
    if (canvas_fd_ < 0) {
        std::cerr << "[OverlayRenderer] Failed to create socket" << std::endl;
        return false;
    }
    
    struct sockaddr_un addr;
    memset(&addr, 0, sizeof(addr));
    addr.sun_family = AF_UNIX;
    strcpy(addr.sun_path + 1, display_socket.c_str());
    
    socklen_t len = offsetof(struct sockaddr_un, sun_path) + 1 + display_socket.length();
    
    if (connect(canvas_fd_, (struct sockaddr*)&addr, len) < 0) {
        std::cerr << "[OverlayRenderer] Failed to connect to " << display_socket << std::endl;
        close(canvas_fd_);
        canvas_fd_ = -1;
        return false;
    }
    
    std::cout << "[OverlayRenderer] Connected to " << display_socket << std::endl;
    return true;
}

void OverlayRenderer::shutdown() {
    if (canvas_fd_ >= 0) {
        close(canvas_fd_);
        canvas_fd_ = -1;
    }
    std::cout << "[OverlayRenderer] Shutdown" << std::endl;
}

void OverlayRenderer::print_document_info(const DocumentInfo& doc) {
    std::cout << "\n=== Document Analysis ===" << std::endl;
    
    // Document type
    std::cout << "Type: " << doc.type_label << std::endl;
    
    // Confidence
    std::cout << std::fixed << std::setprecision(1);
    std::cout << "OCR Confidence: " << (doc.ocr_confidence * 100) << "%" << std::endl;
    std::cout << "Classification Confidence: " << (doc.classification_confidence * 100) << "%" << std::endl;
    
    // Stats
    std::cout << "Lines: " << doc.line_count << std::endl;
    std::cout << "Words: " << doc.word_count << std::endl;
    std::cout << "Characters: " << doc.char_count << std::endl;
    
    // Fields
    if (!doc.fields.empty()) {
        std::cout << "\nExtracted Fields:" << std::endl;
        for (const auto& field : doc.fields) {
            std::cout << "  " << field.key << ": " << field.value 
                      << " (" << (field.confidence * 100) << "%)" << std::endl;
        }
    }
    
    // Full text (truncated)
    if (!doc.full_text.empty()) {
        std::cout << "\nFull Text:" << std::endl;
        std::string text = doc.full_text;
        if (text.length() > 500) {
            text = text.substr(0, 500) + "...";
        }
        std::cout << text << std::endl;
    }
    
    std::cout << "========================\n" << std::endl;
}

void OverlayRenderer::present(const uint32_t* pixels, int w, int h) {
    if (canvas_fd_ < 0 || !pixels) return;
    
    // Send frame using Iris protocol
    iris::present_overlay(canvas_fd_, pixels, w, h, 1.0f);
}

bool OverlayRenderer::is_connected() const {
    return canvas_fd_ >= 0;
}

} // namespace lexis
