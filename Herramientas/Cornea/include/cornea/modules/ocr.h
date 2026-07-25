#pragma once

#include "cornea/module.h"

#include <string>
#include <vector>
#include <memory>

#ifdef CORNEA_HAS_TESSERACT
#include <tesseract/baseapi.h>
#include <leptonica/allheaders.h>
#endif

namespace cornea {

// ── OCR Module ───────────────────────────────────────────────────
// Wrapper de Tesseract OCR para reconocer texto en frames de cámara.
// Detecta: números de modelo, seriales, marcas, firmware versions.
class OCRModule : public AnalysisModule {
public:
    OCRModule();
    ~OCRModule();
    
    const char* name() const override { return "ocr"; }
    const char* description() const override { return "Tesseract OCR text recognition"; }
    
    bool init() override;
    void shutdown() override;
    void process_frame(uint32_t* pixels, int w, int h, 
                       FrameResult& result) override;
    
    // Configuration
    void set_language(const std::string& lang) { lang_ = lang; }
    void set_data_path(const std::string& path) { data_path_ = path; }
    void set_min_confidence(float min) { min_confidence_ = min; }
    
    // Getters
    const std::string& language() const { return lang_; }
    float min_confidence() const { return min_confidence_; }
    
private:
    // Text extraction
    std::vector<TextBlock> extract_text(uint32_t* pixels, int w, int h);
    
    // Pattern matching for device info
    std::string find_model(const std::vector<TextBlock>& blocks);
    std::string find_serial(const std::vector<TextBlock>& blocks);
    std::string find_firmware(const std::vector<TextBlock>& blocks);
    std::string find_vendor_from_text(const std::vector<TextBlock>& blocks);
    
    // Image preprocessing
    Pix* preprocess_image(const uint32_t* pixels, int w, int h);
    
    // Known patterns (regex)
    bool matches_model_pattern(const std::string& text);
    bool matches_serial_pattern(const std::string& text);
    bool matches_firmware_pattern(const std::string& text);
    
    // State
    tesseract::TessBaseAPI* api_ = nullptr;
    std::string lang_ = "eng";
    std::string data_path_ = "/usr/share/tesseract-ocr/4.00/tessdata";
    float min_confidence_ = 0.3f;
    bool initialized_ = false;
};

} // namespace cornea

