#include "iris/canvas.h"
#include "iris/camera_canvas.h"
#include "iris/camera.h"

#include <algorithm>
#include <atomic>
#include <cctype>
#include <cerrno>
#include <chrono>
#include <cmath>
#include <csignal>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <iostream>
#include <memory>
#include <string>
#include <thread>
#include <vector>
#include <unistd.h>
#include <sys/socket.h>
#include <sys/un.h>

static std::atomic<bool> g_running{true};
static bool g_verbose = false;

static void int_handler(int) { g_running = false; }

static bool parse_size(const std::string& s, int& w, int& h) {
    auto x = s.find('x');
    if (x == std::string::npos) return false;
    w = std::stoi(s.substr(0, x));
    h = std::stoi(s.substr(x + 1));
    return w > 0 && h > 0;
}

struct Config {
    std::string mode;
    std::string listen_socket;
    std::string display_socket = "canvas-display";
    std::string tcp_host;
    int tcp_port = 0;
    int width  = 640;
    int height = 480;
    int cam_id = 0;
    int rotate = 0;  // rotación en grados (0-359, modulo 360, ±)
    bool btn = false; // dibujar botón minimizar
    bool original = false; // no rotar frame, mostrar original
};

static void print_usage() {
    std::cout << "iris — camera frame forwarder\n"
              << "\n"
              << "Uso: ./iris [--listen[=socket]] [--size=WxH] [--display=socket] [--verbose]\n"
              << "       ./iris [--tcp] [--host=HOST] [--port=PORT] [--size=WxH]\n"
              << "\n"
              << "Modos:\n"
              << "  --listen, -l [socket]    Recibir frames de Android (default: cam-0)\n"
              << "  --tcp, -t                Enviar frames a servidor remoto\n"
              << "\n"
              << "Opciones:\n"
              << "  --size, -s WxH           Resolucion (default: 640x480)\n"
              << "  --display, -d socket     Socket de display (default: canvas-display)\n"
              << "  --host, -H HOST          Host TCP (default: localhost)\n"
              << "  --port, -p PORT          Puerto TCP\n"
              << "  --verbose, -v            Log detallado\n"
              << "  --rotate, -r GRADOS      Rotar frame (0-359, modulo 360, ±)\n"
              << "  --btn                    Mostrar botón minimizar en overlay\n"
              << "  --original               No rotar frame, mostrar original\n"
              << "  --help, -h               Esta ayuda\n"
              << "\n"
              << "Variables de entorno:\n"
              << "  CANVAS_SOCKET            Socket de display\n"
              << "  CAMERA_SOCKET             Socket de camara (lectura)\n";
}

static Config parse_args(int argc, char** argv) {
    for (int i = 1; i < argc; i++) {
        std::string a = argv[i];
        if (a == "--help" || a == "-h") {
            print_usage();
            std::exit(0);
        }
    }
    Config cfg;
    auto val_of = [](const std::string& a) -> std::string {
        auto eq = a.find('=');
        if (eq != std::string::npos && eq + 1 < a.size())
            return a.substr(eq + 1);
        return {};
    };
    for (int i = 1; i < argc; i++) {
        std::string arg = argv[i];
        if (arg == "--listen" || arg == "-l" || arg.rfind("--listen=", 0) == 0) {
            cfg.mode = "listen";
            auto v = val_of(arg);
            if (!v.empty())
                cfg.listen_socket = v;
            else if (i + 1 < argc && argv[i + 1][0] != '-')
                cfg.listen_socket = argv[++i];
            else
                cfg.listen_socket = "cam-0";
        } else if (arg == "--tcp" || arg == "-t") {
            cfg.mode = "tcp";
        } else if (arg.rfind("--host=", 0) == 0) {
            auto v = val_of(arg); if (!v.empty()) cfg.tcp_host = v;
        } else if (arg == "--host" || arg == "-H") {
            if (i + 1 < argc) cfg.tcp_host = argv[++i];
        } else if (arg.rfind("--port=", 0) == 0) {
            auto v = val_of(arg); if (!v.empty()) cfg.tcp_port = std::stoi(v);
        } else if (arg == "--port" || arg == "-p") {
            if (i + 1 < argc) cfg.tcp_port = std::stoi(argv[++i]);
        } else if (arg.rfind("--size=", 0) == 0 || arg.rfind("-s=", 0) == 0) {
            int w, h;
            if (parse_size(val_of(arg), w, h)) { cfg.width = w; cfg.height = h; }
        } else if (arg == "--size" || arg == "-s") {
            if (i + 1 < argc) {
                int w, h;
                if (parse_size(argv[++i], w, h)) { cfg.width = w; cfg.height = h; }
            }
        } else if (arg.rfind("--display=", 0) == 0) {
            auto v = val_of(arg); if (!v.empty()) cfg.display_socket = v;
        } else if (arg == "--display" || arg == "-d") {
            if (i + 1 < argc) cfg.display_socket = argv[++i];
        } else if (arg.rfind("--rotate=", 0) == 0 || arg.rfind("-r=", 0) == 0) {
            cfg.rotate = std::stoi(val_of(arg));
        } else if (arg == "--rotate" || arg == "-r") {
            if (i + 1 < argc) cfg.rotate = std::stoi(argv[++i]);
        } else if (arg == "--verbose" || arg == "-v") {
            g_verbose = true;
        } else if (arg == "--demo") {
            cfg.mode = "demo";
        } else if (arg == "--btn") {
            cfg.btn = true;
        } else if (arg == "--original") {
            cfg.original = true;
        }
    }
    // Extract camera ID from socket name suffix ("cam-0" -> id 0)
    if (cfg.listen_socket.size() > 4) {
        auto suffix = cfg.listen_socket.substr(4);
        if (!suffix.empty() && suffix[0] >= '0' && suffix[0] <= '9')
            cfg.cam_id = std::stoi(suffix);
    }
    // Display socket override via env var
    const char* env_display = std::getenv("CANVAS_SOCKET");
    if (env_display && env_display[0])
        cfg.display_socket = env_display;
    return cfg;
}

/* -----------------------------------------------------------
 *  send_camera_ctrl — connect to Android's cam-ctrl and send command(s)
 *  Sends all commands in one connection; Android applies them on disconnect.
 *  Reintenta cada 1s si el socket no está disponible aún.
 * --------------------------------------------------------- */
static bool send_camera_ctrl(const std::vector<std::string>& cmds) {
    for (int attempt = 0; attempt < 15; attempt++) {
        int fd = socket(AF_UNIX, SOCK_STREAM, 0);
        if (fd < 0) {
            if (g_verbose) std::cerr << "[Iris] cam-ctrl: socket() failed\n";
            return false;
        }

        struct sockaddr_un addr;
        std::memset(&addr, 0, sizeof(addr));
        addr.sun_family = AF_UNIX;
        std::strcpy(addr.sun_path + 1, "cam-ctrl");

        socklen_t len = offsetof(struct sockaddr_un, sun_path) + 1 + 8;
        if (connect(fd, (struct sockaddr*)&addr, len) < 0) {
            close(fd);
            if (attempt == 0)
                std::cerr << "[Iris] cam-ctrl: waiting for server...\n";
            std::this_thread::sleep_for(std::chrono::seconds(1));
            continue;
        }

        bool ok = true;
        for (const auto& cmd : cmds) {
            std::string payload = cmd + "\n";
            ssize_t n = write(fd, payload.data(), payload.size());
            if (n <= 0) {
                if (g_verbose)
                    std::cerr << "[Iris] cam-ctrl: write failed (" << strerror(errno) << ")\n";
                close(fd);
                ok = false;
                break;
            }
            if (g_verbose)
                std::cout << "[Iris] cam-ctrl: sent '" << cmd << "'\n";
        }
        if (!ok) { std::this_thread::sleep_for(std::chrono::seconds(1)); continue; }

        // Cerrar para que Android procese todos los comandos juntos
        close(fd);
        return true;
    }
    std::cerr << "[Iris] cam-ctrl: giving up after 15 attempts\n";
    return false;
}

/* -----------------------------------------------------------
 *  main
 * --------------------------------------------------------- */
int main(int argc, char** argv) {
    std::signal(SIGINT, int_handler);
    std::signal(SIGTERM, int_handler);
    std::signal(SIGPIPE, SIG_IGN);

    auto cfg = parse_args(argc, argv);

    if (cfg.mode == "listen") {
        if (cfg.listen_socket.empty()) {
            std::cerr << "[Iris] missing socket name for listen mode\n";
            return 1;
        }

        iris::CameraCanvas canvas;
        if (!canvas.listen(cfg.listen_socket)) {
            std::cerr << "[Iris] failed to listen on " << cfg.listen_socket << "\n";
            return 1;
        }

        std::cout << "[Iris] Listening on " << cfg.listen_socket << " (" << cfg.width << "x" << cfg.height << ")\n";

        // Calcular posición del botón (determinístico, basado en geometría)
        int btn_x = -1, btn_y = -1;
        if (cfg.btn) {
            int angle = cfg.rotate % 360;
            if (angle < 0) angle += 360;
            if (angle != 0) {
                double rad = angle * M_PI / 180.0;
                double c = std::cos(rad), s = std::sin(rad);
                double ac = std::abs(c), as_ = std::abs(s);
                int bb_w = static_cast<int>(std::ceil(cfg.width * ac + cfg.height * as_));
                int bb_h = static_cast<int>(std::ceil(cfg.width * as_ + cfg.height * ac));
                double src_cx = cfg.width / 2.0, src_cy = cfg.height / 2.0;
                double dst_cx = bb_w / 2.0, dst_cy = bb_h / 2.0;
                btn_x = static_cast<int>(c * src_cx + s * src_cy + dst_cx) - 20;
                btn_y = static_cast<int>(s * src_cx - c * src_cy + dst_cy) + 20;
            } else {
                btn_x = cfg.width - 20;
                btn_y = 20;
            }
        }

        // Notificar a Android tras tener el socket listo (un solo envío)
        std::vector<std::string> ctrl_cmds = {
            "camera " + std::to_string(cfg.cam_id),
            "size " + std::to_string(cfg.width) + "x" + std::to_string(cfg.height)
        };
        if (cfg.original) {
            ctrl_cmds.push_back("rotate none");
        }
        if (btn_x >= 0) {
            ctrl_cmds.push_back("btn " + std::to_string(btn_x) + " " + std::to_string(btn_y));
        } else {
            ctrl_cmds.push_back("btn hide");
        }
        send_camera_ctrl(ctrl_cmds);

        iris::Canvas display;
        int disp_w = 0, disp_h = 0;
        // Connect to display socket (retry until available)
        while (g_running && !display.connect_overlay(cfg.display_socket)) {
            if (g_verbose)
                std::cerr << "[Iris] waiting for display '" << cfg.display_socket << "'...\n";
            std::this_thread::sleep_for(std::chrono::seconds(1));
        }
        if (g_running)
            std::cout << "[Iris] Display connected on '" << cfg.display_socket << "'\n";

        while (g_running) {
            if (!canvas.connected()) {
                if (g_verbose) std::cout << "[Iris] Waiting for client...\n";
                if (!canvas.accept_client()) {
                    std::this_thread::sleep_for(std::chrono::milliseconds(500));
                    continue;
                }
                std::cout << "[Iris] Client connected\n";
            }

            if (canvas.recv_frame()) {
                int fw = canvas.width(), fh = canvas.height();

                if (cfg.rotate != 0 && !cfg.original) {
                    if (g_verbose)
                        std::cout << "[Iris] rotating " << cfg.rotate << "°\n";
                    if (!canvas.rotate(cfg.rotate)) {
                        std::cerr << "[Iris] rotate failed\n";
                    }
                    fw = canvas.width(); fh = canvas.height();
                }

                if (g_verbose)
                    std::cout << "[Iris] frame " << fw << "x" << fh << "\n";
                // Reinit display if size changed
                if (fw != disp_w || fh != disp_h) {
                    if (!display.init(fw, fh)) {
                        std::cerr << "[Iris] display reinit failed\n";
                        break;
                    }
                    disp_w = fw; disp_h = fh;
                }
                // Forward to display
                display.load_frame(
                    reinterpret_cast<const uint8_t*>(canvas.pixels()),
                    static_cast<size_t>(fw) * fh * 4
                );
                display.present();
            } else if (canvas.connected()) {
                std::this_thread::sleep_for(std::chrono::milliseconds(10));
            }
        }

    } else if (cfg.mode == "demo") {
        while (g_running) {
            iris::Canvas canvas;
            if (!canvas.connect_overlay(cfg.display_socket)) {
                std::cerr << "[Iris] Waiting for display '" << cfg.display_socket << "'...\n";
                std::this_thread::sleep_for(std::chrono::seconds(1));
                continue;
            }
            if (!canvas.init(cfg.width, cfg.height)) {
                std::cerr << "[Iris] failed to init canvas\n";
                return 1;
            }

            int cx = cfg.width / 2, cy = cfg.height / 2;
            int r = std::min(cfg.width, cfg.height) / 4;
            for (int frame = 0; g_running && frame < 60; frame++) {
                canvas.clear(0xFF1a1a2e);
                canvas.draw_line(0, 0, cfg.width, cfg.height, 0xFFe94560, 2);
                canvas.draw_line(cfg.width, 0, 0, cfg.height, 0xFFe94560, 2);
                canvas.fill_rect(cfg.width / 4, cfg.height / 4,
                                  cfg.width / 2, cfg.height / 2, 0x4400a8ff);
                canvas.draw_circle(cx, cy, r, 0xFFe94560, false);
                canvas.draw_circle(cx, cy, r / 3, 0xFF0f3460, true);
                canvas.draw_text(20, cfg.height - 20, "AArchDroid iris demo", 0xFFEEEEEE);
                canvas.draw_text(cfg.width / 2 - 40, cfg.height / 2 + 5,
                                  "TEST", 0xFFFFCC00, 18);
                if (!canvas.present()) {
                    std::cerr << "[Iris] Display lost, reconnecting...\n";
                    break;
                }
                std::this_thread::sleep_for(std::chrono::milliseconds(50));
            }
            if (!g_running) break;
            std::this_thread::sleep_for(std::chrono::seconds(1));
        }
        std::cout << "[Iris] Demo complete\n";
    } else if (cfg.mode == "tcp") {
        if (cfg.tcp_host.empty() || cfg.tcp_port <= 0) {
            std::cerr << "[Iris] missing --host/--port for tcp mode\n";
            return 1;
        }

        iris::Camera cam;
        if (!cam.open("/dev/video0", cfg.width, cfg.height)) {
            std::cerr << "[Iris] failed to open camera\n";
            return 1;
        }
        if (!cam.start()) {
            std::cerr << "[Iris] failed to start camera\n";
            return 1;
        }

        iris::Canvas canvas;
        if (!canvas.connect_overlay("cam-0")) {
            std::cerr << "[Iris] failed to connect\n";
            return 1;
        }

        std::vector<uint8_t> buf(cfg.width * cfg.height * 4);

        while (g_running) {
            if (cam.capture(buf.data(), buf.size())) {
                canvas.load_frame(buf.data(), buf.size());
                canvas.present();
                if (g_verbose)
                    std::cout << "[Iris] sent frame " << cfg.width << "x" << cfg.height << "\n";
            }
        }

        cam.stop();
        cam.close();
    }

    return 0;
}
