#include "iris/camera_canvas.h"

#include <cstring>
#include <cerrno>
#include <cmath>
#include <algorithm>
#include <iostream>

#include <unistd.h>
#include <sys/socket.h>
#include <sys/un.h>

namespace iris {

CameraCanvas::~CameraCanvas() {
    if (listen_fd_ >= 0) {
        close(listen_fd_);
        listen_fd_ = -1;
    }
}

// ── Server mode ───────────────────────────────────────────────────

bool CameraCanvas::listen(const std::string& socket_name) {
    if (listen_fd_ >= 0) {
        close(listen_fd_);
        listen_fd_ = -1;
    }

    listen_fd_ = socket(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0);
    if (listen_fd_ < 0) {
        std::cerr << "[CameraCanvas] socket() failed: " << strerror(errno) << "\n";
        return false;
    }

    {
        int opt = 1;
        setsockopt(listen_fd_, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt));
    }

    struct sockaddr_un addr;
    std::memset(&addr, 0, sizeof(addr));
    addr.sun_family = AF_UNIX;

    size_t name_len = socket_name.size();
    if (name_len > sizeof(addr.sun_path) - 1)
        name_len = sizeof(addr.sun_path) - 1;
    std::memcpy(addr.sun_path + 1, socket_name.data(), name_len);

    socklen_t addr_len = offsetof(struct sockaddr_un, sun_path) + 1 + name_len;

    if (::bind(listen_fd_, (struct sockaddr*)&addr, addr_len) < 0) {
        std::cerr << "[CameraCanvas] bind('" << socket_name << "') failed: "
                  << strerror(errno) << "\n";
        close(listen_fd_);
        listen_fd_ = -1;
        return false;
    }

    if (::listen(listen_fd_, SOCKET_BACKLOG) < 0) {
        std::cerr << "[CameraCanvas] listen() failed: " << strerror(errno) << "\n";
        close(listen_fd_);
        listen_fd_ = -1;
        return false;
    }

    socket_name_ = socket_name;
    std::cerr << "[CameraCanvas] Listening on '" << socket_name_ << "'\n";
    return true;
}

bool CameraCanvas::accept_client() {
    if (listen_fd_ < 0) {
        std::cerr << "[CameraCanvas] No listening socket\n";
        return false;
    }

    struct sockaddr_un peer;
    socklen_t peer_len = sizeof(peer);
    int client_fd = ::accept(listen_fd_, (struct sockaddr*)&peer, &peer_len);
    if (client_fd < 0) {
        std::cerr << "[CameraCanvas] accept() failed: " << strerror(errno) << "\n";
        return false;
    }

    if (socket_fd_ >= 0) {
        close(socket_fd_);
    }
    socket_fd_ = client_fd;

    std::cerr << "[CameraCanvas] Client accepted\n";
    return true;
}

static bool read_all(int fd, void* buf, size_t len) {
    size_t total = 0;
    while (total < len) {
        ssize_t n = ::read(fd, (char*)buf + total, len - total);
        if (n <= 0) return false;
        total += n;
    }
    return true;
}

bool CameraCanvas::recv_frame() {
    if (socket_fd_ < 0) return false;

    FrameHeader header;
    if (!read_all(socket_fd_, &header, FRAME_HEADER_SIZE)) {
        std::cerr << "[CameraCanvas] Client disconnected (header)\n";
        close(socket_fd_);
        socket_fd_ = -1;
        return false;
    }

    uint32_t w = header.width;
    uint32_t h = header.height;
    size_t pixel_bytes = static_cast<size_t>(w) * h * 4;

    if (pixel_bytes == 0 || pixel_bytes > MAX_FRAME_BYTES) {
        std::cerr << "[CameraCanvas] Invalid frame size: " << w << "x" << h
                  << " = " << pixel_bytes << "\n";
        close(socket_fd_);
        socket_fd_ = -1;
        return false;
    }

    if (static_cast<int>(w) != width_ || static_cast<int>(h) != height_) {
        width_ = static_cast<int>(w);
        height_ = static_cast<int>(h);
        pixels_.resize(width_ * height_, 0xFF000000);
    }

    if (!read_all(socket_fd_, pixels_.data(), pixel_bytes)) {
        std::cerr << "[CameraCanvas] Client disconnected (pixels)\n";
        return false;
    }

    frame_id_ = static_cast<int>(header.frame_id);

#ifdef MANDELA_USE_SKIA
    rebuild_skia_surface();
#endif

    return true;
}

// ── Rotation ──────────────────────────────────────────────────────

bool CameraCanvas::rotate(int degrees) {
    degrees %= 360;
    if (degrees < 0) degrees += 360;

    if (degrees == 0) return true;
    if (pixels_.empty() || width_ <= 0 || height_ <= 0) return false;

    int src_w = width_;
    int src_h = height_;

    if (degrees == 180) {
        for (int r = 0; r < src_h / 2; r++) {
            int opp_r = src_h - 1 - r;
            for (int c = 0; c < src_w; c++) {
                int opp_c = src_w - 1 - c;
                std::swap(pixels_[r * src_w + c], pixels_[opp_r * src_w + opp_c]);
            }
        }
        if (src_h % 2 == 1) {
            int mid_r = src_h / 2;
            for (int c = 0; c < src_w / 2; c++) {
                int opp_c = src_w - 1 - c;
                std::swap(pixels_[mid_r * src_w + c], pixels_[mid_r * src_w + opp_c]);
            }
        }
        goto rebuild;
    }

    if (degrees == 90 || degrees == 270) {
        int dst_w = src_h;
        int dst_h = src_w;
        std::vector<uint32_t> tmp(static_cast<size_t>(dst_w) * dst_h);

        for (int r = 0; r < src_h; r++) {
            int src_off = r * src_w;
            for (int c = 0; c < src_w; c++) {
                uint32_t px = pixels_[src_off + c];
                if (degrees == 90) {
                    tmp[c * dst_w + (src_h - 1 - r)] = px;
                } else {
                    tmp[(src_w - 1 - c) * dst_w + r] = px;
                }
            }
        }

        pixels_ = std::move(tmp);
        width_ = dst_w;
        height_ = dst_h;
        goto rebuild;
    }

    {
        double rad = degrees * M_PI / 180.0;
        double cos_a = std::cos(rad);
        double sin_a = std::sin(rad);

        double abs_cos = std::abs(cos_a);
        double abs_sin = std::abs(sin_a);
        int dst_w = static_cast<int>(std::ceil(src_w * abs_cos + src_h * abs_sin));
        int dst_h = static_cast<int>(std::ceil(src_w * abs_sin + src_h * abs_cos));

        double cx = src_w / 2.0;
        double cy = src_h / 2.0;
        double dx = dst_w / 2.0;
        double dy = dst_h / 2.0;

        std::vector<uint32_t> tmp(static_cast<size_t>(dst_w) * dst_h, 0x00000000);

        for (int y = 0; y < dst_h; y++) {
            for (int x = 0; x < dst_w; x++) {
                double px = x - dx;
                double py = y - dy;
                double sx = cos_a * px + sin_a * py + cx;
                double sy = -sin_a * px + cos_a * py + cy;

                if (sx < 0 || sx >= src_w - 1 || sy < 0 || sy >= src_h - 1)
                    continue;

                int ix = static_cast<int>(sx);
                int iy = static_cast<int>(sy);
                double fx = sx - ix;
                double fy = sy - iy;

                uint32_t p00 = pixels_[iy * src_w + ix];
                uint32_t p10 = pixels_[iy * src_w + ix + 1];
                uint32_t p01 = pixels_[(iy + 1) * src_w + ix];
                uint32_t p11 = pixels_[(iy + 1) * src_w + ix + 1];

                auto lerp = [&](int shift) -> int {
                    int v00 = (p00 >> shift) & 0xFF;
                    int v10 = (p10 >> shift) & 0xFF;
                    int v01 = (p01 >> shift) & 0xFF;
                    int v11 = (p11 >> shift) & 0xFF;
                    double top = v00 + (v10 - v00) * fx;
                    double bot = v01 + (v11 - v01) * fx;
                    return static_cast<int>(top + (bot - top) * fy);
                };

                int b = lerp(0);
                int g = lerp(8);
                int r = lerp(16);
                int a = lerp(24);

                tmp[y * dst_w + x] =
                    (static_cast<uint32_t>(a) << 24) |
                    (static_cast<uint32_t>(r) << 16) |
                    (static_cast<uint32_t>(g) << 8) |
                    static_cast<uint32_t>(b);
            }
        }

        pixels_ = std::move(tmp);
        width_ = dst_w;
        height_ = dst_h;
    }

rebuild:
#ifdef MANDELA_USE_SKIA
    rebuild_skia_surface();
#endif
    return true;
}

// ── Minimize button ───────────────────────────────────────────────

void CameraCanvas::draw_minimize_btn(int x, int y, int size) {
    int half = size / 2;
    int r2 = half * half;
    for (int dy = -half; dy <= half; dy++) {
        for (int dx = -half; dx <= half; dx++) {
            int px = x + dx;
            int py = y + dy;
            if (px < 0 || px >= width_ || py < 0 || py >= height_)
                continue;
            if (dx * dx + dy * dy <= r2)
                pixels_[py * width_ + px] = 0xCC222222;
        }
    }
    int lw = size / 3;
    if (lw < 3) lw = 3;
    int lx1 = x - lw;
    int lx2 = x + lw;
    for (int dx = lx1; dx <= lx2; dx++) {
        if (dx >= 0 && dx < width_ && y >= 0 && y < height_)
            pixels_[y * width_ + dx] = 0xFFFF4444;
    }
}

} // namespace iris
