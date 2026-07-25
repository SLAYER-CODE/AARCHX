/*
 * Iris — dual_camera example
 *
 * Abre cámara frontal (/dev/video0) y trasera (/dev/video2)
 * simultáneamente, captura frames y los envía por sockets AF_UNIX
 * a los overlays Android "cam-front" y "cam-back".
 *
 * Uso:
 *   dual_camera [--front=/dev/videoN] [--back=/dev/videoN] [--size=640x480] [--fps=30]
 *
 * El lado Android debe tener MultiCanvasServer escuchando en esos sockets.
 */

#include "iris/camera.h"
#include "iris/canvas.h"

#include <csignal>
#include <cstring>
#include <iostream>
#include <chrono>
#include <thread>
#include <vector>

static volatile bool g_running = true;

static void signal_handler(int) {
    g_running = false;
}

struct Config {
    std::string front_dev = "/dev/video0";
    std::string back_dev = "/dev/video2";
    int width = 640;
    int height = 480;
    int fps = 30;
    bool verbose = false;
};

static void print_help(const char* prog) {
    std::cerr << "Usage: " << prog << " [options]\n"
              << "Options:\n"
              << "  --front=<dev>   Front camera device [" << "/dev/video0]\n"
              << "  --back=<dev>    Back camera device  [" << "/dev/video2]\n"
              << "  --size=WxH      Resolution          [640x480]\n"
              << "  --fps=N         Frames per second   [30]\n"
              << "  --verbose       Verbose output\n"
              << "  --help          This help\n";
}

static Config parse_args(int argc, char* argv[]) {
    Config cfg;
    for (int i = 1; i < argc; i++) {
        std::string arg = argv[i];
        if (arg == "--help") {
            print_help(argv[0]);
            exit(0);
        } else if (arg.rfind("--front=", 0) == 0) {
            cfg.front_dev = arg.substr(8);
        } else if (arg.rfind("--back=", 0) == 0) {
            cfg.back_dev = arg.substr(7);
        } else if (arg.rfind("--size=", 0) == 0) {
            if (sscanf(arg.c_str() + 7, "%dx%d", &cfg.width, &cfg.height) != 2) {
                std::cerr << "Invalid --size format. Use WxH (e.g. 640x480)\n";
                exit(1);
            }
        } else if (arg.rfind("--fps=", 0) == 0) {
            cfg.fps = std::stoi(arg.substr(6));
        } else if (arg == "--verbose" || arg == "-v") {
            cfg.verbose = true;
        } else {
            std::cerr << "Unknown option: " << arg << "\n";
            print_help(argv[0]);
            exit(1);
        }
    }
    return cfg;
}

int main(int argc, char* argv[]) {
    Config cfg = parse_args(argc, argv);

    std::signal(SIGINT, signal_handler);
    std::signal(SIGTERM, signal_handler);

    // ── Inicializar front camera ──
    iris::Camera cam_front;
    if (!cam_front.open(cfg.front_dev.c_str(), cfg.width, cfg.height, cfg.fps)) {
        std::cerr << "[dual_camera] Failed to open front camera: "
                  << cfg.front_dev << "\n";
        return 1;
    }
    if (!cam_front.start()) {
        std::cerr << "[dual_camera] Failed to start front camera\n";
        return 1;
    }

    // ── Inicializar back camera ──
    iris::Camera cam_back;
    if (!cam_back.open(cfg.back_dev.c_str(), cfg.width, cfg.height, cfg.fps)) {
        std::cerr << "[dual_camera] Failed to open back camera: "
                  << cfg.back_dev << "\n";
        cam_front.close();
        return 1;
    }
    if (!cam_back.start()) {
        std::cerr << "[dual_camera] Failed to start back camera\n";
        cam_front.close();
        return 1;
    }

    // ── Inicializar canvases ──
    iris::Canvas canvas_front, canvas_back;
    if (!canvas_front.init(cfg.width, cfg.height)) {
        std::cerr << "[dual_camera] Failed to init front canvas\n";
        return 1;
    }
    if (!canvas_back.init(cfg.width, cfg.height)) {
        std::cerr << "[dual_camera] Failed to init back canvas\n";
        return 1;
    }

    // ── Conectar sockets ──
    if (!canvas_front.connect("cam-front")) {
        std::cerr << "[dual_camera] Cannot connect front socket. "
                  << "Is the Android MultiCanvasServer running?\n";
        // Continuamos igual — puede conectarse después
    }
    if (!canvas_back.connect("cam-back")) {
        std::cerr << "[dual_camera] Cannot connect back socket. "
                  << "Is the Android MultiCanvasServer running?\n";
    }

    std::cout << "[dual_camera] Streaming "
              << cfg.front_dev << " → cam-front, "
              << cfg.back_dev << " → cam-back\n"
              << "[dual_camera] " << cfg.width << "x" << cfg.height
              << " @" << cfg.fps << " fps\n";
    std::cout << "[dual_camera] Press Ctrl+C to stop\n";

    // ── Buffer compartido para captura (reutilizado) ──
    size_t frame_bytes = static_cast<size_t>(cfg.width) * cfg.height * 4;
    std::vector<uint8_t> bgra_buf(frame_bytes);

    // ── Loop principal ──
    using Clock = std::chrono::steady_clock;
    auto frame_duration = std::chrono::milliseconds(1000 / cfg.fps / 2);
    int frame_count = 0;
    auto last_log = Clock::now();

    while (g_running) {
        auto loop_start = Clock::now();

        // Capturar frame frontal
        if (cam_front.capture(bgra_buf.data(), frame_bytes)) {
            canvas_front.load_frame(bgra_buf.data(), frame_bytes);
            canvas_front.present();
        }

        // Capturar frame trasero
        if (cam_back.capture(bgra_buf.data(), frame_bytes)) {
            canvas_back.load_frame(bgra_buf.data(), frame_bytes);
            canvas_back.present();
        }

        frame_count++;

        // Log cada 5 segundos
        auto now = Clock::now();
        auto elapsed = std::chrono::duration_cast<std::chrono::seconds>(
            now - last_log).count();
        if (cfg.verbose && elapsed >= 5) {
            std::cout << "[dual_camera] " << frame_count << " frames ("
                      << (frame_count / elapsed) << " fps)\n";
            frame_count = 0;
            last_log = now;
        }

        // Frame rate control
        auto elapsed_frame = Clock::now() - loop_start;
        if (elapsed_frame < frame_duration) {
            std::this_thread::sleep_for(frame_duration - elapsed_frame);
        }
    }

    // ── Cleanup ──
    std::cout << "\n[dual_camera] Shutting down...\n";
    cam_front.close();
    cam_back.close();
    std::cout << "[dual_camera] Done.\n";
    return 0;
}
