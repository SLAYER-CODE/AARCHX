#pragma once

#include "cornea/module.h"

#include <string>
#include <vector>

namespace cornea {

// ── Diagnostics Module ───────────────────────────────────────────
// Muestra información de diagnóstico sobre el overlay de Android:
//   - FPS counter
//   - Texto OCR detectado (con bounding boxes)
//   - Logo detectado
//   - Info del dispositivo reconocido
//   - Estado de módulos
class DiagnosticsModule : public AnalysisModule {
public:
    const char* name() const override { return "diagnostics"; }
    const char* description() const override { return "Real-time diagnostics overlay"; }
    
    bool init() override;
    void shutdown() override;
    void process_frame(uint32_t* pixels, int w, int h, 
                       FrameResult& result) override;
    
    // Overlay configuration
    void set_show_fps(bool en) { show_fps_ = en; }
    void set_show_ocr(bool en) { show_ocr_ = en; }
    void set_show_device(bool en) { show_device_ = en; }
    void set_show_modules(bool en) { show_modules_ = en; }
    void set_position(int x, int y) { pos_x_ = x; pos_y_ = y; }
    
    // Data updates
    void update_fps(int fps) { current_fps_ = fps; }
    void update_module_status(const std::string& name, bool active);
    
private:
    // Drawing helpers (pure C++)
    void draw_text(uint32_t* pixels, int w, int h, int x, int y, 
                   const std::string& text, uint32_t color, int size = 12);
    void draw_rect(uint32_t* pixels, int w, int h,
                   int x, int y, int rw, int rh, uint32_t color);
    void draw_rect_filled(uint32_t* pixels, int w, int h,
                          int x, int y, int rw, int rh, uint32_t color);
    void draw_bounding_box(uint32_t* pixels, int w, int h,
                           int x, int y, int bw, int bh, uint32_t color, int thickness = 2);
    
    // Panel renderers
    void render_fps_panel(uint32_t* pixels, int w, int h);
    void render_ocr_results(uint32_t* pixels, int w, int h, const FrameResult& result);
    void render_visual_detections(uint32_t* pixels, int w, int h, const FrameResult& result);
    void render_device_info(uint32_t* pixels, int w, int h, const FrameResult& result);
    void render_module_status(uint32_t* pixels, int w, int h);
    
    // Config
    bool show_fps_ = true;
    bool show_ocr_ = true;
    bool show_device_ = true;
    bool show_modules_ = true;
    int pos_x_ = 10;
    int pos_y_ = 10;
    
    // Data
    int current_fps_ = 0;
    
    struct ModuleStatus {
        std::string name;
        bool active = false;
    };
    std::vector<ModuleStatus> module_status_;
};

} // namespace cornea

