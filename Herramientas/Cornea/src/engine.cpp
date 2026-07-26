#include "cornea/engine.h"
#include "cornea/overlay_renderer.h"
#include "cornea/modules/ocr.h"
#include "cornea/modules/logo_detector.h"
#include "cornea/modules/vulndb.h"
#include "cornea/modules/diagnostics.h"
#include "cornea/modules/device_classifier.h"
#include "cornea/modules/visual_detector.h"
#include "iris/canvas.h"

#include <iostream>
#include <thread>
#include <chrono>
#include <cstring>
#include <algorithm>
#include <cmath>
#include <vector>

#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>

namespace cornea {

Engine::Engine() {}

Engine::~Engine() {
    stop();
}

bool Engine::init(const EngineConfig& config) {
    config_ = config;
    
    std::cout << "[Engine] Initialized" << std::endl;
    std::cout << "  Camera: " << config_.camera_socket << std::endl;
    std::cout << "  Size: " << config_.width << "x" << config_.height << std::endl;
    std::cout << "  Overlay: " << (config_.overlay_enabled ? "ON" : "OFF") << std::endl;
    
    return true;
}

void Engine::start() {
    if (running_) return;

    // Init all modules
    for (auto& mod : modules_) {
        if (mod->name() == std::string("ocr")) {
            auto* ocr = dynamic_cast<OCRModule*>(mod.get());
            if (ocr) {
                ocr->set_data_path(config_.tesseract_data);
                std::cout << "[Engine] OCR data path: " << config_.tesseract_data << std::endl;
            }
        }
        if (!mod->init()) {
            std::cerr << "[Engine] Module '" << mod->name() << "' init failed" << std::endl;
        }
    }

    // Populate diagnostics module status
    auto* diag = get_module("diagnostics");
    if (diag) {
        auto* diagnostics = static_cast<DiagnosticsModule*>(diag);
        for (auto& mod : modules_) {
            diagnostics->update_module_status(mod->name(), mod->enabled());
        }
    }

    running_ = true;
    process_thread_ = std::thread(&Engine::process_loop, this);

    std::cout << "[Engine] Started" << std::endl;
}

void Engine::stop() {
    if (!running_) return;
    
    running_ = false;
    
    // Wait for async OCR to finish
    if (ocr_future_.valid()) {
        ocr_future_.wait();
    }
    
    if (process_thread_.joinable()) {
        process_thread_.join();
    }
    
    std::cout << "[Engine] Stopped" << std::endl;
}

void Engine::trigger_process() {
    process_pending_ = true;
}

FrameResult Engine::last_result() const {
    std::lock_guard<std::mutex> lock(result_mutex_);
    return last_result_;
}

void Engine::register_module(std::unique_ptr<AnalysisModule> module) {
    if (module) {
        std::cout << "[Engine] Registered module: " << module->name() << std::endl;
        modules_.push_back(std::move(module));
    }
}

void Engine::enable_module(const std::string& name) {
    auto* mod = get_module(name);
    if (mod) mod->set_enabled(true);
}

void Engine::disable_module(const std::string& name) {
    auto* mod = get_module(name);
    if (mod) mod->set_enabled(false);
}

AnalysisModule* Engine::get_module(const std::string& name) {
    for (auto& mod : modules_) {
        if (mod->name() == name) {
            return mod.get();
        }
    }
    return nullptr;
}

// ── Main loop ──────────────────────────────────────────────────
void Engine::process_loop() {
    auto last_fps_time = std::chrono::steady_clock::now();
    int frame_count = 0;

    if (!camera_canvas_.listen(config_.camera_socket)) {
        std::cerr << "[Engine] Failed to listen on " << config_.camera_socket << std::endl;
        return;
    }
    std::cout << "[Engine] Listening on " << config_.camera_socket 
              << " (" << config_.width << "x" << config_.height << ")" << std::endl;

    send_camera_ctrl();

    while (running_) {
        if (!camera_canvas_.connected()) {
            if (config_.verbose)
                std::cout << "[Engine] Waiting for camera client..." << std::endl;
            if (!camera_canvas_.accept_client()) {
                std::this_thread::sleep_for(std::chrono::milliseconds(500));
                continue;
            }
            std::cout << "[Engine] Camera client connected" << std::endl;
        }

        if (camera_canvas_.recv_frame()) {
            int fw = camera_canvas_.width();
            int fh = camera_canvas_.height();

            if (config_.rotate != 0) {
                camera_canvas_.rotate(config_.rotate);
                fw = camera_canvas_.width();
                fh = camera_canvas_.height();
            }

            uint32_t* pixels = camera_canvas_.pixels();
            
            // ── Step 1: Collect async results if ready ──
            bool async_ready = false;
            if (ocr_future_.valid() && 
                ocr_future_.wait_for(std::chrono::seconds(0)) == std::future_status::ready) {
                FrameResult ocr_result = ocr_future_.get();
                async_ready = true;
                
                {
                    std::lock_guard<std::mutex> lock(result_mutex_);
                    last_result_.device.text_blocks = ocr_result.device.text_blocks;
                    last_result_.device.ocr_confidence = ocr_result.device.ocr_confidence;
                    last_result_.device.model = ocr_result.device.model;
                    last_result_.device.serial = ocr_result.device.serial;
                    last_result_.device.firmware = ocr_result.device.firmware;
                    last_result_.device.vulns = ocr_result.device.vulns;
                    last_result_.device.default_creds = ocr_result.device.default_creds;
                    if (!ocr_result.device.vendor.empty())
                        last_result_.device.vendor = ocr_result.device.vendor;
                    
                    // Device classification results
                    if (ocr_result.device.type != DeviceType::UNKNOWN) {
                        last_result_.device.type = ocr_result.device.type;
                        last_result_.device.type_label = ocr_result.device.type_label;
                    }
                    if (ocr_result.device.state != DeviceState::UNKNOWN) {
                        last_result_.device.state = ocr_result.device.state;
                        last_result_.device.state_label = ocr_result.device.state_label;
                    }
                    last_result_.device.classification_confidence = ocr_result.device.classification_confidence;
                    
                    // Visual detection results — always update (including clearing)
                    last_result_.visual_detections = ocr_result.visual_detections;
                    
                    if (!ocr_result.visual_detections.empty()) {
                        std::cout << "[Engine] Async visual_detections: " << ocr_result.visual_detections.size() << " items" << std::endl;
                    }
                    
                    last_result_.valid = !last_result_.device.vendor.empty() ||
                                         !last_result_.device.model.empty() ||
                                         !last_result_.device.text_blocks.empty() ||
                                         last_result_.device.type != DeviceType::UNKNOWN ||
                                         !last_result_.visual_detections.empty();
                }
                
                if (frame_callback_) {
                    frame_callback_(last_result());
                }
            }
            
            // ── Step 2: Auto-resize canvas to match camera if at default size ──
            // When the camera sends a different aspect ratio (e.g. 480x640 portrait
            // vs 640x480 landscape default), resize the canvas to match so the image
            // is not distorted. Only when canvas is at the configured default size —
            // if it was resized by a "resize" command (fullscreen), keep that size.
            if (overlay_canvas_ &&
                overlay_canvas_->width() == config_.overlay_width &&
                overlay_canvas_->height() == config_.overlay_height &&
                (overlay_canvas_->width() != fw || overlay_canvas_->height() != fh)) {
                std::cout << "[Engine] Auto-resize canvas "
                          << overlay_canvas_->width() << "x" << overlay_canvas_->height()
                          << " -> " << fw << "x" << fh << " (match camera)" << std::endl;
                overlay_canvas_->resize(fw, fh);
            }

            // ── Step 2b: Determine render target ──
            uint32_t* diag_pixels;
            int diag_w, diag_h;
            bool render_mode = overlay_canvas_ &&
                (overlay_canvas_->width() != fw || overlay_canvas_->height() != fh);

            if (render_mode) {
                // Render mode: scale camera → canvas, draw diagnostics on canvas
                diag_pixels = overlay_canvas_->pixels();
                diag_w = overlay_canvas_->width();
                diag_h = overlay_canvas_->height();
                for (int dy = 0; dy < diag_h; dy++) {
                    int sy = dy * fh / diag_h;
                    for (int dx = 0; dx < diag_w; dx++) {
                        diag_pixels[dy * diag_w + dx] = pixels[sy * fw + dx * fw / diag_w];
                    }
                }
            } else {
                // Non-render: canvas must match camera dimensions
                if (overlay_canvas_ &&
                    (overlay_canvas_->width() != fw || overlay_canvas_->height() != fh)) {
                    overlay_canvas_->resize(fw, fh);
                }
                diag_pixels = pixels;
                diag_w = fw;
                diag_h = fh;
            }
            
            // ── Step 3: DiagnosticsModule inline (fast, draws green+blue boxes) ──
            {
                std::lock_guard<std::mutex> lock(result_mutex_);
                FrameResult diag_result;
                diag_result.timestamp = std::chrono::system_clock::to_time_t(
                    std::chrono::system_clock::now());
                diag_result.device = last_result_.device;
                diag_result.valid = last_result_.valid;
                diag_result.visual_detections = last_result_.visual_detections;
                
                auto* diag = get_module("diagnostics");
                if (diag && diag->enabled()) {
                    diag->process_frame(diag_pixels, diag_w, diag_h, diag_result);
                }
                
                // Debug: log state every 60 frames (~2s at 30fps)
                if (frame_count % 60 == 0) {
                    std::cout << "[Engine] frame=" << frame_count
                              << " async=" << (async_ready ? "ready" : "pending")
                              << " text_blocks=" << last_result_.device.text_blocks.size()
                              << " visual_det=" << last_result_.visual_detections.size()
                              << " valid=" << last_result_.valid
                              << " overlay=" << (raw_frame_callback_ ? "yes" : "no")
                              << (render_mode ? " render=upscaled" : "")
                              << std::endl;
                }
            }
            
            // ── Step 4: Present frame (engine handles all paths) ──
            if (overlay_canvas_) {
                if (render_mode) {
                    // Canvas already has the annotated scaled pixels
                    overlay_canvas_->present();
                } else {
                    // Non-render: load camera pixels into canvas and present
                    overlay_canvas_->load_frame(
                        reinterpret_cast<const uint8_t*>(pixels),
                        static_cast<size_t>(fw) * fh * 4
                    );
                    overlay_canvas_->present();
                }
            } else if (raw_frame_callback_) {
                raw_frame_callback_(pixels, fw, fh);
            }
            
            // ── Step 5: Poll commands from Android (resize, etc.) ──
            if (overlay_canvas_ && overlay_canvas_->poll_commands()) {
                // Canvas was resized — update renderer's render size
                auto* renderer = static_cast<OverlayRenderer*>(overlay_renderer_);
                if (renderer) {
                    renderer->set_render_size(overlay_canvas_->width(), overlay_canvas_->height());
                }
            }
            
            // ── Step 4: Launch async if previous done ──
            if (!ocr_future_.valid() || 
                ocr_future_.wait_for(std::chrono::seconds(0)) == std::future_status::ready) {
                
                std::vector<uint32_t> pixels_copy(pixels, pixels + fw * fh);
                
                ocr_future_ = std::async(std::launch::async, 
                    [this, copy = std::move(pixels_copy), w = fw, h = fh]() -> FrameResult {
                        return this->process_frame_async(copy.data(), w, h);
                    });
            }
            
            frame_count++;
        } else if (camera_canvas_.connected()) {
            std::this_thread::sleep_for(std::chrono::milliseconds(10));
        }
        
        // Calculate FPS
        auto now = std::chrono::steady_clock::now();
        auto elapsed = std::chrono::duration_cast<std::chrono::seconds>(
            now - last_fps_time).count();
        if (elapsed >= 1) {
            current_fps_ = frame_count;
            frame_count = 0;
            last_fps_time = now;

            auto* diag = get_module("diagnostics");
            if (diag) static_cast<DiagnosticsModule*>(diag)->update_fps(current_fps_);
        }
    }
}

// ── Async: OCR + VulnDB + DeviceClassifier (runs in background thread) ────
FrameResult Engine::process_frame_async(const uint32_t* pixels, int w, int h) {
    FrameResult result;
    result.timestamp = std::chrono::system_clock::to_time_t(
        std::chrono::system_clock::now());
    
    // Copy pixels for modules that need non-const (OCR, VulnDB)
    std::vector<uint32_t> buf(pixels, pixels + w * h);
    
    // Run OCR (the heavy one)
    auto* ocr = get_module("ocr");
    if (ocr && ocr->enabled()) {
        ocr->process_frame(buf.data(), w, h, result);
    }
    
    // Run VulnDB (needs OCR results, fast SQLite lookup)
    if (result.valid) {
        auto* vulndb = get_module("vulndb");
        if (vulndb && vulndb->enabled()) {
            vulndb->process_frame(buf.data(), w, h, result);
        }
    }
    
    // Run DeviceClassifier (always runs, uses visual analysis when no OCR text)
    {
        auto* classifier = get_module("device_classifier");
        if (classifier && classifier->enabled()) {
            classifier->process_frame(buf.data(), w, h, result);
        }
    }
    
    // Run VisualDetector (YOLO object detection)
    {
        auto* visual = get_module("visual_detector");
        if (visual && visual->enabled()) {
            visual->process_frame(buf.data(), w, h, result);
        }
    }
    
    return result;
}

void Engine::send_camera_ctrl() {
    int fd = socket(AF_UNIX, SOCK_STREAM, 0);
    if (fd < 0) return;

    struct sockaddr_un addr;
    memset(&addr, 0, sizeof(addr));
    addr.sun_family = AF_UNIX;
    strcpy(addr.sun_path + 1, "cam-ctrl");

    socklen_t len = offsetof(struct sockaddr_un, sun_path) + 1 + 8;
    if (connect(fd, (struct sockaddr*)&addr, len) < 0) {
        close(fd);
        return;
    }

    std::string cmds = "camera " + config_.camera_socket.substr(4) + "\n";
    cmds += "size " + std::to_string(config_.width) + "x" + std::to_string(config_.height) + "\n";
    write(fd, cmds.data(), cmds.size());

    shutdown(fd, SHUT_WR);

    char resp[128] = {};
    ssize_t nread = read(fd, resp, sizeof(resp) - 1);
    close(fd);

    if (nread > 0) {
        resp[nread] = '\0';
        const char* prefix = "sensor_orientation=";
        const char* p = strstr(resp, prefix);
        if (p) {
            config_.sensor_orientation = atoi(p + strlen(prefix));
            if (config_.rotate == 0 && config_.sensor_orientation != 0) {
                config_.rotate = config_.sensor_orientation;
            }
            if (config_.verbose)
                std::cout << "[Engine] sensor_orientation=" << config_.sensor_orientation << std::endl;
        }
    }
}

void Engine::request_resolution(int width, int height) {
    if (width <= 0 || height <= 0) return;
    std::cout << "[Engine] Requesting resolution: " << width << "x" << height << std::endl;
    config_.width = width;
    config_.height = height;
    send_camera_ctrl();
}

} // namespace cornea
