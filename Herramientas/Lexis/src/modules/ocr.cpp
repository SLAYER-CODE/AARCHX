#include "lexis/modules/ocr.h"

#include <iostream>
#include <regex>
#include <algorithm>
#include <cstring>
#include <cmath>

#ifdef LEXIS_HAS_TESSERACT
#include <tesseract/baseapi.h>
#include <leptonica/allheaders.h>
#endif

namespace lexis {

OCRModule::OCRModule() {}

OCRModule::~OCRModule() {
    shutdown();
}

bool OCRModule::init() {
#ifdef LEXIS_HAS_TESSERACT
    api_ = new tesseract::TessBaseAPI();
    
    // Init Tesseract: data path + language
    int ret = api_->Init(data_path_.c_str(), lang_.c_str());
    if (ret != 0) {
        std::cerr << "[OCR] Tesseract init failed (lang=" << lang_ << ", path=" << data_path_ << ")" << std::endl;
        delete api_;
        api_ = nullptr;
        return false;
    }
    
    // Config for document text recognition
    api_->SetVariable("tessedit_char_whitelist",
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        "-_./():[]{}=#@!&*+<>|\\\"' ,;~`^$%€£¥");
    api_->SetVariable("classify_bln_numeric_mode", "0");
    api_->SetVariable("textord_min_linesize", "2.5");
    
    // Set page segmentation mode
    api_->SetPageSegMode(static_cast<tesseract::PageSegMode>(page_seg_mode_));
    
    initialized_ = true;
    std::cout << "[OCR] Tesseract initialized: " << lang_ << " @ " << data_path_ << std::endl;
    return true;
#else
    std::cout << "[OCR] Initialized (Tesseract NOT available - compile with -DLEXIS_HAS_TESSERACT)" << std::endl;
    return true;
#endif
}

void OCRModule::shutdown() {
#ifdef LEXIS_HAS_TESSERACT
    if (api_) {
        api_->End();
        delete api_;
        api_ = nullptr;
    }
#endif
    initialized_ = false;
    std::cout << "[OCR] Shutdown" << std::endl;
}

void OCRModule::process_frame(uint32_t* pixels, int w, int h,
                               FrameResult& result) {
    if (!enabled_ || !pixels) return;
    
    auto blocks = extract_text(pixels, w, h);
    
    if (blocks.empty()) return;
    
    // Store raw text blocks
    result.document.text_blocks = blocks;
    
    // Build full text
    std::string full_text;
    for (const auto& block : blocks) {
        full_text += block.text + "\n";
    }
    result.document.full_text = full_text;
    
    // Calculate stats
    result.document.line_count = blocks.size();
    result.document.word_count = 0;
    result.document.char_count = 0;
    for (const auto& block : blocks) {
        size_t pos = 0;
        while ((pos = block.text.find(' ', pos)) != std::string::npos) {
            result.document.word_count++;
            pos++;
        }
        result.document.word_count++;  // Last word
        result.document.char_count += block.text.length();
    }
    
    // Calculate average confidence
    float total_conf = 0.0f;
    for (const auto& block : blocks) {
        total_conf += block.confidence;
    }
    result.document.ocr_confidence = blocks.empty() ? 0.0f : total_conf / blocks.size();
    
    result.valid = !blocks.empty();
}

#ifdef LEXIS_HAS_TESSERACT

// ── BGRA → grayscale ─────────────────────────────────────────────

static Pix* bgra_to_pix(const uint32_t* pixels, int w, int h) {
    // Create 8-bit grayscale Pix
    Pix* pix = pixCreate(w, h, 8);
    if (!pix) return nullptr;
    
    uint32_t* pix_data = pixGetData(pix);
    int wpl = pixGetWpl(pix);
    
    for (int y = 0; y < h; y++) {
        for (int x = 0; x < w; x++) {
            uint32_t bgra = pixels[y * w + x];
            uint8_t b = (bgra >> 16) & 0xFF;
            uint8_t g = (bgra >> 8)  & 0xFF;
            uint8_t r = (bgra)       & 0xFF;
            // Luminance (BT.601)
            uint8_t gray = (uint8_t)(0.299 * r + 0.587 * g + 0.114 * b);
            SET_DATA_BYTE(pix_data + y * wpl, x, gray);
        }
    }
    
    return pix;
}

// ── Preprocessing: threshold + denoise ───────────────────────────

static Pix* preprocess(Pix* src) {
    // 1. Convert to 8bpp if needed
    Pix* gray = src;
    if (pixGetDepth(src) != 8) {
        gray = pixConvertTo8(src, 0);
    }
    
    // 2. Adaptive threshold (Otsu) for clean text
    Pix* thresh = nullptr;
    pixOtsuAdaptiveThreshold(gray, 200, 200, 0, 0, 0.0, nullptr, &thresh);
    if (!thresh) {
        if (gray != src) pixDestroy(&gray);
        return src;
    }
    
    // 3. Convert threshold result to 8bpp if it came out as 1-bit
    Pix* thresh8 = thresh;
    if (pixGetDepth(thresh) != 8) {
        thresh8 = pixConvertTo8(thresh, 0);
    }
    
    // 4. Light median filter to remove noise (3x3)
    Pix* denoised = pixMedianFilter(thresh8, 3, 3);
    
    // Cleanup intermediates
    if (thresh8 != thresh) pixDestroy(&thresh8);
    if (thresh != gray) pixDestroy(&thresh);
    if (gray != src) pixDestroy(&gray);
    
    return denoised;
}

// ── Scale up small images for better OCR ─────────────────────────

static Pix* scale_if_small(Pix* src, int min_height) {
    int h = pixGetHeight(src);
    if (h >= min_height) return src;
    
    float scale = (float)min_height / (float)h;
    Pix* scaled = pixScale(src, scale, scale);
    return scaled;
}

#endif

std::vector<TextBlock> OCRModule::extract_text(uint32_t* pixels, int w, int h) {
    std::vector<TextBlock> blocks;
    
#ifdef LEXIS_HAS_TESSERACT
    if (!api_ || !initialized_) return blocks;
    
    // 1. Convert BGRA → Pix
    Pix* raw = bgra_to_pix(pixels, w, h);
    if (!raw) return blocks;
    
    // 2. Preprocess
    Pix* processed = preprocess(raw);
    if (!processed) {
        pixDestroy(&raw);
        return blocks;
    }
    
    // 3. Scale if too small (documents often have small text)
    Pix* final_img = scale_if_small(processed, 32);
    
    // 4. Run Tesseract
    api_->SetImage(final_img);
    
    // 5. Get results with bounding boxes using ResultIterator (line level)
    char* full_text = api_->GetUTF8Text();
    if (full_text && strlen(full_text) > 0) {
        tesseract::ResultIterator* it = api_->GetIterator();
        if (it) {
            int line_num = 0;
            do {
                // Get bounding box for full line
                int x, y, w_b, h_b;
                if (!it->BoundingBox(tesseract::RIL_TEXTLINE, &x, &y, &w_b, &h_b)) continue;
                
                // Get full line text
                char* line = it->GetUTF8Text(tesseract::RIL_TEXTLINE);
                float conf = it->Confidence(tesseract::RIL_TEXTLINE) / 100.0f;
                
                if (!line || strlen(line) == 0) {
                    delete[] line;
                    continue;
                }
                
                // Filter low confidence
                if (conf < min_confidence_) {
                    delete[] line;
                    continue;
                }
                
                std::string text(line);
                delete[] line;
                
                // Trim whitespace
                size_t start = text.find_first_not_of(" \t\n\r");
                size_t end = text.find_last_not_of(" \t\n\r");
                if (start == std::string::npos) continue;
                text = text.substr(start, end - start + 1);
                
                if (text.length() < 2) continue;
                
                TextBlock block;
                block.text = text;
                block.confidence = conf;
                block.x = x;
                block.y = y;
                block.width = w_b;
                block.height = h_b;
                block.line_number = line_num++;
                
                blocks.push_back(block);
                
            } while (it->Next(tesseract::RIL_TEXTLINE));
            
            delete it;
        }
        
        delete[] full_text;
    }
    
    // Cleanup
    if (final_img != processed) pixDestroy(&final_img);
    if (processed != raw) pixDestroy(&processed);
    pixDestroy(&raw);
#else
    // No Tesseract: stub that returns empty
    (void)pixels; (void)w; (void)h;
#endif
    
    return blocks;
}

} // namespace lexis
