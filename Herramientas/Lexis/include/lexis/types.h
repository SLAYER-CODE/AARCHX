#pragma once

#include <cstdint>
#include <cstddef>
#include <string>
#include <vector>

namespace lexis {

// ── Document types ─────────────────────────────────────────────
enum class DocType : uint8_t {
    UNKNOWN     = 0,
    INVOICE     = 1,    // Facturas
    RECEIPT     = 2,    // Recibos
    LABEL       = 3,    // Etiquetas de producto
    ID_CARD     = 4,    // Documentos de identidad
    CONTRACT    = 5,    // Contratos
    LETTER      = 6,    // Cartas
    FORM        = 7,    // Formularios
    TICKET      = 8,    // Boletos/tickets
    OTHER       = 99
};

// ── OCR text block (región detectada por cámara) ────────────────
struct TextBlock {
    std::string text;           // Texto reconocido
    float confidence = 0.0f;    // Confianza OCR (0.0-1.0)
    int x = 0, y = 0;          // Posición en frame
    int width = 0, height = 0;  // Tamaño del bounding box
    int line_number = 0;        // Número de línea
};

// ── Key-value pair (campo extraído del documento) ───────────────
struct Field {
    std::string key;            // Nombre del campo (ej: "fecha", "total", "nombre")
    std::string value;          // Valor extraído
    float confidence = 0.0f;    // Confianza de la extracción
    int x = 0, y = 0;          // Posición en documento
    int width = 0, height = 0;  // Tamaño del bounding box
};

// ── Document info (resultado del análisis) ──────────────────────
struct DocumentInfo {
    // Tipo de documento
    DocType type = DocType::UNKNOWN;
    std::string type_label;     // "Invoice", "Receipt", etc.
    
    // Texto completo
    std::string full_text;      // Texto completo reconocido
    
    // Campos extraídos
    std::vector<Field> fields;
    
    // Raw OCR data
    std::vector<TextBlock> text_blocks;
    
    // Confidence scores
    float ocr_confidence = 0.0f;
    float classification_confidence = 0.0f;
    
    // Metadata
    int line_count = 0;
    int word_count = 0;
    int char_count = 0;
};

// ── Frame processing result ────────────────────────────────────
struct FrameResult {
    DocumentInfo document;      // Documento analizado
    int64_t timestamp = 0;      // Timestamp del frame procesado
    bool valid = false;         // Si se detectó algo
};

// ── Protocol constants (reuses Iris/Mandela MNDL protocol) ──────
static constexpr uint32_t OVERLAY_MAGIC = 0x4D4E444C;  // "MNDL"
static constexpr int OVERLAY_HEADER_SIZE = 20;

} // namespace lexis
