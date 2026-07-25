/*
 * Iris Viewer — test de captura + canvas en PC (GLFW)
 *
 * Uso: iris_viewer [--device=/dev/videoN] [--size=WxH] [--fps=N]
 *
 * Detecta cámaras, captura frames y los renderiza en ventana GLFW.
 * Si no hay cámara, genera test pattern animado (barras de color).
 *
 * Teclas: q/Esc = salir, f = fullscreen
 *
 * Compilar:
 *   cmake -B build_native -DIRIS_BUILD_VIEWER=ON
 *   cmake --build build_native
 */

#include "iris/canvas.h"
#include "iris/camera.h"

#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <cmath>
#include <string>
#include <vector>
#include <chrono>
#include <thread>
#include <iostream>
#include <csignal>

#define GLFW_INCLUDE_NONE
#include <GLFW/glfw3.h>
#include <GL/gl.h>

static volatile bool g_running = true;
static GLFWwindow* g_window = nullptr;
static bool g_fullscreen = false;

// ── Signal ──────────────────────────────────────────────────────────

static void signal_handler(int) { g_running = false; }

// ── GLFW callbacks ──────────────────────────────────────────────────

static void glfw_error_cb(int err, const char* desc) {
    std::fprintf(stderr, "[GLFW] Error %d: %s\n", err, desc);
}

static void glfw_key_cb(GLFWwindow* win, int key, int, int action, int) {
    if (action != GLFW_PRESS) return;
    if (key == GLFW_KEY_ESCAPE || key == GLFW_KEY_Q) {
        g_running = false;
    } else if (key == GLFW_KEY_F) {
        g_fullscreen = !g_fullscreen;
        GLFWmonitor* mon = g_fullscreen ? glfwGetPrimaryMonitor() : nullptr;
        if (mon) {
            const GLFWvidmode* mode = glfwGetVideoMode(mon);
            glfwSetWindowMonitor(win, mon, 0, 0, mode->width, mode->height, mode->refreshRate);
        } else {
            glfwSetWindowMonitor(win, nullptr, 100, 100, 800, 600, 0);
        }
    }
}

// ── Test pattern generator ──────────────────────────────────────────

static void generate_test_pattern(uint32_t* pixels, int w, int h, int frame) {
    double t = frame * 0.02;
    for (int y = 0; y < h; y++) {
        for (int x = 0; x < w; x++) {
            double u = (double)x / w;
            double v = (double)y / h;

            // Rainbow hue rotating with time
            double hue = fmod(u + t * 0.5, 1.0);
            double r, g, b;
            double h6 = hue * 6.0;
            int hi = (int)h6;
            double f = h6 - hi;
            double q_ = 1.0 - f;
            switch (hi % 6) {
                case 0: r=1; g=f; b=0; break;
                case 1: r=q_; g=1; b=0; break;
                case 2: r=0; g=1; b=f; break;
                case 3: r=0; g=q_; b=1; break;
                case 4: r=f; g=0; b=1; break;
                default: r=1; g=0; b=q_; break;
            }

            // Sine wave overlay
            double wave = std::sin(u * 20.0 + t * 3.0) * 0.3 + 0.7;
            r *= wave; g *= wave; b *= wave;

            // Grid lines
            if ((x % 32 == 0) || (y % 32 == 0)) {
                r *= 0.6; g *= 0.6; b *= 0.6;
            }

            // Center crosshair
            if (std::abs(x - w/2) < 2 || std::abs(y - h/2) < 2) {
                r = 1.0f; g = 1.0f; b = 1.0f;
            }

            // BGRA little-endian
            uint8_t r8 = (uint8_t)(r * 255);
            uint8_t g8 = (uint8_t)(g * 255);
            uint8_t b8 = (uint8_t)(b * 255);
            pixels[y * w + x] = 0xFF000000 | (b8 << 16) | (g8 << 8) | r8;
        }
    }
}

// ── OpenGL texture upload ───────────────────────────────────────────

static GLuint g_texture = 0;

static void upload_frame(const uint32_t* pixels, int w, int h) {
    if (g_texture == 0) {
        glGenTextures(1, &g_texture);
        glBindTexture(GL_TEXTURE_2D, g_texture);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    }
    glBindTexture(GL_TEXTURE_2D, g_texture);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0,
                 GL_BGRA, GL_UNSIGNED_INT_8_8_8_8_REV, pixels);
}

static void render_scene(int win_w, int win_h, int img_w, int img_h) {
    glClear(GL_COLOR_BUFFER_BIT);

    glMatrixMode(GL_PROJECTION);
    glLoadIdentity();
    glOrtho(0, win_w, win_h, 0, -1, 1);
    glMatrixMode(GL_MODELVIEW);
    glLoadIdentity();

    glBindTexture(GL_TEXTURE_2D, g_texture);
    glEnable(GL_TEXTURE_2D);

    // Aspect-ratio correct fit
    float img_aspect = (float)img_w / img_h;
    float win_aspect = (float)win_w / win_h;
    float x0, y0, x1, y1;
    if (img_aspect > win_aspect) {
        float h = win_w / img_aspect;
        x0 = 0; x1 = win_w;
        y0 = (win_h - h) / 2; y1 = y0 + h;
    } else {
        float w = win_h * img_aspect;
        y0 = 0; y1 = win_h;
        x0 = (win_w - w) / 2; x1 = x0 + w;
    }

    glBegin(GL_QUADS);
    glTexCoord2i(0, 0); glVertex2f(x0, y0);
    glTexCoord2i(1, 0); glVertex2f(x1, y0);
    glTexCoord2i(1, 1); glVertex2f(x1, y1);
    glTexCoord2i(0, 1); glVertex2f(x0, y1);
    glEnd();

    glDisable(GL_TEXTURE_2D);
}

// ── Parse args ──────────────────────────────────────────────────────

struct Config {
    std::string device;
    int width = 640;
    int height = 480;
    int fps = 30;
};

static Config parse_args(int argc, char* argv[]) {
    Config cfg;
    for (int i = 1; i < argc; i++) {
        std::string a = argv[i];
        if (a.rfind("--device=", 0) == 0) {
            cfg.device = a.substr(9);
        } else if (a.rfind("--size=", 0) == 0) {
            sscanf(a.c_str() + 7, "%dx%d", &cfg.width, &cfg.height);
        } else if (a.rfind("--fps=", 0) == 0) {
            cfg.fps = std::stoi(a.substr(6));
        } else if (a == "--help") {
            std::cout << "Uso: iris_viewer [--device=/dev/videoN] [--size=WxH] [--fps=N]\n";
            exit(0);
        }
    }
    return cfg;
}

// ── Main ────────────────────────────────────────────────────────────

int main(int argc, char* argv[]) {
    Config cfg = parse_args(argc, argv);

    std::signal(SIGINT, signal_handler);
    std::signal(SIGTERM, signal_handler);

    // Init GLFW
    glfwSetErrorCallback(glfw_error_cb);
    if (!glfwInit()) {
        std::cerr << "[Viewer] Failed to init GLFW\n";
        return 1;
    }

    g_window = glfwCreateWindow(800, 600, "Iris Viewer", nullptr, nullptr);
    if (!g_window) {
        std::cerr << "[Viewer] Failed to create window\n";
        glfwTerminate();
        return 1;
    }
    glfwMakeContextCurrent(g_window);
    glfwSwapInterval(1);
    glfwSetKeyCallback(g_window, glfw_key_cb);
    glClearColor(0.15f, 0.15f, 0.15f, 1.0f);

    // Init Iris canvas
    iris::Canvas canvas;
    if (!canvas.init(cfg.width, cfg.height)) {
        std::cerr << "[Viewer] Failed to init canvas\n";
        return 1;
    }

    // Try camera
    iris::Camera cam;
    bool have_camera = false;

    if (!cfg.device.empty()) {
        have_camera = cam.open(cfg.device.c_str(), cfg.width, cfg.height, cfg.fps);
        if (have_camera) have_camera = cam.start();
    } else {
        std::vector<std::string> devices = {"/dev/video0", "/dev/video2", "/dev/video1"};
        for (const auto& dev : devices) {
            if (cam.open(dev.c_str(), cfg.width, cfg.height, cfg.fps)) {
                std::cout << "[Viewer] Camera: " << dev << "\n";
                if (cam.start()) {
                    have_camera = true;
                    break;
                }
                cam.close();
            }
        }
    }

    if (have_camera) {
        std::cout << "[Viewer] Source: camera (" << cfg.width << "x" << cfg.height << ")\n";
    } else {
        std::cout << "[Viewer] Source: synthetic test pattern\n";
    }

    // Main loop
    std::vector<uint8_t> bgra_buf(static_cast<size_t>(cfg.width) * cfg.height * 4);
    int frame_id = 0;

    while (g_running && !glfwWindowShouldClose(g_window)) {
        glfwPollEvents();

        bool got_frame = false;
        if (have_camera) {
            got_frame = cam.capture(bgra_buf.data(), bgra_buf.size());
        }

        if (got_frame) {
            canvas.load_frame(bgra_buf.data(), bgra_buf.size());
        } else {
            generate_test_pattern(canvas.pixels(), cfg.width, cfg.height, frame_id);
        }

        // Upload texture & render
        upload_frame(canvas.pixels(), cfg.width, cfg.height);
        int fb_w, fb_h;
        glfwGetFramebufferSize(g_window, &fb_w, &fb_h);
        glViewport(0, 0, fb_w, fb_h);
        render_scene(fb_w, fb_h, cfg.width, cfg.height);
        glfwSwapBuffers(g_window);

        // También enviar por socket (por si hay server)
        canvas.present();

        frame_id++;
        std::this_thread::sleep_for(std::chrono::milliseconds(1000 / cfg.fps));
    }

    // Cleanup
    if (have_camera) cam.close();
    if (g_texture) glDeleteTextures(1, &g_texture);
    glfwDestroyWindow(g_window);
    glfwTerminate();
    std::cout << "[Viewer] Done.\n";
    return 0;
}
