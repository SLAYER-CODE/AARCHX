#include "iris/types.h"

#include <cstdint>
#include <algorithm>
#include <cstring>

namespace iris {

// YUV420 planar → BGRA (útil para código que recibe YUV420 de otras fuentes)
// y_plane: puntero al plano Y (width × height bytes)
// u_plane: puntero al plano U (width/2 × height/2 bytes)
// v_plane: puntero al plano V (width/2 × height/2 bytes)
// stride_y, stride_u, stride_v: stride de cada plano (pueden ser > width)
// out_bgra: buffer de salida (width × height × 4 bytes)
void yuv420_to_bgra(const uint8_t* y_plane, const uint8_t* u_plane,
                    const uint8_t* v_plane,
                    int stride_y, int stride_u, int stride_v,
                    uint8_t* out_bgra, int width, int height) {
    for (int row = 0; row < height; row++) {
        for (int col = 0; col < width; col++) {
            int y = y_plane[row * stride_y + col];

            int uv_row = row / 2;
            int uv_col = col / 2;
            int u = u_plane[uv_row * stride_u + uv_col];
            int v = v_plane[uv_row * stride_v + uv_col];

            // YUV → RGB (rec.601)
            int c = y - 16;
            int d = u - 128;
            int e = v - 128;
            int r = std::clamp((298 * c + 409 * e + 128) >> 8, 0, 255);
            int g = std::clamp((298 * c - 100 * d - 208 * e + 128) >> 8, 0, 255);
            int b = std::clamp((298 * c + 516 * d + 128) >> 8, 0, 255);

            // BGRA little-endian (Skia format)
            uint32_t* pixel = reinterpret_cast<uint32_t*>(
                out_bgra + (row * width + col) * 4);
            *pixel = 0xFF000000 | (b << 16) | (g << 8) | r;
        }
    }
}

// NV12 (YUV420 con UV interleaved) → BGRA
void nv12_to_bgra(const uint8_t* y_plane, const uint8_t* uv_plane,
                  int stride_y, int stride_uv,
                  uint8_t* out_bgra, int width, int height) {
    for (int row = 0; row < height; row++) {
        for (int col = 0; col < width; col++) {
            int y = y_plane[row * stride_y + col];

            int uv_row = row / 2;
            int uv_col = (col / 2) * 2;
            int u = uv_plane[uv_row * stride_uv + uv_col];
            int v = uv_plane[uv_row * stride_uv + uv_col + 1];

            int c = y - 16;
            int d = u - 128;
            int e = v - 128;
            int r = std::clamp((298 * c + 409 * e + 128) >> 8, 0, 255);
            int g = std::clamp((298 * c - 100 * d - 208 * e + 128) >> 8, 0, 255);
            int b = std::clamp((298 * c + 516 * d + 128) >> 8, 0, 255);

            uint32_t* pixel = reinterpret_cast<uint32_t*>(
                out_bgra + (row * width + col) * 4);
            *pixel = 0xFF000000 | (b << 16) | (g << 8) | r;
        }
    }
}

} // namespace iris
