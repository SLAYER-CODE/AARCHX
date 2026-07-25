/*
 * capture_test — captura un frame y lo guarda como PPM
 *
 * Uso: capture_test [--device=/dev/videoN] [--size=640x480] [--output=frame.ppm]
 *
 * Verifica el pipeline completo: camera → YUV→BGRA → dump a disco.
 */

#include "iris/camera.h"

#include <cstdio>
#include <cstring>
#include <string>
#include <vector>
#include <chrono>
#include <thread>
#include <iostream>

static void save_ppm(const char* path, const uint8_t* bgra, int w, int h) {
    FILE* f = fopen(path, "wb");
    if (!f) { perror("fopen"); return; }
    fprintf(f, "P6\n%d %d\n255\n", w, h);
    for (int y = 0; y < h; y++) {
        for (int x = 0; x < w; x++) {
            int idx = (y * w + x) * 4;
            uint8_t b = bgra[idx + 0];
            uint8_t g = bgra[idx + 1];
            uint8_t r = bgra[idx + 2];
            fputc(r, f); fputc(g, f); fputc(b, f);
        }
    }
    fclose(f);
    printf("[capture_test] Saved %s (%dx%d)\n", path, w, h);
}

int main(int argc, char* argv[]) {
    std::string device = "/dev/video2";
    std::string output = "frame.ppm";
    int width = 640, height = 480;

    for (int i = 1; i < argc; i++) {
        std::string a = argv[i];
        if (a.rfind("--device=", 0) == 0) device = a.substr(9);
        else if (a.rfind("--output=", 0) == 0) output = a.substr(9);
        else if (a.rfind("--size=", 0) == 0) sscanf(a.c_str() + 7, "%dx%d", &width, &height);
        else if (a == "--help") {
            printf("Uso: capture_test [--device=/dev/videoN] [--output=file.ppm] [--size=WxH]\n");
            return 0;
        }
    }

    iris::Camera cam;
    if (!cam.open(device.c_str(), width, height, 30)) {
        std::cerr << "[capture_test] Failed to open " << device << "\n";
        return 1;
    }
    if (!cam.start()) {
        std::cerr << "[capture_test] Failed to start\n";
        return 1;
    }

    printf("[capture_test] Camera: %s %dx%d\n", device.c_str(), cam.width(), cam.height());

    std::vector<uint8_t> bgra(static_cast<size_t>(cam.width()) * cam.height() * 4);

    // Try up to 30 frames to get one
    for (int i = 0; i < 30; i++) {
        if (cam.capture(bgra.data(), bgra.size())) {
            printf("[capture_test] Frame %d captured!\n", i);
            save_ppm(output.c_str(), bgra.data(), cam.width(), cam.height());
            cam.stop();
            cam.close();
            return 0;
        }
        std::this_thread::sleep_for(std::chrono::milliseconds(10));
    }

    std::cerr << "[capture_test] No frames captured after 30 attempts\n";
    cam.stop();
    cam.close();
    return 1;
}
