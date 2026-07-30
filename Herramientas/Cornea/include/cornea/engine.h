#pragma once

#include "cornea/types.h"
#include "cornea/module.h"
#include "cornea/tracker.h"
#include "iris/camera_canvas.h"

#include <memory>
#include <vector>
#include <string>
#include <atomic>
#include <thread>
#include <mutex>
#include <future>
#include <functional>

namespace iris { class Canvas; }

namespace cornea {

// ── Engine configuration ─────────────────────────────────────────
struct EngineConfig {
    // Camera source
    std::string camera_socket = "cam-0";  // Socket de cámara Android
    int width = 640;                       // Resolución deseada
    int height = 480;
    int rotate = 0;                        // Rotación del frame (grados)
    int sensor_orientation = 0;            // Orientación del sensor (desde Android)
    
    // Processing
    int process_interval_ms = 0;           // Intervalo entre procesamiento (0 = no wait)
    bool continuous_scan = true;           // Escaneo continuo vs manual
    
    // Modules to enable
    std::vector<std::string> modules = {
        "ocr", "logo_detector", "vulndb", "diagnostics",
        "visual_detector", "device_classifier"
    };
    
    // Visualization
    bool overlay_enabled = true;           // Android overlay
    bool terminal_enabled = true;          // stdout output
    std::string display_socket = "canvas-display";
    int overlay_width = 640;
    int overlay_height = 480;
    
    // Paths
    std::string tesseract_data = "/root/cornea/tessdata";
    std::string template_dir = "/data/local/aarchdroid/root/cornea/templates";
    std::string db_path = "/data/local/aarchdroid/root/cornea/cornea.db";
    
    // Verbose
    bool verbose = false;

    // ANSI color output
    bool ansi = true;

    // Tracker mode: "kalman" or "iou"
    std::string tracker_mode = "kalman";

    // Frame throttling (0 = no limit)
    int max_fps = 0;
    int process_every = 1;
};

// ── Frame callbacks ──────────────────────────────────────────────
using FrameCallback = std::function<void(const FrameResult&)>;
using RawFrameCallback = std::function<void(const uint32_t*, int, int)>;

// ── Engine ───────────────────────────────────────────────────────
class Engine {
public:
    Engine();
    ~Engine();
    
    // Lifecycle
    bool init(const EngineConfig& config);
    void start();
    void stop();
    bool is_running() const { return running_.load(); }
    
    // Manual trigger
    void trigger_process();
    
    // Runtime resolution change (re-sends cam-ctrl with new size)
    void request_resolution(int width, int height);
    
    // Overlay canvas (for render-size aware pipeline)
    void set_overlay_canvas(iris::Canvas* c) { overlay_canvas_ = c; }
    void set_overlay_renderer(void* r) { overlay_renderer_ = r; }
    
    // Results
    FrameResult last_result() const;
    
    // Module management
    void register_module(std::unique_ptr<AnalysisModule> module);
    void enable_module(const std::string& name);
    void disable_module(const std::string& name);
    
    // Callbacks
    void on_frame_processed(FrameCallback cb) { frame_callback_ = cb; }
    void on_frame_raw(RawFrameCallback cb) { raw_frame_callback_ = cb; }
    
    // Stats
    int frames_processed() const { return frames_processed_; }
    int fps() const { return current_fps_; }
    
private:
    // Processing loop
    void process_loop();
    FrameResult process_frame_async(const uint32_t* pixels, int w, int h);
    
    // Camera handling
    void send_camera_ctrl();
    
    // Modules
    std::vector<std::unique_ptr<AnalysisModule>> modules_;
    AnalysisModule* get_module(const std::string& name);
    
    // State
    std::atomic<bool> running_{false};
    std::atomic<bool> process_pending_{false};
    std::thread process_thread_;
    EngineConfig config_;
    
    // Thread-safe result storage
    mutable std::mutex result_mutex_;
    FrameResult last_result_;
    
    // Async OCR pipeline
    std::future<FrameResult> ocr_future_;
    
    FrameCallback frame_callback_;
    RawFrameCallback raw_frame_callback_;
    int frames_processed_ = 0;
    int current_fps_ = 0;
    
    // Camera (listen mode, like Iris)
    iris::CameraCanvas camera_canvas_;
    
    // Overlay canvas pointer (owned by OverlayRenderer, not Engine)
    iris::Canvas* overlay_canvas_ = nullptr;
    void* overlay_renderer_ = nullptr;  // OverlayRenderer*, avoid include

    // Tracker (inline, frame-to-frame)
    Tracker tracker_;
};

} // namespace cornea
