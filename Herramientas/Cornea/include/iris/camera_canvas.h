#pragma once

#include "iris/canvas.h"

namespace iris {

// Extensión de Canvas para el modo servidor de cámara.
// Añade: listen(), accept_client(), recv_frame(), rotate(), draw_minimize_btn().
// La parte cliente (connect_overlay, present, drawing) viene de iris::Canvas base.
class CameraCanvas : public Canvas {
public:
    CameraCanvas() = default;
    ~CameraCanvas() override;

    // Modo servidor: escuchar en socket cam-* y recibir frames
    bool listen(const std::string& socket_name);
    bool accept_client();
    bool recv_frame();

    // Rotar el buffer interno (0-359, modulo 360, ±)
    bool rotate(int degrees);

    // Botón minimizar
    void draw_minimize_btn(int x, int y, int size = 34);

private:
    int listen_fd_ = -1;
};

} // namespace iris
