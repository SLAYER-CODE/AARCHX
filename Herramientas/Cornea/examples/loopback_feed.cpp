/*
 * loopback_feed — escribe test pattern animado en v4l2loopback
 *
 * Útil para probar la captura desde una cámara virtual.
 *
 * Uso: loopback_feed [--device=/dev/video2] [--size=640x480] [--fps=30]
 *
 * Mientras este proceso corre, /dev/video2 emite frames como si
 * fuera una cámara real (válido para pruebas de iris::Camera).
 */

#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <cmath>
#include <string>
#include <algorithm>
#include <chrono>
#include <thread>
#include <vector>
#include <csignal>
#include <fcntl.h>
#include <unistd.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <linux/videodev2.h>

// fourcc
#ifndef V4L2_PIX_FMT_YUYV
#define V4L2_PIX_FMT_YUYV v4l2_fourcc('Y', 'U', 'Y', 'V')
#endif

struct Buffer {
    void* start = nullptr;
    size_t length = 0;
};

static bool g_running = true;

static void signal_handler(int) { g_running = false; }

// BGRA → YUYV conversion (simplified)
static void bgra_to_yuyv(const uint32_t* bgra, uint8_t* yuyv, int w, int h) {
    for (int i = 0; i < w * h / 2; i++) {
        int src_idx = i * 2;
        uint32_t p0 = bgra[src_idx];
        uint32_t p1 = bgra[src_idx + 1];

        int r0 = p0 & 0xFF; int g0 = (p0 >> 8) & 0xFF; int b0 = (p0 >> 16) & 0xFF;
        int r1 = p1 & 0xFF; int g1 = (p1 >> 8) & 0xFF; int b1 = (p1 >> 16) & 0xFF;

        int y0 = ((66 * r0 + 129 * g0 + 25 * b0 + 128) >> 8) + 16;
        int y1 = ((66 * r1 + 129 * g1 + 25 * b1 + 128) >> 8) + 16;
        int u  = ((-38 * r0 - 74 * g0 + 112 * b0 + 128) >> 8) + 128;
        int v  = ((112 * r0 - 94 * g0 - 18 * b0 + 128) >> 8) + 128;

        auto clamp8 = [](int v, int lo, int hi) { return std::max(lo, std::min(v, hi)); };
        yuyv[0] = clamp8(y0, 16, 235);
        yuyv[1] = clamp8(u, 16, 240);
        yuyv[2] = clamp8(y1, 16, 235);
        yuyv[3] = clamp8(v, 16, 240);
        yuyv += 4;
    }
}

// Test pattern (BGRA rainbow)
static void generate_pattern(uint32_t* pixels, int w, int h, int frame) {
    double t = frame * 0.02;
    for (int y = 0; y < h; y++) {
        for (int x = 0; x < w; x++) {
            double u = (double)x / w;
            double v_ = (double)y / h;
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
            double wave = std::sin(u * 20.0 + t * 3.0) * 0.3 + 0.7;
            r *= wave; g *= wave; b *= wave;
            if ((x % 32 == 0) || (y % 32 == 0)) { r *= 0.6; g *= 0.6; b *= 0.6; }
            uint32_t bgra = 0xFF000000;
            bgra |= ((uint8_t)(b * 255)) << 16;
            bgra |= ((uint8_t)(g * 255)) << 8;
            bgra |= ((uint8_t)(r * 255));
            pixels[y * w + x] = bgra;
        }
    }
}

int main(int argc, char* argv[]) {
    const char* device = "/dev/video2";
    int width = 640, height = 480, fps = 30;

    for (int i = 1; i < argc; i++) {
        std::string a = argv[i];
        if (a.rfind("--device=", 0) == 0) device = a.c_str() + 9;
        else if (a.rfind("--size=", 0) == 0) sscanf(a.c_str() + 7, "%dx%d", &width, &height);
        else if (a.rfind("--fps=", 0) == 0) fps = std::stoi(a.substr(6));
    }

    signal(SIGINT, signal_handler);
    signal(SIGTERM, signal_handler);

    int fd = open(device, O_RDWR);
    if (fd < 0) { perror("open"); return 1; }

    // Set format (VIDEO_OUTPUT for loopback)
    struct v4l2_format fmt;
    memset(&fmt, 0, sizeof(fmt));
    fmt.type = V4L2_BUF_TYPE_VIDEO_OUTPUT;
    fmt.fmt.pix.width = width;
    fmt.fmt.pix.height = height;
    fmt.fmt.pix.pixelformat = V4L2_PIX_FMT_YUYV;
    fmt.fmt.pix.field = V4L2_FIELD_NONE;
    if (ioctl(fd, VIDIOC_S_FMT, &fmt) < 0) { perror("S_FMT"); close(fd); return 1; }

    // Request buffers
    struct v4l2_requestbuffers req;
    memset(&req, 0, sizeof(req));
    req.count = 4;
    req.type = V4L2_BUF_TYPE_VIDEO_OUTPUT;
    req.memory = V4L2_MEMORY_MMAP;
    if (ioctl(fd, VIDIOC_REQBUFS, &req) < 0) { perror("REQBUFS"); close(fd); return 1; }

    std::vector<Buffer> buffers(req.count);
    for (uint32_t i = 0; i < req.count; i++) {
        struct v4l2_buffer buf;
        memset(&buf, 0, sizeof(buf));
        buf.type = V4L2_BUF_TYPE_VIDEO_OUTPUT;
        buf.memory = V4L2_MEMORY_MMAP;
        buf.index = i;
        if (ioctl(fd, VIDIOC_QUERYBUF, &buf) < 0) { perror("QUERYBUF"); close(fd); return 1; }
        buffers[i].length = buf.length;
        buffers[i].start = mmap(nullptr, buf.length, PROT_READ | PROT_WRITE, MAP_SHARED, fd, buf.m.offset);
        if (buffers[i].start == MAP_FAILED) { perror("mmap"); close(fd); return 1; }
    }

    // Queue all buffers
    for (uint32_t i = 0; i < req.count; i++) {
        struct v4l2_buffer buf;
        memset(&buf, 0, sizeof(buf));
        buf.type = V4L2_BUF_TYPE_VIDEO_OUTPUT;
        buf.memory = V4L2_MEMORY_MMAP;
        buf.index = i;
        if (ioctl(fd, VIDIOC_QBUF, &buf) < 0) { perror("QBUF"); close(fd); return 1; }
    }

    // Stream on
    int type = V4L2_BUF_TYPE_VIDEO_OUTPUT;
    if (ioctl(fd, VIDIOC_STREAMON, &type) < 0) { perror("STREAMON"); close(fd); return 1; }

    printf("[loopback_feed] Feeding %s %dx%d @%dfps\n", device, width, height, fps);
    printf("[loopback_feed] Press Ctrl+C to stop\n");

    std::vector<uint8_t> bgra_buf(static_cast<size_t>(width) * height * 4);
    std::vector<uint8_t> yuyv_buf(static_cast<size_t>(width) * height * 2);
    int frame = 0;

    while (g_running) {
        // Dequeue
        struct v4l2_buffer buf;
        memset(&buf, 0, sizeof(buf));
        buf.type = V4L2_BUF_TYPE_VIDEO_OUTPUT;
        buf.memory = V4L2_MEMORY_MMAP;
        if (ioctl(fd, VIDIOC_DQBUF, &buf) < 0) { if (errno == EAGAIN) continue; break; }

        // Generate test pattern
        generate_pattern((uint32_t*)bgra_buf.data(), width, height, frame++);
        bgra_to_yuyv((uint32_t*)bgra_buf.data(), yuyv_buf.data(), width, height);

        // Fill buffer
        size_t copy_size = buf.length < yuyv_buf.size() ? buf.length : yuyv_buf.size();
        memcpy(buffers[buf.index].start, yuyv_buf.data(), copy_size);

        // Re-queue
        ioctl(fd, VIDIOC_QBUF, &buf);

        std::this_thread::sleep_for(std::chrono::milliseconds(1000 / fps));
    }

    // Stream off
    type = V4L2_BUF_TYPE_VIDEO_OUTPUT;
    ioctl(fd, VIDIOC_STREAMOFF, &type);

    for (auto& b : buffers) munmap(b.start, b.length);
    close(fd);
    printf("[loopback_feed] Done.\n");
    return 0;
}
