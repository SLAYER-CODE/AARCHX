#pragma once

#include "iris/types.h"

#include <cstdint>
#include <string>

namespace iris {

// Buffer de captura v4l2 (mmap'd)
struct V4l2Buffer {
    void* start = nullptr;
    size_t length = 0;
};

// Camera: captura v4l2 desde /dev/videoN.
// Soporta YUYV y MJPEG; convierte internamente a BGRA.
// Permite múltiples instancias (frontal + trasera).
class Camera {
public:
    Camera();
    ~Camera();

    // Abrir dispositivo y negociar formato.
    // width/height: resolución deseada (el driver elegirá la más cercana).
    // fps: frames per segundo deseados (30 por defecto).
    // format: cuatrocc (v4l2_fourcc), ej. 'YUYV' o 'MJPG' (0 = YUYV por defecto).
    bool open(const char* device, int width, int height, int fps = 30, uint32_t format = 0);

    // Iniciar streaming
    bool start();

    // Capturar un frame. out_bgra debe tener al menos width*height*4 bytes.
    // Retorna true si se capturó un frame, false si timeout/error.
    bool capture(uint8_t* out_bgra, size_t size);

    // Detener streaming
    void stop();

    // Cerrar dispositivo y liberar recursos
    void close();

    // === Control de cámara ===

    // Exposición manual en microsegundos (v4l2 V4L2_CID_EXPOSURE_ABSOLUTE)
    bool set_exposure(int us);

    // Ganancia en dB (v4l2 V4L2_CID_GAIN)
    bool set_gain(int db);

    // Modo iteración: desactiva auto-exposición y auto-ganancia,
    // útil para capturas forzadas sin ajuste automático.
    bool set_iteration_mode(bool on);

    // Consultar si la cámara está abierta
    bool is_open() const { return fd_ >= 0; }

    // Resolución real negociada
    int width() const { return fmt_width_; }
    int height() const { return fmt_height_; }

private:
    int fd_ = -1;
    int fmt_width_ = 0;
    int fmt_height_ = 0;
    uint32_t pixel_format_ = 0;  // v4l2 fourcc real (YUYV o MJPG)
    uint32_t buffer_count_ = 0;
    int buf_type_ = 0;           // V4L2_BUF_TYPE_VIDEO_CAPTURE o VIDEO_OUTPUT
    V4l2Buffer* buffers_ = nullptr;

    bool detect_device_type();

    bool request_buffers_mmap();
    void free_buffers();
    bool set_fmt(int width, int height, uint32_t fourcc);
    bool set_fps(int fps);

    // YUYV -> BGRA (por línea)
    void yuyv_to_bgra(const uint8_t* yuyv, uint8_t* bgra, int width, int height);

    // MJPEG -> BGRA (decode simple, requiere TinyJPEG o similar)
    // Por ahora MJPEG no está implementado — usamos YUYV.
    bool mjpeg_to_bgra(const uint8_t* mjpeg, size_t mjpeg_size,
                       uint8_t* bgra, int width, int height);
};

} // namespace iris
