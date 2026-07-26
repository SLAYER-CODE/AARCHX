#include "lexis/engine.h"
#include "lexis/modules/ocr.h"
#include "lexis/modules/document_analyzer.h"
#include "lexis/modules/diagnostics.h"

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

namespace lexis {

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
            
            // ── Step 1: Collect async OCR results if ready ──
            if (ocr_future_.valid() && 
                ocr_future_.wait_for(std::chrono::seconds(0)) == std::future_status::ready) {
                FrameResult ocr_result = ocr_future_.get();
                
                {
                    std::lock_guard<std::mutex> lock(result_mutex_);
                    last_result_.document.text_blocks = ocr_result.document.text_blocks;
                    last_result_.document.full_text = ocr_result.document.full_text;
                    last_result_.document.ocr_confidence = ocr_result.document.ocr_confidence;
                    last_result_.document.line_count = ocr_result.document.line_count;
                    last_result_.document.word_count = ocr_result.document.word_count;
                    last_result_.document.char_count = ocr_result.document.char_count;
                    last_result_.document.type = ocr_result.document.type;
                    last_result_.document.type_label = ocr_result.document.type_label;
                    last_result_.document.fields = ocr_result.document.fields;
                    last_result_.document.classification_confidence = ocr_result.document.classification_confidence;
                    last_result_.valid = ocr_result.valid;
                }
                
                if (frame_callback_) {
                    frame_callback_(last_result());
                }
            }
            
            // ── Step 2: DiagnosticsModule inline (fast, draws overlay) ──
            {
                std::lock_guard<std::mutex> lock(result_mutex_);
                FrameResult diag_result;
                diag_result.timestamp = std::chrono::system_clock::to_time_t(
                    std::chrono::system_clock::now());
                diag_result.document = last_result_.document;
                diag_result.valid = last_result_.valid;
                
                // Only run DiagnosticsModule (skip OCR and document_analyzer)
                auto* diag = get_module("diagnostics");
                if (diag && diag->enabled()) {
                    diag->process_frame(pixels, fw, fh, diag_result);
                }
            }
            
            // ── Step 3: Send overlay immediately ──
            if (raw_frame_callback_) {
                raw_frame_callback_(pixels, fw, fh);
            }
            
            // ── Step 4: Launch async OCR+DocumentAnalyzer if previous done ──
            if (!ocr_future_.valid() || 
                ocr_future_.wait_for(std::chrono::seconds(0)) == std::future_status::ready) {
                
                // Copy pixels (camera buffer overwritten on next recv_frame)
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

// ── Async: OCR + DocumentAnalyzer (runs in background thread) ────
FrameResult Engine::process_frame_async(const uint32_t* pixels, int w, int h) {
    FrameResult result;
    result.timestamp = std::chrono::system_clock::to_time_t(
        std::chrono::system_clock::now());
    
    // Copy pixels for modules that need non-const (OCR)
    std::vector<uint32_t> buf(pixels, pixels + w * h);
    
    // Run OCR (the heavy one)
    auto* ocr = get_module("ocr");
    if (ocr && ocr->enabled()) {
        ocr->process_frame(buf.data(), w, h, result);
    }
    
    // Run DocumentAnalyzer (needs OCR results, fast classification)
    if (result.valid) {
        auto* analyzer = get_module("document_analyzer");
        if (analyzer && analyzer->enabled()) {
            analyzer->process_frame(buf.data(), w, h, result);
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

} // namespace lexis
