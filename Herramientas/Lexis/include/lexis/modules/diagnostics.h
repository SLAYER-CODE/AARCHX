#pragma once

#include "lexis/module.h"

#include <string>
#include <vector>
#include <map>

namespace lexis {

// ── Diagnostics Module ──────────────────────────────────────────
// Muestra información de diagnóstico en el overlay:
//   - FPS counter
//   - Estado de módulos
//   - Documento detectado
class DiagnosticsModule : public AnalysisModule {
public:
    DiagnosticsModule();
    ~DiagnosticsModule();
    
    const char* name() const override { return "diagnostics"; }
    const char* description() const override { return "Diagnostic overlay panels"; }
    
    bool init() override;
    void shutdown() override;
    void process_frame(uint32_t* pixels, int w, int h, 
                       FrameResult& result) override;
    
    // Update stats (called by engine)
    void update_fps(int fps);
    void update_module_status(const std::string& module, bool enabled);
    
private:
    // Drawing helpers
    void draw_text(uint32_t* pixels, int w, int h, int x, int y, 
                   const std::string& text, uint32_t color);
    void draw_rect(uint32_t* pixels, int w, int h, int x, int y, 
                   int width, int height, uint32_t color);
    void draw_rect_filled(uint32_t* pixels, int w, int h, int x, int y, 
                          int width, int height, uint32_t color);
    
    // State
    int current_fps_ = 0;
    std::map<std::string, bool> module_status_;
};

} // namespace lexis
