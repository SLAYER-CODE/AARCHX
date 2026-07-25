#include "cornea/modules/device_classifier.h"

#include <iostream>
#include <algorithm>
#include <cmath>
#include <cstring>
#include <regex>

namespace cornea {

DeviceClassifierModule::DeviceClassifierModule() {
    // Initialize brand-to-type mapping
    // Phones
    brand_type_map_["samsung"] = DeviceType::PHONE;
    brand_type_map_["apple"] = DeviceType::PHONE;
    brand_type_map_["xiaomi"] = DeviceType::PHONE;
    brand_type_map_["huawei"] = DeviceType::PHONE;
    brand_type_map_["oneplus"] = DeviceType::PHONE;
    brand_type_map_["google"] = DeviceType::PHONE;
    brand_type_map_["oppo"] = DeviceType::PHONE;
    brand_type_map_["vivo"] = DeviceType::PHONE;
    brand_type_map_["realme"] = DeviceType::PHONE;
    brand_type_map_["motorola"] = DeviceType::PHONE;
    brand_type_map_["nokia"] = DeviceType::PHONE;
    brand_type_map_["sony"] = DeviceType::PHONE;
    
    // PCs/Laptops
    brand_type_map_["dell"] = DeviceType::COMPUTER;
    brand_type_map_["lenovo"] = DeviceType::COMPUTER;
    brand_type_map_["hp"] = DeviceType::COMPUTER;
    brand_type_map_["asus"] = DeviceType::COMPUTER;
    brand_type_map_["acer"] = DeviceType::COMPUTER;
    brand_type_map_["microsoft"] = DeviceType::COMPUTER;
    
    // Routers/Networking
    brand_type_map_["tp-link"] = DeviceType::ROUTER;
    brand_type_map_["tplink"] = DeviceType::ROUTER;
    brand_type_map_["netgear"] = DeviceType::ROUTER;
    brand_type_map_["d-link"] = DeviceType::ROUTER;
    brand_type_map_["linksys"] = DeviceType::ROUTER;
    brand_type_map_["ubiquiti"] = DeviceType::ROUTER;
    brand_type_map_["ubnt"] = DeviceType::ROUTER;
    brand_type_map_["mikrotik"] = DeviceType::ROUTER;
    brand_type_map_["tenda"] = DeviceType::ROUTER;
    brand_type_map_["asus"] = DeviceType::ROUTER;
    brand_type_map_["zyxel"] = DeviceType::ROUTER;
    
    // CCTV/Ip Cameras
    brand_type_map_["hikvision"] = DeviceType::CAMERA;
    brand_type_map_["dahua"] = DeviceType::CAMERA;
    brand_type_map_["axis"] = DeviceType::CAMERA;
    brand_type_map_["foscam"] = DeviceType::CAMERA;
    brand_type_map_["reolink"] = DeviceType::CAMERA;
    brand_type_map_["amcrest"] = DeviceType::CAMERA;
    brand_type_map_["unifi"] = DeviceType::CAMERA;
    
    // TVs
    brand_type_map_["samsung"] = DeviceType::TV;  // Could be phone or TV
    brand_type_map_["lg"] = DeviceType::TV;
    brand_type_map_["sony"] = DeviceType::TV;
    brand_type_map_["tcl"] = DeviceType::TV;
    brand_type_map_["hisense"] = DeviceType::TV;
    brand_type_map_["xiaomi"] = DeviceType::TV;
    
    // Printers
    brand_type_map_["canon"] = DeviceType::PRINTER;
    brand_type_map_["epson"] = DeviceType::PRINTER;
    brand_type_map_["brother"] = DeviceType::PRINTER;
    brand_type_map_["xerox"] = DeviceType::PRINTER;
    brand_type_map_["ricoh"] = DeviceType::PRINTER;
    
    // Servers
    brand_type_map_["synology"] = DeviceType::SERVER;
    brand_type_map_["qnap"] = DeviceType::SERVER;
    brand_type_map_["dell"] = DeviceType::SERVER;
    brand_type_map_["hp"] = DeviceType::SERVER;
    brand_type_map_["lenovo"] = DeviceType::SERVER;
    
    // Gaming Consoles
    brand_type_map_["playstation"] = DeviceType::TV;  // Console
    brand_type_map_["xbox"] = DeviceType::TV;
    brand_type_map_["nintendo"] = DeviceType::TV;
}

DeviceClassifierModule::~DeviceClassifierModule() {
    shutdown();
}

bool DeviceClassifierModule::init() {
    std::cout << "[DeviceClassifier] Initialized" << std::endl;
    return true;
}

void DeviceClassifierModule::shutdown() {
    std::cout << "[DeviceClassifier] Shutdown" << std::endl;
}

void DeviceClassifierModule::process_frame(uint32_t* pixels, int w, int h,
                                           FrameResult& result) {
    if (!enabled_ || !pixels) return;
    
    // Classify device type
    result.device.type = classify_device_type(result.device.text_blocks, pixels, w, h);
    
    // Detect device state
    DeviceState state = detect_state(pixels, w, h);
    result.device.state = state;
    
    // Store classification confidence
    result.device.classification_confidence = confidence_;
    
    // Update device info based on classification
    switch (result.device.type) {
        case DeviceType::PHONE:
            result.device.type_label = "Smartphone";
            break;
        case DeviceType::COMPUTER:
            result.device.type_label = "Computer/Laptop";
            break;
        case DeviceType::ROUTER:
            result.device.type_label = "Router/Access Point";
            break;
        case DeviceType::CAMERA:
            result.device.type_label = "IP Camera/CCTV";
            break;
        case DeviceType::TV:
            result.device.type_label = "Smart TV/Console";
            break;
        case DeviceType::PRINTER:
            result.device.type_label = "Printer";
            break;
        case DeviceType::SERVER:
            result.device.type_label = "Server/NAS";
            break;
        case DeviceType::SWITCH:
            result.device.type_label = "Network Switch";
            break;
        default:
            result.device.type_label = "Unknown Device";
            break;
    }
    
    // Add state info
    switch (state) {
        case DeviceState::ON:
            result.device.state_label = "ON";
            break;
        case DeviceState::OFF:
            result.device.state_label = "OFF";
            break;
        case DeviceState::SLEEP:
            result.device.state_label = "SLEEP/STANDBY";
            break;
        case DeviceState::BOOTING:
            result.device.state_label = "BOOTING";
            break;
        case DeviceState::ERROR:
            result.device.state_label = "ERROR";
            break;
        default:
            result.device.state_label = "UNKNOWN";
            break;
    }
    
    // Debug output
    std::cout << "[DeviceClassifier] Type: " << result.device.type_label 
              << " State: " << result.device.state_label
              << " Confidence: " << (int)(confidence_ * 100) << "%"
              << " Text blocks: " << result.device.text_blocks.size()
              << std::endl;
    
    // Mark as valid if we detected something
    result.valid = (result.device.type != DeviceType::UNKNOWN) || 
                   !result.device.text_blocks.empty() ||
                   confidence_ > 0.0f;
}

DeviceType DeviceClassifierModule::classify_device_type(
    const std::vector<TextBlock>& blocks,
    const uint32_t* pixels, int w, int h) {
    
    // First, try to identify by brand text
    std::string brand = detect_brand_from_text(blocks);
    if (!brand.empty()) {
        auto it = brand_type_map_.find(brand);
        if (it != brand_type_map_.end()) {
            confidence_ = 0.9f;
            return it->second;
        }
    }
    
    // Try heuristic detection from text
    float text_scores[] = {
        detect_phone(blocks, pixels, w, h),
        detect_pc(blocks, pixels, w, h),
        detect_router(blocks, pixels, w, h),
        detect_cctv(blocks, pixels, w, h),
        detect_tv(blocks, pixels, w, h),
        detect_printer(blocks, pixels, w, h),
        detect_modem(blocks, pixels, w, h),
        detect_console(blocks, pixels, w, h),
        detect_usb(blocks, pixels, w, h),
        detect_switch(blocks, pixels, w, h)
    };
    
    // Visual-only detection (no OCR needed)
    float visual_score_phone = 0.0f;
    float visual_score_tv = 0.0f;
    float visual_score_router = 0.0f;
    
    // Analyze image characteristics
    float brightness = calculate_brightness(pixels, w, h);
    float aspect = (float)h / (float)w;
    
    // Phone: portrait orientation + screen on
    if (aspect > 1.3f && aspect < 2.5f && brightness > 0.3f) {
        visual_score_phone = 0.4f;
    }
    
    // TV: landscape + large + bright screen
    if (w > 600 && aspect < 1.0f && brightness > 0.4f) {
        visual_score_tv = 0.5f;
    }
    
    // Router: small LEDs pattern
    int led_count = 0;
    uint32_t led_color;
    if (detect_leds(pixels, w, h, led_color, led_count)) {
        if (led_count >= 3 && led_count <= 10) {
            visual_score_router = 0.3f;
        }
    }
    
    // Combine text and visual scores
    DeviceType types[] = {
        DeviceType::PHONE,
        DeviceType::COMPUTER,
        DeviceType::ROUTER,
        DeviceType::CAMERA,
        DeviceType::TV,
        DeviceType::PRINTER,
        DeviceType::SERVER,
        DeviceType::TV,
        DeviceType::OTHER,
        DeviceType::SWITCH
    };
    
    float combined_scores[10];
    for (int i = 0; i < 10; i++) {
        combined_scores[i] = text_scores[i];
    }
    combined_scores[0] += visual_score_phone;   // Phone
    combined_scores[4] += visual_score_tv;      // TV
    combined_scores[2] += visual_score_router;  // Router
    
    // Find highest score
    float max_score = 0.0f;
    DeviceType best_type = DeviceType::UNKNOWN;
    
    for (int i = 0; i < 10; i++) {
        if (combined_scores[i] > max_score) {
            max_score = combined_scores[i];
            best_type = types[i];
        }
    }
    
    confidence_ = max_score;
    
    // If no strong match, return unknown
    if (max_score < 0.3f) {
        return DeviceType::UNKNOWN;
    }
    
    return best_type;
}

// ── Heuristic Detectors ────────────────────────────────────────

float DeviceClassifierModule::detect_phone(const std::vector<TextBlock>& blocks,
                                           const uint32_t* pixels, int w, int h) {
    float score = 0.0f;
    
    // Phone-specific keywords (avoid generic terms)
    static const std::vector<std::string> keywords = {
        "android", "ios", "iphone", "galaxy", "pixel", "oneplus",
        "imei", "sim", "4g", "5g", "lte",
        "battery", "charging", "usb-c", "lightning",
        "phone", "mobile", "smartphone", "cellular"
    };
    
    for (const auto& block : blocks) {
        std::string lower = block.text;
        std::transform(lower.begin(), lower.end(), lower.begin(), ::tolower);
        
        for (const auto& kw : keywords) {
            if (lower.find(kw) != std::string::npos) {
                score += 0.2f;  // Higher weight for specific keywords
            }
        }
    }
    
    // Visual: Phone screens are typically tall and narrow (portrait)
    float aspect = (float)h / (float)w;
    if (aspect > 1.5f && aspect < 2.5f) {
        score += 0.2f;  // Portrait orientation
    }
    
    return std::min(score, 1.0f);
}

float DeviceClassifierModule::detect_pc(const std::vector<TextBlock>& blocks,
                                        const uint32_t* pixels, int w, int h) {
    float score = 0.0f;
    
    // PC keywords
    static const std::vector<std::string> keywords = {
        "windows", "linux", "macos", "mac os", "ubuntu", "debian",
        "desktop", "laptop", "processor", "intel", "amd", "nvidia",
        "ram", "ssd", "hdd", "bios", "uefi", "boot"
    };
    
    for (const auto& block : blocks) {
        std::string lower = block.text;
        std::transform(lower.begin(), lower.end(), lower.begin(), ::tolower);
        
        for (const auto& kw : keywords) {
            if (lower.find(kw) != std::string::npos) {
                score += 0.15f;
            }
        }
    }
    
    // Visual: PC screens are typically landscape (16:9 or 16:10)
    float aspect = (float)w / (float)h;
    if (aspect > 1.3f && aspect < 2.0f) {
        score += 0.2f;  // Landscape orientation
    }
    
    return std::min(score, 1.0f);
}

float DeviceClassifierModule::detect_router(const std::vector<TextBlock>& blocks,
                                            const uint32_t* pixels, int w, int h) {
    float score = 0.0f;
    
    // Router-specific keywords (avoid generic terms like "wifi" that appear everywhere)
    static const std::vector<std::string> keywords = {
        "router", "gateway", "access point", "wlan", "wan port", "lan port",
        "dhcp", "nat", "firewall", "2.4ghz", "5ghz", "ssid",
        "wps", "wpa", "vpn", "static ip", "dynamic ip", "pppoe",
        "bridge mode", "repeater", "extender", "mesh"
    };
    
    for (const auto& block : blocks) {
        std::string lower = block.text;
        std::transform(lower.begin(), lower.end(), lower.begin(), ::tolower);
        
        for (const auto& kw : keywords) {
            if (lower.find(kw) != std::string::npos) {
                score += 0.2f;  // Higher weight for specific keywords
            }
        }
    }
    
    // Visual: Routers often have multiple LEDs in a row
    int led_count = 0;
    uint32_t led_color;
    if (detect_leds(pixels, w, h, led_color, led_count)) {
        if (led_count >= 4) {
            score += 0.3f;  // Multiple LEDs typical of routers
        }
    }
    
    return std::min(score, 1.0f);
}

float DeviceClassifierModule::detect_cctv(const std::vector<TextBlock>& blocks,
                                          const uint32_t* pixels, int w, int h) {
    float score = 0.0f;
    
    // CCTV keywords
    static const std::vector<std::string> keywords = {
        "camera", "ip camera", "cctv", "surveillance", "nvr", "dvr",
        "resolution", "1080p", "4k", "night vision", "infrared", "ir",
        "motion detection", "onvif", "rtsp", "h.264", "h.265"
    };
    
    for (const auto& block : blocks) {
        std::string lower = block.text;
        std::transform(lower.begin(), lower.end(), lower.begin(), ::tolower);
        
        for (const auto& kw : keywords) {
            if (lower.find(kw) != std::string::npos) {
                score += 0.15f;
            }
        }
    }
    
    return std::min(score, 1.0f);
}

float DeviceClassifierModule::detect_tv(const std::vector<TextBlock>& blocks,
                                        const uint32_t* pixels, int w, int h) {
    float score = 0.0f;
    
    // TV keywords
    static const std::vector<std::string> keywords = {
        "smart tv", "android tv", "tizen", "webos", "roku",
        "netflix", "youtube", "hdmi", "cec", "remote",
        "channel", "input", "source", "volume"
    };
    
    for (const auto& block : blocks) {
        std::string lower = block.text;
        std::transform(lower.begin(), lower.end(), lower.begin(), ::tolower);
        
        for (const auto& kw : keywords) {
            if (lower.find(kw) != std::string::npos) {
                score += 0.15f;
            }
        }
    }
    
    // Visual: TVs are typically large and landscape
    float aspect = (float)w / (float)h;
    if (aspect > 1.5f && w > 1000) {
        score += 0.2f;  // Large landscape display
    }
    
    return std::min(score, 1.0f);
}

float DeviceClassifierModule::detect_printer(const std::vector<TextBlock>& blocks,
                                             const uint32_t* pixels, int w, int h) {
    float score = 0.0f;
    
    // Printer keywords
    static const std::vector<std::string> keywords = {
        "printer", "print", "toner", "ink", "cartridge", "paper",
        "tray", "feeder", "scanner", "copier", "fax", "ppm",
        "dpi", "resolution"
    };
    
    for (const auto& block : blocks) {
        std::string lower = block.text;
        std::transform(lower.begin(), lower.end(), lower.begin(), ::tolower);
        
        for (const auto& kw : keywords) {
            if (lower.find(kw) != std::string::npos) {
                score += 0.15f;
            }
        }
    }
    
    return std::min(score, 1.0f);
}

float DeviceClassifierModule::detect_modem(const std::vector<TextBlock>& blocks,
                                           const uint32_t* pixels, int w, int h) {
    float score = 0.0f;
    
    // Modem keywords
    static const std::vector<std::string> keywords = {
        "modem", "dsl", "adsl", "vdsl", "cable modem", "fiber",
        "optical", "pon", "ont", "gpon", "epon", "isp",
        "internet", "broadband"
    };
    
    for (const auto& block : blocks) {
        std::string lower = block.text;
        std::transform(lower.begin(), lower.end(), lower.begin(), ::tolower);
        
        for (const auto& kw : keywords) {
            if (lower.find(kw) != std::string::npos) {
                score += 0.15f;
            }
        }
    }
    
    return std::min(score, 1.0f);
}

float DeviceClassifierModule::detect_console(const std::vector<TextBlock>& blocks,
                                             const uint32_t* pixels, int w, int h) {
    float score = 0.0f;
    
    // Console keywords
    static const std::vector<std::string> keywords = {
        "playstation", "ps4", "ps5", "xbox", "nintendo", "switch",
        "wii", "gaming", "controller", "gamepad", "console"
    };
    
    for (const auto& block : blocks) {
        std::string lower = block.text;
        std::transform(lower.begin(), lower.end(), lower.begin(), ::tolower);
        
        for (const auto& kw : keywords) {
            if (lower.find(kw) != std::string::npos) {
                score += 0.2f;
            }
        }
    }
    
    return std::min(score, 1.0f);
}

float DeviceClassifierModule::detect_usb(const std::vector<TextBlock>& blocks,
                                         const uint32_t* pixels, int w, int h) {
    float score = 0.0f;
    
    // USB device keywords
    static const std::vector<std::string> keywords = {
        "usb", "flash drive", "pendrive", "storage", "mass storage",
        "removable", "disk", "volume"
    };
    
    for (const auto& block : blocks) {
        std::string lower = block.text;
        std::transform(lower.begin(), lower.end(), lower.begin(), ::tolower);
        
        for (const auto& kw : keywords) {
            if (lower.find(kw) != std::string::npos) {
                score += 0.2f;
            }
        }
    }
    
    // USB devices are typically small
    if (w < 200 && h < 200) {
        score += 0.2f;
    }
    
    return std::min(score, 1.0f);
}

float DeviceClassifierModule::detect_switch(const std::vector<TextBlock>& blocks,
                                            const uint32_t* pixels, int w, int h) {
    float score = 0.0f;
    
    // Switch-specific keywords
    static const std::vector<std::string> keywords = {
        "switch", "vlan", "stp", "spanning tree",
        "managed switch", "unmanaged switch", "poe", "power over ethernet",
        "layer 2", "layer 3", "mac address table"
    };
    
    for (const auto& block : blocks) {
        std::string lower = block.text;
        std::transform(lower.begin(), lower.end(), lower.begin(), ::tolower);
        
        for (const auto& kw : keywords) {
            if (lower.find(kw) != std::string::npos) {
                score += 0.25f;  // Higher weight for specific keywords
            }
        }
    }
    
    // Visual: Switches often have many ports in a row
    int led_count = 0;
    uint32_t led_color;
    if (detect_leds(pixels, w, h, led_color, led_count)) {
        if (led_count >= 8) {
            score += 0.3f;  // 8+ LEDs typical of switches
        }
    }
    
    return std::min(score, 1.0f);
}

// ── State Detection ────────────────────────────────────────────

DeviceState DeviceClassifierModule::detect_state(const uint32_t* pixels, int w, int h) {
    // Check screen brightness
    float brightness = detect_screen_brightness(pixels, w, h);
    
    // Check for LEDs
    uint32_t led_color;
    int led_count;
    bool has_leds = detect_leds(pixels, w, h, led_color, led_count);
    
    // Determine state
    if (brightness > 0.5f) {
        return DeviceState::ON;  // Screen is bright
    } else if (brightness > 0.2f) {
        // Dim screen could be sleep or low brightness
        if (has_leds && led_count > 0) {
            return DeviceState::SLEEP;  // LEDs on but screen dim
        }
        return DeviceState::ON;  // Screen dim but on
    } else {
        // Screen is dark
        if (has_leds) {
            // Check LED color
            uint8_t r = (led_color >> 16) & 0xFF;
            uint8_t g = (led_color >> 8) & 0xFF;
            uint8_t b = led_color & 0xFF;
            
            if (r > 200 && g < 50 && b < 50) {
                return DeviceState::ERROR;  // Red LED
            } else if (g > 200 && r < 50 && b < 50) {
                return DeviceState::ON;  // Green LED = on
            } else if (b > 200 && r < 50 && g < 50) {
                return DeviceState::SLEEP;  // Blue LED = standby
            } else if (r > 100 && g > 100 && b < 50) {
                return DeviceState::BOOTING;  // Orange/amber = booting
            }
        }
        return DeviceState::OFF;  // No screen, no LEDs
    }
}

float DeviceClassifierModule::detect_screen_brightness(const uint32_t* pixels, int w, int h) {
    if (!pixels || w <= 0 || h <= 0) return 0.0f;
    
    // Sample center region (likely screen)
    int cx = w / 2;
    int cy = h / 2;
    int sample_w = w / 4;
    int sample_h = h / 4;
    
    float total_brightness = 0.0f;
    int count = 0;
    
    for (int y = cy - sample_h; y < cy + sample_h && y < h; y++) {
        for (int x = cx - sample_w; x < cx + sample_w && x < w; x++) {
            if (x >= 0 && y >= 0) {
                uint32_t pixel = pixels[y * w + x];
                uint8_t r = (pixel >> 16) & 0xFF;
                uint8_t g = (pixel >> 8) & 0xFF;
                uint8_t b = pixel & 0xFF;
                
                // Luminance
                float lum = 0.299f * r + 0.587f * g + 0.114f * b;
                total_brightness += lum / 255.0f;
                count++;
            }
        }
    }
    
    return count > 0 ? total_brightness / count : 0.0f;
}

float DeviceClassifierModule::calculate_brightness(const uint32_t* pixels, int w, int h) {
    if (!pixels || w <= 0 || h <= 0) return 0.0f;
    
    float total = 0.0f;
    int count = 0;
    
    // Sample every 4th pixel for speed
    for (int y = 0; y < h; y += 4) {
        for (int x = 0; x < w; x += 4) {
            uint32_t pixel = pixels[y * w + x];
            uint8_t r = (pixel >> 16) & 0xFF;
            uint8_t g = (pixel >> 8) & 0xFF;
            uint8_t b = pixel & 0xFF;
            
            float lum = 0.299f * r + 0.587f * g + 0.114f * b;
            total += lum / 255.0f;
            count++;
        }
    }
    
    return count > 0 ? total / count : 0.0f;
}

bool DeviceClassifierModule::detect_leds(const uint32_t* pixels, int w, int h,
                                         uint32_t& led_color, int& led_count) {
    led_count = 0;
    led_color = 0;
    
    // Look for small bright colored regions (LEDs)
    // Scan in blocks
    int block_size = 8;
    std::vector<std::pair<int, uint32_t>> candidates;
    
    for (int by = 0; by < h; by += block_size) {
        for (int bx = 0; bx < w; bx += block_size) {
            // Check if this block is a bright colored spot
            float max_saturation = 0.0f;
            uint32_t brightest_pixel = 0;
            int bright_count = 0;
            
            for (int y = by; y < by + block_size && y < h; y++) {
                for (int x = bx; x < bx + block_size && x < w; x++) {
                    uint32_t pixel = pixels[y * w + x];
                    uint8_t r = (pixel >> 16) & 0xFF;
                    uint8_t g = (pixel >> 8) & 0xFF;
                    uint8_t b = pixel & 0xFF;
                    
                    float max_c = std::max({(float)r, (float)g, (float)b});
                    float min_c = std::min({(float)r, (float)g, (float)b});
                    float saturation = max_c > 0 ? (max_c - min_c) / max_c : 0;
                    
                    if (saturation > 0.5f && max_c > 150) {
                        bright_count++;
                        if (saturation > max_saturation) {
                            max_saturation = saturation;
                            brightest_pixel = pixel;
                        }
                    }
                }
            }
            
            if (bright_count >= 2) {
                candidates.push_back({bright_count, brightest_pixel});
            }
        }
    }
    
    if (candidates.empty()) return false;
    
    // Sort by brightness
    std::sort(candidates.begin(), candidates.end(),
              [](const auto& a, const auto& b) { return a.first > b.first; });
    
    // Use top candidate
    led_count = candidates.size();
    led_color = candidates[0].second;
    
    return true;
}

// ── Text Analysis Helpers ──────────────────────────────────────

std::string DeviceClassifierModule::detect_brand_from_text(const std::vector<TextBlock>& blocks) {
    for (const auto& block : blocks) {
        std::string lower = block.text;
        std::transform(lower.begin(), lower.end(), lower.begin(), ::tolower);
        
        for (const auto& [brand, type] : brand_type_map_) {
            if (lower.find(brand) != std::string::npos) {
                return brand;
            }
        }
    }
    return "";
}

std::string DeviceClassifierModule::detect_model_from_text(const std::vector<TextBlock>& blocks) {
    static const std::regex model_re(
        R"((?:model|型号|型号)[:\s]*([A-Z0-9][\w\-\.]+))",
        std::regex::icase
    );
    
    for (const auto& block : blocks) {
        std::smatch m;
        if (std::regex_search(block.text, m, model_re)) {
            return m[1].str();
        }
    }
    return "";
}

} // namespace cornea
