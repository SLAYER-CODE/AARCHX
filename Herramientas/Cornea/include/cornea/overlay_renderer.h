#pragma once

#include "cornea/types.h"

#include <string>
#include <memory>

// Forward declaration
namespace iris { class Canvas; }

namespace cornea {

// ── Overlay Renderer ─────────────────────────────────────────────
// Renderiza resultados del reconocimiento sobre el frame del overlay.
// Maneja visualización dual: terminal stdout + canvas Android.
class OverlayRenderer {
public:
    OverlayRenderer();
    ~OverlayRenderer();
    
    // Lifecycle
    bool init(int width, int height, const std::string& display_socket, bool verbose = false);
    void shutdown();
    
    // ── Android overlay ──────────────────────────────────────────
    bool connect_overlay();
    bool is_connected() const;
    bool present(const uint32_t* pixels, int w, int h);
    iris::Canvas* canvas() { return canvas_.get(); }
    
    // ── Render size (for fullscreen overlay at higher resolution) ─
    void set_render_size(int w, int h);    // Canvas resizes to w×h, camera scales to fill
    void clear_render_size();              // Restore to camera resolution
    bool has_render_size() const;          // Is canvas at a different resolution than camera?
    int render_width() const;
    int render_height() const;
    
    // ── Terminal output ──────────────────────────────────────────
    void print_analysis(const FrameResult& result);
    void print_vulnerability(const DeviceInfo::Vulnerability& vuln);
    void print_credential(const DeviceInfo::Credential& cred);
    void print_ocr_results(const std::vector<TextBlock>& blocks);
    void print_logo_matches(const std::vector<LogoMatch>& matches);
    void print_header(const std::string& title);
    void print_separator();
    
    // ── Frame annotation ─────────────────────────────────────────
    void annotate_frame(uint32_t* pixels, int w, int h, const FrameResult& result);
    
    // ── Drawing primitives (pure C++) ────────────────────────────
    static void draw_text(uint32_t* pixels, int w, int h,
                          int x, int y, const std::string& text, 
                          uint32_t color, int size = 12);
    static void draw_rect(uint32_t* pixels, int w, int h,
                          int x, int y, int rw, int rh, uint32_t color);
    static void draw_rect_filled(uint32_t* pixels, int w, int h,
                                 int x, int y, int rw, int rh, uint32_t color);
    static void draw_bounding_box(uint32_t* pixels, int w, int h,
                                  int x, int y, int bw, int bh, 
                                  uint32_t color, int thickness = 2);
    
    // ── Color constants ──────────────────────────────────────────
    static constexpr uint32_t COLOR_CRITICAL = 0xFF0000FF;  // Red
    static constexpr uint32_t COLOR_HIGH     = 0xFF0080FF;  // Orange
    static constexpr uint32_t COLOR_MEDIUM   = 0xFF00FFFF;  // Yellow
    static constexpr uint32_t COLOR_LOW      = 0xFF00FF00;  // Green
    static constexpr uint32_t COLOR_INFO     = 0xFFFF8000;  // Blue
    static constexpr uint32_t COLOR_TEXT     = 0xFFFFFFFF;  // White
    static constexpr uint32_t COLOR_OCR      = 0xFF00FF00;  // Green (OCR boxes)
    static constexpr uint32_t COLOR_LOGO     = 0xFFFF8000;  // Blue (logo boxes)
    static constexpr uint32_t COLOR_BG       = 0xCC000000;  // Semi-transparent black
    
    // Stats
    int frames_rendered() const { return frames_rendered_; }
    
private:
    // State
    std::unique_ptr<iris::Canvas> canvas_;
    std::string display_socket_;
    int width_ = 0;
    int height_ = 0;
    bool connected_ = false;
    bool verbose_ = false;
    int frames_rendered_ = 0;
    int render_w_ = 0;  // 0 = use camera resolution
    int render_h_ = 0;
    
    // Font data (minimal 8x12 bitmap font)
    static void draw_char(uint32_t* pixels, int w, int h,
                          int x, int y, char c, uint32_t color);
};

} // namespace cornea

