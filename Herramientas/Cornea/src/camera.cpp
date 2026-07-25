#include "iris/camera.h"

#include <cstring>
#include <cerrno>
#include <iostream>
#include <algorithm>

#include <fcntl.h>
#include <unistd.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <sys/select.h>
#include <linux/videodev2.h>

#ifndef V4L2_PIX_FMT_YUYV
#define V4L2_PIX_FMT_YUYV v4l2_fourcc('Y', 'U', 'Y', 'V')
#endif
#ifndef V4L2_PIX_FMT_MJPEG
#define V4L2_PIX_FMT_MJPEG v4l2_fourcc('M', 'J', 'P', 'G')
#endif

namespace iris {

Camera::Camera() {}

Camera::~Camera() {
    close();
}

bool Camera::detect_device_type() {
    struct v4l2_capability cap;
    std::memset(&cap, 0, sizeof(cap));
    if (ioctl(fd_, VIDIOC_QUERYCAP, &cap) < 0) {
        std::cerr << "[Iris] VIDIOC_QUERYCAP failed: " << strerror(errno) << "\n";
        return false;
    }

    if (cap.capabilities & V4L2_CAP_VIDEO_CAPTURE) {
        buf_type_ = V4L2_BUF_TYPE_VIDEO_CAPTURE;
        std::cerr << "[Iris] Device type: VIDEO_CAPTURE\n";
        return true;
    }
    if (cap.capabilities & V4L2_CAP_VIDEO_OUTPUT) {
        buf_type_ = V4L2_BUF_TYPE_VIDEO_OUTPUT;
        std::cerr << "[Iris] Device type: VIDEO_OUTPUT (loopback)\n";
        return true;
    }

    std::cerr << "[Iris] Device has neither CAPTURE nor OUTPUT capabilities\n";
    return false;
}

bool Camera::open(const char* device, int width, int height, int fps, uint32_t format) {
    if (fd_ >= 0) close();

    fd_ = ::open(device, O_RDWR);
    if (fd_ < 0) {
        std::cerr << "[Iris] Cannot open " << device << ": " << strerror(errno) << "\n";
        return false;
    }

    if (!detect_device_type()) {
        close();
        return false;
    }

    uint32_t desired = format ? format : V4L2_PIX_FMT_YUYV;
    if (!set_fmt(width, height, desired)) {
        if (desired != V4L2_PIX_FMT_YUYV) {
            std::cerr << "[Iris] Fallback to YUYV\n";
            if (!set_fmt(width, height, V4L2_PIX_FMT_YUYV)) {
                close();
                return false;
            }
        } else {
            close();
            return false;
        }
    }

    if (!set_fps(fps)) {
        std::cerr << "[Iris] Warning: could not set FPS\n";
    }

    if (!request_buffers_mmap()) {
        close();
        return false;
    }

    std::cerr << "[Iris] Camera opened: " << device
              << " " << fmt_width_ << "x" << fmt_height_
              << " fourcc=" << std::hex << pixel_format_ << std::dec
              << " type=" << (buf_type_ == V4L2_BUF_TYPE_VIDEO_CAPTURE ? "CAPTURE" : "OUTPUT")
              << "\n";
    return true;
}

bool Camera::set_fmt(int width, int height, uint32_t fourcc) {
    if (buf_type_ == 0) return false;

    struct v4l2_format fmt;
    std::memset(&fmt, 0, sizeof(fmt));
    fmt.type = buf_type_;
    fmt.fmt.pix.width = width;
    fmt.fmt.pix.height = height;
    fmt.fmt.pix.pixelformat = fourcc;
    fmt.fmt.pix.field = V4L2_FIELD_ANY;

    if (ioctl(fd_, VIDIOC_S_FMT, &fmt) < 0) {
        std::cerr << "[Iris] VIDIOC_S_FMT failed: " << strerror(errno) << "\n";
        return false;
    }

    fmt_width_ = fmt.fmt.pix.width;
    fmt_height_ = fmt.fmt.pix.height;
    pixel_format_ = fmt.fmt.pix.pixelformat;

    return true;
}

bool Camera::set_fps(int fps) {
    if (buf_type_ != V4L2_BUF_TYPE_VIDEO_CAPTURE) return true;  // OUTPUT devices don't support FPS

    struct v4l2_streamparm parm;
    std::memset(&parm, 0, sizeof(parm));
    parm.type = V4L2_BUF_TYPE_VIDEO_CAPTURE;
    parm.parm.capture.timeperframe.numerator = 1;
    parm.parm.capture.timeperframe.denominator = fps;

    if (ioctl(fd_, VIDIOC_S_PARM, &parm) < 0) {
        return false;
    }
    return true;
}

bool Camera::request_buffers_mmap() {
    if (buf_type_ == 0) return false;

    struct v4l2_requestbuffers req;
    std::memset(&req, 0, sizeof(req));
    req.count = 4;
    req.type = buf_type_;
    req.memory = V4L2_MEMORY_MMAP;

    if (ioctl(fd_, VIDIOC_REQBUFS, &req) < 0) {
        std::cerr << "[Iris] VIDIOC_REQBUFS failed: " << strerror(errno) << "\n";
        return false;
    }

    buffer_count_ = req.count;
    buffers_ = new V4l2Buffer[buffer_count_];

    for (uint32_t i = 0; i < buffer_count_; i++) {
        struct v4l2_buffer buf;
        std::memset(&buf, 0, sizeof(buf));
        buf.type = buf_type_;
        buf.memory = V4L2_MEMORY_MMAP;
        buf.index = i;

        if (ioctl(fd_, VIDIOC_QUERYBUF, &buf) < 0) {
            std::cerr << "[Iris] VIDIOC_QUERYBUF " << i << " failed\n";
            free_buffers();
            return false;
        }

        buffers_[i].length = buf.length;
        buffers_[i].start = mmap(nullptr, buf.length,
                                  PROT_READ | PROT_WRITE,
                                  MAP_SHARED, fd_, buf.m.offset);
        if (buffers_[i].start == MAP_FAILED) {
            std::cerr << "[Iris] mmap buffer " << i << " failed: "
                      << strerror(errno) << "\n";
            free_buffers();
            return false;
        }
    }

    return true;
}

void Camera::free_buffers() {
    if (buffers_) {
        for (uint32_t i = 0; i < buffer_count_; i++) {
            if (buffers_[i].start && buffers_[i].start != MAP_FAILED) {
                munmap(buffers_[i].start, buffers_[i].length);
            }
        }
        delete[] buffers_;
        buffers_ = nullptr;
    }
    buffer_count_ = 0;
}

bool Camera::start() {
    if (buf_type_ == 0) return false;

    for (uint32_t i = 0; i < buffer_count_; i++) {
        struct v4l2_buffer buf;
        std::memset(&buf, 0, sizeof(buf));
        buf.type = buf_type_;
        buf.memory = V4L2_MEMORY_MMAP;
        buf.index = i;

        if (ioctl(fd_, VIDIOC_QBUF, &buf) < 0) {
            std::cerr << "[Iris] VIDIOC_QBUF " << i << " failed\n";
            return false;
        }
    }

    int type = buf_type_;
    if (ioctl(fd_, VIDIOC_STREAMON, &type) < 0) {
        std::cerr << "[Iris] VIDIOC_STREAMON failed: " << strerror(errno) << "\n";
        return false;
    }

    return true;
}

bool Camera::capture(uint8_t* out_bgra, size_t size) {
    if (fd_ < 0 || !buffers_ || buf_type_ == 0) return false;

    struct v4l2_buffer buf;
    std::memset(&buf, 0, sizeof(buf));
    buf.type = buf_type_;
    buf.memory = V4L2_MEMORY_MMAP;

    if (ioctl(fd_, VIDIOC_DQBUF, &buf) < 0) {
        if (errno == EAGAIN) return false;
        std::cerr << "[Iris] VIDIOC_DQBUF failed: " << strerror(errno) << "\n";
        return false;
    }

    size_t expected_pixels = static_cast<size_t>(fmt_width_) * fmt_height_ * 4;
    if (size < expected_pixels) {
        ioctl(fd_, VIDIOC_QBUF, &buf);
        return false;
    }

    const uint8_t* data = static_cast<const uint8_t*>(buffers_[buf.index].start);
    size_t data_len = buf.bytesused;

    switch (pixel_format_) {
        case V4L2_PIX_FMT_YUYV:
            yuyv_to_bgra(data, out_bgra, fmt_width_, fmt_height_);
            break;
        case V4L2_PIX_FMT_MJPEG:
            if (!mjpeg_to_bgra(data, data_len, out_bgra, fmt_width_, fmt_height_)) {
                std::memset(out_bgra, 0, expected_pixels);
            }
            break;
        default:
            std::cerr << "[Iris] Unknown pixel format\n";
            std::memset(out_bgra, 0, expected_pixels);
            break;
    }

    ioctl(fd_, VIDIOC_QBUF, &buf);
    return true;
}

void Camera::stop() {
    if (fd_ < 0 || buf_type_ == 0) return;
    int type = buf_type_;
    ioctl(fd_, VIDIOC_STREAMOFF, &type);
}

void Camera::close() {
    stop();
    free_buffers();
    if (fd_ >= 0) {
        ::close(fd_);
        fd_ = -1;
    }
    buf_type_ = 0;
}

bool Camera::set_exposure(int us) {
    if (fd_ < 0) return false;

    struct v4l2_ext_controls ctrls;
    struct v4l2_ext_control ctrl;
    std::memset(&ctrls, 0, sizeof(ctrls));
    std::memset(&ctrl, 0, sizeof(ctrl));

    ctrl.id = V4L2_CID_EXPOSURE_ABSOLUTE;
    ctrl.value = us;

    ctrls.ctrl_class = V4L2_CTRL_CLASS_CAMERA;
    ctrls.count = 1;
    ctrls.controls = &ctrl;

    if (ioctl(fd_, VIDIOC_S_EXT_CTRLS, &ctrls) < 0) {
        std::cerr << "[Iris] set_exposure failed: " << strerror(errno) << "\n";
        return false;
    }
    return true;
}

bool Camera::set_gain(int db) {
    if (fd_ < 0) return false;

    struct v4l2_ext_controls ctrls;
    struct v4l2_ext_control ctrl;
    std::memset(&ctrls, 0, sizeof(ctrls));
    std::memset(&ctrl, 0, sizeof(ctrl));

    ctrl.id = V4L2_CID_GAIN;
    ctrl.value = db;

    ctrls.ctrl_class = V4L2_CTRL_CLASS_CAMERA;
    ctrls.count = 1;
    ctrls.controls = &ctrl;

    if (ioctl(fd_, VIDIOC_S_EXT_CTRLS, &ctrls) < 0) {
        std::cerr << "[Iris] set_gain failed: " << strerror(errno) << "\n";
        return false;
    }
    return true;
}

bool Camera::set_iteration_mode(bool on) {
    if (fd_ < 0) return false;

    struct v4l2_ext_controls ctrls;
    struct v4l2_ext_control ctrl[2];
    std::memset(&ctrls, 0, sizeof(ctrls));
    std::memset(&ctrl, 0, sizeof(ctrl));

    ctrl[0].id = V4L2_CID_EXPOSURE_AUTO;
    ctrl[0].value = on ? 0 : 1;

    ctrl[1].id = V4L2_CID_AUTOGAIN;
    ctrl[1].value = on ? 0 : 1;

    ctrls.ctrl_class = V4L2_CTRL_CLASS_CAMERA;
    ctrls.count = 2;
    ctrls.controls = ctrl;

    if (ioctl(fd_, VIDIOC_S_EXT_CTRLS, &ctrls) < 0) {
        std::cerr << "[Iris] set_iteration_mode failed: " << strerror(errno) << "\n";
        return false;
    }
    return true;
}

void Camera::yuyv_to_bgra(const uint8_t* yuyv, uint8_t* bgra,
                           int width, int height) {
    for (int row = 0; row < height; row++) {
        for (int col = 0; col < width; col += 2) {
            int idx = row * width * 2 + col * 2;
            int y0 = yuyv[idx];
            int u  = yuyv[idx + 1];
            int y1 = yuyv[idx + 2];
            int v  = yuyv[idx + 3];

            auto yuv_to_rgb = [](int y, int u, int v) -> uint32_t {
                int c = y - 16;
                int d = u - 128;
                int e = v - 128;
                int r = std::clamp((298 * c + 409 * e + 128) >> 8, 0, 255);
                int g = std::clamp((298 * c - 100 * d - 208 * e + 128) >> 8, 0, 255);
                int b = std::clamp((298 * c + 516 * d + 128) >> 8, 0, 255);
                return 0xFF000000 | (b << 16) | (g << 8) | r;
            };

            int out_idx = row * width + col;
            ((uint32_t*)bgra)[out_idx]     = yuv_to_rgb(y0, u, v);
            ((uint32_t*)bgra)[out_idx + 1] = yuv_to_rgb(y1, u, v);
        }
    }
}

bool Camera::mjpeg_to_bgra(const uint8_t* mjpeg, size_t mjpeg_size,
                            uint8_t* bgra, int width, int height) {
    (void)mjpeg; (void)mjpeg_size; (void)bgra; (void)width; (void)height;
    std::cerr << "[Iris] MJPEG decode not yet implemented\n";
    return false;
}

} // namespace iris
