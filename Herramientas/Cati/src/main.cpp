#include "iris/canvas.h"

#define STB_IMAGE_IMPLEMENTATION
#include "stb/stb_image.h"

#include <cstring>
#include <iostream>
#include <csignal>
#include <atomic>
#include <unistd.h>
#include <getopt.h>

static std::atomic<bool> g_running{true};
static void signal_handler(int) { g_running = false; }

struct Config {
    std::string image_path;
    std::string display_socket = "canvas-display";
    int force_w = 0;
    int force_h = 0;
    bool fit = false;
    bool verbose = false;
};

static Config parse_args(int argc, char** argv) {
    Config cfg;
    static struct option long_opts[] = {
        {"display", required_argument, nullptr, 'd'},
        {"size",    required_argument, nullptr, 's'},
        {"fit",     no_argument,       nullptr, 'f'},
        {"verbose", no_argument,       nullptr, 'v'},
        {"help",    no_argument,       nullptr, 'h'},
        {nullptr, 0, nullptr, 0}
    };

    int opt;
    while ((opt = getopt_long(argc, argv, "d:s:fvh", long_opts, nullptr)) != -1) {
        switch (opt) {
            case 'd': cfg.display_socket = optarg; break;
            case 's': {
                if (sscanf(optarg, "%dx%d", &cfg.force_w, &cfg.force_h) != 2) {
                    std::cerr << "Invalid size: " << optarg << " (use WxH)\n";
                    exit(1);
                }
                break;
            }
            case 'f': cfg.fit = true; break;
            case 'v': cfg.verbose = true; break;
            case 'h':
                std::cout << "Usage: cati <image> [options]\n"
                          << "  -d, --display SOCKET  Display socket (default: canvas-display)\n"
                          << "  -s, --size WxH        Force canvas size\n"
                          << "  -f, --fit             Fit to canvas (stretch)\n"
                          << "  -v, --verbose         Verbose\n";
                exit(0);
            default: exit(1);
        }
    }

    if (optind >= argc) {
        std::cerr << "Error: missing image path\nUsage: cati <image> [options]\n";
        exit(1);
    }
    cfg.image_path = argv[optind];
    return cfg;
}

int main(int argc, char** argv) {
    signal(SIGINT, signal_handler);
    signal(SIGTERM, signal_handler);

    auto cfg = parse_args(argc, argv);

    int img_w, img_h, channels;
    unsigned char* img = stbi_load(cfg.image_path.c_str(), &img_w, &img_h, &channels, 4);
    if (!img) {
        std::cerr << "Error: cannot load image '" << cfg.image_path << "'\n";
        return 1;
    }

    if (cfg.verbose) {
        std::cout << "Image: " << img_w << "x" << img_h
                  << " (" << channels << " ch)\n";
    }

    int canvas_w = cfg.force_w ? cfg.force_w : img_w;
    int canvas_h = cfg.force_h ? cfg.force_h : img_h;

    if (cfg.force_w == 0 && cfg.force_h == 0 && cfg.fit) {
        canvas_w = std::min(img_w, 640);
        canvas_h = img_h * canvas_w / img_w;
        if (canvas_h > 480) {
            canvas_h = std::min(img_h, 480);
            canvas_w = img_w * canvas_h / img_h;
        }
    }

    iris::Canvas canvas;
    if (!canvas.init(canvas_w, canvas_h)) {
        std::cerr << "Error: canvas init failed\n";
        stbi_image_free(img);
        return 1;
    }

    if (cfg.verbose) {
        std::cout << "Canvas: " << canvas_w << "x" << canvas_h << "\n";
    }

    if (!canvas.connect_overlay(cfg.display_socket)) {
        std::cerr << "Error: cannot connect to overlay '" << cfg.display_socket << "'\n";
        stbi_image_free(img);
        return 1;
    }

    canvas.clear(0xFF000000);

    int dst_x = 0, dst_y = 0;
    int dst_w = canvas_w, dst_h = canvas_h;

    if (!cfg.fit) {
        float scale = std::min((float)canvas_w / img_w, (float)canvas_h / img_h);
        dst_w = (int)(img_w * scale);
        dst_h = (int)(img_h * scale);
        dst_x = (canvas_w - dst_w) / 2;
        dst_y = (canvas_h - dst_h) / 2;
    }

    uint32_t* px = canvas.pixels();
    for (int y = 0; y < dst_h; y++) {
        int src_y = y * img_h / dst_h;
        for (int x = 0; x < dst_w; x++) {
            int src_x = x * img_w / dst_w;
            int src_idx = (src_y * img_w + src_x) * 4;
            uint8_t r = img[src_idx + 0];
            uint8_t g = img[src_idx + 1];
            uint8_t b = img[src_idx + 2];
            uint8_t a = img[src_idx + 3];
            px[(dst_y + y) * canvas_w + (dst_x + x)] =
                (a << 24) | (r << 16) | (g << 8) | b;
        }
    }

    canvas.present();

    if (cfg.verbose) {
        std::cout << "Tap the overlay or press Ctrl+C to exit\n";
    }

    while (g_running) {
        if (!canvas.connected()) {
            if (!canvas.connect_overlay(cfg.display_socket)) {
                usleep(500000);
                continue;
            }
            canvas.present();
        }

        if (!canvas.poll_commands()) {
            usleep(50000);
            continue;
        }

        if (canvas.touch_down()) {
            if (cfg.verbose) {
                std::cout << "Touch at " << canvas.touch_x() << "," << canvas.touch_y()
                          << " — exiting\n";
            }
            break;
        }
    }

    stbi_image_free(img);
    return 0;
}
