#pragma once

#include <cstdint>
#include <cstddef>

namespace ac {

// ── Protocolo canvas-display (20 bytes, con magic MNDL) ──────────
// Herramienta nativa → CanvasSocketServer.kt (Android overlay)
static constexpr uint32_t OVERLAY_MAGIC = 0x4D4E444C;  // "MNDL"
static constexpr int OVERLAY_HEADER_SIZE = 20;

// ── Protocolo cam-* (12 bytes, sin magic) ─────────────────────────
// CameraFrameSender (Android) → Herramienta nativa
struct FrameHeader {
    uint32_t frame_id;
    uint32_t width;
    uint32_t height;
} __attribute__((packed));
static constexpr int FRAME_HEADER_SIZE = sizeof(FrameHeader);  // 12

static constexpr int MAX_FRAME_BYTES = 4 * 1920 * 1080;  // 1080p raw BGRA

static constexpr int SOCKET_BACKLOG = 4;

} // namespace ac
