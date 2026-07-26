#pragma once

#include "lexis/types.h"

#include <string>
#include <cstdint>

namespace lexis {

// ── Overlay Renderer ─────────────────────────────────────────────
// Renderiza resultados de análisis en overlay Android + terminal.
class OverlayRenderer {
public:
    OverlayRenderer();
    ~OverlayRenderer();
    
    bool init(int width, int height, const std::string& display_socket, bool verbose);
    void shutdown();
    
    // Terminal output
    void print_document_info(const DocumentInfo& doc);
    
    // Canvas output
    void present(const uint32_t* pixels, int w, int h);
    bool is_connected() const;
    
private:
    // Canvas state
    int canvas_fd_ = -1;
    int width_ = 0;
    int height_ = 0;
    bool verbose_ = false;
};

} // namespace lexis
