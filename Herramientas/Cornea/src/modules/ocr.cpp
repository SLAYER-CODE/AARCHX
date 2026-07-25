#include "cornea/modules/ocr.h"

#include <iostream>
#include <regex>
#include <algorithm>
#include <cstring>
#include <cmath>

#ifdef CORNEA_HAS_TESSERACT
#include <tesseract/baseapi.h>
#include <leptonica/allheaders.h>
#endif

namespace cornea {

OCRModule::OCRModule() {}

OCRModule::~OCRModule() {
    shutdown();
}

bool OCRModule::init() {
#ifdef CORNEA_HAS_TESSERACT
    api_ = new tesseract::TessBaseAPI();
    
    // Init Tesseract: data path + language
    int ret = api_->Init(data_path_.c_str(), lang_.c_str());
    if (ret != 0) {
        std::cerr << "[OCR] Tesseract init failed (lang=" << lang_ << ", path=" << data_path_ << ")" << std::endl;
        delete api_;
        api_ = nullptr;
        return false;
    }
    
    // Config for device label recognition
    api_->SetVariable("tessedit_char_whitelist",
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        "-_./():[]{}=#@!&*+<>|\\\"' ,;~`^");
    api_->SetVariable("classify_bln_numeric_mode", "0");
    api_->SetVariable("textord_min_linesize", "2.5");
    
    initialized_ = true;
    std::cout << "[OCR] Tesseract initialized: " << lang_ << " @ " << data_path_ << std::endl;
    return true;
#else
    std::cout << "[OCR] Initialized (Tesseract NOT available - compile with -DCORNEA_HAS_TESSERACT)" << std::endl;
    return true;
#endif
}

void OCRModule::shutdown() {
#ifdef CORNEA_HAS_TESSERACT
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
    
    result.device.text_blocks = blocks;
    result.device.model = find_model(blocks);
    result.device.serial = find_serial(blocks);
    result.device.firmware = find_firmware(blocks);
    
    std::string vendor_from_text = find_vendor_from_text(blocks);
    if (!vendor_from_text.empty() && result.device.vendor.empty()) {
        result.device.vendor = vendor_from_text;
    }
    
    float total_conf = 0.0f;
    for (const auto& block : blocks) {
        total_conf += block.confidence;
    }
    result.device.ocr_confidence = blocks.empty() ? 0.0f : total_conf / blocks.size();
    result.valid = !blocks.empty();
}

#ifdef CORNEA_HAS_TESSERACT

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
    
#ifdef CORNEA_HAS_TESSERACT
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
    
    // 3. Scale if too small (labels are often tiny)
    Pix* final = scale_if_small(processed, 32);
    
    // 4. Run Tesseract
    api_->SetImage(final);
    
    // 5. Get results with bounding boxes using ResultIterator (line level)
    char* full_text = api_->GetUTF8Text();
    if (full_text && strlen(full_text) > 0) {
        tesseract::ResultIterator* it = api_->GetIterator();
        if (it) {
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
                
                blocks.push_back(block);
                
            } while (it->Next(tesseract::RIL_TEXTLINE));
            
            delete it;
        }
        
        delete[] full_text;
    }
    
    // Cleanup
    if (final != processed) pixDestroy(&final);
    if (processed != raw) pixDestroy(&processed);
    pixDestroy(&raw);
#else
    // No Tesseract: stub that returns empty
    (void)pixels; (void)w; (void)h;
#endif
    
    return blocks;
}

std::string OCRModule::find_model(const std::vector<TextBlock>& blocks) {
    for (const auto& block : blocks) {
        if (matches_model_pattern(block.text)) {
            return block.text;
        }
    }
    return "";
}

std::string OCRModule::find_serial(const std::vector<TextBlock>& blocks) {
    for (const auto& block : blocks) {
        if (matches_serial_pattern(block.text)) {
            return block.text;
        }
    }
    return "";
}

std::string OCRModule::find_firmware(const std::vector<TextBlock>& blocks) {
    for (const auto& block : blocks) {
        if (matches_firmware_pattern(block.text)) {
            return block.text;
        }
    }
    return "";
}

std::string OCRModule::find_vendor_from_text(const std::vector<TextBlock>& blocks) {
    static const std::vector<std::pair<std::string, std::string>> vendors = {
        {"tp-link", "TP-Link"}, {"tplink", "TP-Link"},
        {"hikvision", "Hikvision"}, {"dahua", "Dahua"},
        {"huawei", "Huawei"}, {"xiaomi", "Xiaomi"},
        {"samsung", "Samsung"}, {"apple", "Apple"},
        {"netgear", "Netgear"}, {"d-link", "D-Link"},
        {"ubiquiti", "Ubiquiti"}, {"ubnt", "Ubiquiti"},
        {"mikrotik", "MikroTik"}, {"mikrotk", "MikroTik"},
        {"synology", "Synology"}, {"qnap", "QNAP"},
        {"canon", "Canon"}, {"hp", "HP"},
        {"lg", "LG"}, {"sony", "Sony"},
        {"google", "Google"}, {"amazon", "Amazon"},
        {"espressif", "Espressif"}, {"raspberry", "Raspberry Pi"},
        {"cisco", "Cisco"}, {"linksys", "Linksys"},
        {"zyxel", "ZyXEL"}, {"tenda", "Tenda"},
        {"netis", "Netis"}, {"asus", "ASUS"},
        {"dell", "Dell"}, {"lenovo", "Lenovo"},
        {"nintendo", "Nintendo"}, {"playstation", "Sony"},
        {"axis", "Axis"}, {"foscam", "Foscam"},
        {"reolink", "Reolink"}, {"amcrest", "Amcrest"},
        {"unifi", "Ubiquiti"}, {"edge", "Ubiquiti"}
    };
    
    for (const auto& block : blocks) {
        std::string lower = block.text;
        std::transform(lower.begin(), lower.end(), lower.begin(), ::tolower);
        
        for (const auto& [pattern, name] : vendors) {
            if (lower.find(pattern) != std::string::npos) {
                return name;
            }
        }
    }
    return "";
}

bool OCRModule::matches_model_pattern(const std::string& text) {
    // Model patterns: "Archer C7", "DS-2CD2042", "HG8245H", "RB951Ui", "SM-S921B"
    static const std::regex model_re(
        R"((?:model|型号|型号)[:\s]*([A-Z0-9][\w\-\.]+))",
        std::regex::icase
    );
    if (std::regex_search(text, model_re)) return true;
    
    // Also match standalone model-like patterns (uppercase + digits + hyphens)
    static const std::regex standalone_model(
        R"(^[A-Z]{2,}[\-\.]?[A-Z0-9]{2,}[\-\.]?[A-Z0-9]*$)"
    );
    return std::regex_search(text, standalone_model);
}

bool OCRModule::matches_serial_pattern(const std::string& text) {
    static const std::regex serial_re(
        R"((?:s/n|serial|序列号|序列)[:\s]*([A-Z0-9]{4,}))",
        std::regex::icase
    );
    return std::regex_search(text, serial_re);
}

bool OCRModule::matches_firmware_pattern(const std::string& text) {
    static const std::regex fw_re(
        R"((?:firmware|fw|version|固件)[:\s]*v?(\d+[\d\.]+))",
        std::regex::icase
    );
    return std::regex_search(text, fw_re);
}

} // namespace cornea

