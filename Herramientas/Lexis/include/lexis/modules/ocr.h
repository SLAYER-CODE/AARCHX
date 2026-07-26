#pragma once

#include "lexis/module.h"

#include <string>
#include <vector>
#include <memory>

#ifdef LEXIS_HAS_TESSERACT
#include <tesseract/baseapi.h>
#include <leptonica/allheaders.h>
#endif

namespace lexis {

// ── OCR Module ───────────────────────────────────────────────────
// Wrapper de Tesseract OCR para reconocer texto en documentos.
// Detecta: líneas de texto, palabras, caracteres individuales.
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
    void set_page_seg_mode(int mode) { page_seg_mode_ = mode; }
    
    // Getters
    const std::string& language() const { return lang_; }
    float min_confidence() const { return min_confidence_; }
    int page_seg_mode() const { return page_seg_mode_; }
    
private:
    // Text extraction
    std::vector<TextBlock> extract_text(uint32_t* pixels, int w, int h);
    
    // Image preprocessing
    Pix* preprocess_image(const uint32_t* pixels, int w, int h);
    
    // State
    tesseract::TessBaseAPI* api_ = nullptr;
    std::string lang_ = "eng";
    std::string data_path_ = "/usr/share/tesseract-ocr/4.00/tessdata";
    float min_confidence_ = 0.3f;
    int page_seg_mode_ = 3;  // PSM_AUTO
    bool initialized_ = false;
};

} // namespace lexis
