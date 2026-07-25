#pragma once

#include <cstdint>
#include <cstddef>
#include <string>
#include <vector>

namespace cornea {

// ── Device types (reconocidos por cámara) ────────────────────────
enum class DeviceType : uint8_t {
    UNKNOWN     = 0,
    PHONE       = 1,    // Smartphones (Android, iOS)
    IoT         = 2,    // IoT devices (cámaras, sensores, etc.)
    ROUTER      = 3,    // Routers, access points
    COMPUTER    = 4,    // Desktops, laptops
    SERVER      = 5,    // Servers, NAS
    PRINTER     = 6,    // Network printers
    TV          = 7,    // Smart TVs, streaming
    SWITCH      = 8,    // Network switches
    CAMERA      = 9,    // IP cameras
    OTHER       = 99
};

// ── Device states ──────────────────────────────────────────────
enum class DeviceState : uint8_t {
    UNKNOWN     = 0,
    ON          = 1,    // Encendido, activo
    OFF         = 2,    // Apagado
    SLEEP       = 3,    // Modo suspensión/standby
    BOOTING     = 4,    // Iniciando
    ERROR       = 5,    // Error/LED rojo
};

// ── OCR text block (región detectada por cámara) ─────────────────
struct TextBlock {
    std::string text;           // Texto reconocido
    float confidence = 0.0f;    // Confianza OCR (0.0-1.0)
    int x = 0, y = 0;          // Posición en frame
    int width = 0, height = 0;  // Tamaño del bounding box
};

// ── Logo match (template matching result) ────────────────────────
struct LogoMatch {
    std::string vendor;         // Nombre del fabricante
    float confidence = 0.0f;    // Confianza del match (0.0-1.0)
    int x = 0, y = 0;          // Posición en frame
    int width = 0, height = 0;  // Tamaño del template match
    std::string template_file;  // Archivo template usado
};

// ── Device info (resultado del reconocimiento) ───────────────────
struct DeviceInfo {
    // Identificación
    DeviceType type = DeviceType::UNKNOWN;
    DeviceState state = DeviceState::UNKNOWN;
    std::string type_label;     // "Smartphone", "Router", etc.
    std::string state_label;    // "ON", "OFF", "SLEEP", etc.
    std::string vendor;         // Fabricante (de logo o OCR)
    std::string model;          // Modelo (de OCR o DB)
    std::string serial;         // Número de serie (de OCR)
    std::string firmware;       // Firmware version (de OCR)
    
    // Confidence scores
    float ocr_confidence = 0.0f;
    float logo_confidence = 0.0f;
    float classification_confidence = 0.0f;  // Device type classification
    
    // OCR raw data
    std::vector<TextBlock> text_blocks;   // Todos los textos detectados
    std::vector<LogoMatch> logo_matches;  // Todos los logos detectados
    
    // From database
    struct Vulnerability {
        std::string id;          // CVE-YYYY-NNNN
        std::string severity;    // CRITICAL, HIGH, MEDIUM, LOW, INFO
        std::string title;       // Descripción corta
        std::string detail;      // Detalle completo
        std::string solution;    // Remediación
    };
    std::vector<Vulnerability> vulns;
    
    struct Credential {
        std::string service;     // ssh, http, telnet, rtsp
        std::string username;
        std::string password;
        std::string source;      // manufacturer, common, leaked
    };
    std::vector<Credential> default_creds;
};

// ── Visual detection (YOLO bounding box) ─────────────────────────
struct VisualDetection {
    int class_id = 0;
    std::string class_name;
    float confidence = 0.0f;
    float x = 0.0f, y = 0.0f;
    float w = 0.0f, h = 0.0f;
};

// ── Frame processing result ──────────────────────────────────────
struct FrameResult {
    DeviceInfo device;          // Dispositivo reconocido
    int64_t timestamp = 0;      // Timestamp del frame procesado
    bool valid = false;         // Si se detectó algo
    std::vector<VisualDetection> visual_detections;  // YOLO detections
};

// ── Protocol constants (reuses Iris/Mandela MNDL protocol) ──────
static constexpr uint32_t OVERLAY_MAGIC = 0x4D4E444C;  // "MNDL"
static constexpr int OVERLAY_HEADER_SIZE = 20;

} // namespace cornea

