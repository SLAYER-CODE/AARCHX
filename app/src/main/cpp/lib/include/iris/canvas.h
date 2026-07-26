#pragma once

#ifdef MANDELA_USE_SKIA
#include <SkCanvas.h>
#include <SkFont.h>
#include <SkImageInfo.h>
#include <SkPaint.h>
#include <SkRect.h>
#include <SkSurface.h>
#include <SkTextUtils.h>
#endif

#include "iris/types.h"

#include <cstdint>
#include <cstdio>
#include <string>
#include <vector>

namespace iris {

// ── Canvas class (socket overlay + pixel buffer + drawing) ──────
// Herramienta nativa → CanvasSocketServer.kt (Android overlay)
class Canvas {
public:
    Canvas();
    virtual ~Canvas();

    bool init(int width, int height);
    void load_frame(const uint8_t* bgra_data, size_t size);

    // Drawing (Skia si disponible, software fallback si no)
    void clear(uint32_t color = 0xFF000000);
    void fill_rect(int x, int y, int w, int h, uint32_t color);
    void draw_line(int x1, int y1, int x2, int y2, uint32_t color, int width = 1);
    void draw_circle(int cx, int cy, int radius, uint32_t color, bool fill = true);
    void draw_text(int x, int y, const std::string& text, uint32_t color, int size = 12);

    // Socket overlay (canvas-display)
    bool connect_overlay(const std::string& socket_name = "canvas-display");
    bool present();
    bool poll_commands();
    bool resize(int width, int height);

    // Accesores
    int width() const { return width_; }
    int height() const { return height_; }
    uint32_t* pixels() { return pixels_.data(); }
    const uint32_t* pixels() const { return pixels_.data(); }
    bool connected() const { return socket_fd_ >= 0; }
    int socket_fd() const { return socket_fd_; }

    void set_overlay_scale(float s) { overlay_scale_ = s; }
    float overlay_scale() const { return overlay_scale_; }

protected:
    int width_ = 0;
    int height_ = 0;
    float overlay_scale_ = 1.0f;
    int socket_fd_ = -1;
    std::string socket_name_;
    std::vector<uint32_t> pixels_;
    int frame_id_ = 0;

#ifdef MANDELA_USE_SKIA
    sk_sp<SkSurface> sk_surface_;
    SkCanvas* sk_canvas_ = nullptr;
    void rebuild_skia_surface();
#endif
};

// ── Standalone drawing helpers (sin Canvas, operan sobre raw pixel buffer) ──
// Útil para herramientas como Mandela que tienen su propio buffer de píxeles.
namespace draw {
    void clear(uint32_t* pixels, int w, int h, uint32_t color);
    void fill_rect(uint32_t* pixels, int w, int h, int x, int y, int rw, int rh, uint32_t color);
    void draw_line(uint32_t* pixels, int w, int h, int x1, int y1, int x2, int y2, uint32_t color, int line_width = 1);
    void draw_circle(uint32_t* pixels, int w, int h, int cx, int cy, int radius, uint32_t color, bool fill = true);
    void draw_text(uint32_t* pixels, int w, int h, int x, int y, const std::string& text, uint32_t color, int size = 12);
}

// ── Standalone socket present (envía frame por socket sin usar Canvas class) ──
// Útil para herramientas que tienen su propio pixel buffer y socket fd.
// socket_fd: connected AF_UNIX SOCK_STREAM
// pixels: buffer BGRA de w×h
bool present_overlay(int socket_fd, const uint32_t* pixels, int w, int h, float overlay_scale = 1.0f);

} // namespace iris
