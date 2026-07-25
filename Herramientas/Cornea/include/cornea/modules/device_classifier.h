#pragma once

#include "cornea/module.h"

#include <string>
#include <vector>
#include <map>
#include <regex>

namespace cornea {

// ── Device Classifier Module ────────────────────────────────────
// Identifica tipo de dispositivo y su estado usando análisis visual.
// Combina OCR (leer etiquetas) con análisis de imagen (brillo, LEDs, forma).
class DeviceClassifierModule : public AnalysisModule {
public:
    DeviceClassifierModule();
    ~DeviceClassifierModule();
    
    const char* name() const override { return "device_classifier"; }
    const char* description() const override { return "Visual device type and state classification"; }
    
    bool init() override;
    void shutdown() override;
    void process_frame(uint32_t* pixels, int w, int h, 
                       FrameResult& result) override;
    
private:
    // ── Device Type Classification ──────────────────────────────
    DeviceType classify_device_type(const std::vector<TextBlock>& blocks,
                                    const uint32_t* pixels, int w, int h);
    
    // Heuristics for each device type
    float detect_phone(const std::vector<TextBlock>& blocks,
                       const uint32_t* pixels, int w, int h);
    float detect_pc(const std::vector<TextBlock>& blocks,
                    const uint32_t* pixels, int w, int h);
    float detect_router(const std::vector<TextBlock>& blocks,
                        const uint32_t* pixels, int w, int h);
    float detect_cctv(const std::vector<TextBlock>& blocks,
                      const uint32_t* pixels, int w, int h);
    float detect_tv(const std::vector<TextBlock>& blocks,
                    const uint32_t* pixels, int w, int h);
    float detect_printer(const std::vector<TextBlock>& blocks,
                         const uint32_t* pixels, int w, int h);
    float detect_modem(const std::vector<TextBlock>& blocks,
                       const uint32_t* pixels, int w, int h);
    float detect_console(const std::vector<TextBlock>& blocks,
                         const uint32_t* pixels, int w, int h);
    float detect_usb(const std::vector<TextBlock>& blocks,
                     const uint32_t* pixels, int w, int h);
    float detect_switch(const std::vector<TextBlock>& blocks,
                        const uint32_t* pixels, int w, int h);
    
    // ── State Detection ─────────────────────────────────────────
    DeviceState detect_state(const uint32_t* pixels, int w, int h);
    float detect_screen_brightness(const uint32_t* pixels, int w, int h);
    bool detect_leds(const uint32_t* pixels, int w, int h, 
                     uint32_t& led_color, int& led_count);
    
    // ── Image Analysis Helpers ──────────────────────────────────
    float calculate_brightness(const uint32_t* pixels, int w, int h);
    float calculate_average_color(const uint32_t* pixels, int w, int h,
                                  uint8_t& r, uint8_t& g, uint8_t& b);
    int count_bright_regions(const uint32_t* pixels, int w, int h, 
                             int threshold, int min_size);
    bool has_text_region(const std::vector<TextBlock>& blocks, 
                         const std::string& keyword);
    
    // ── Brand/Model Detection from OCR ──────────────────────────
    std::string detect_brand_from_text(const std::vector<TextBlock>& blocks);
    std::string detect_model_from_text(const std::vector<TextBlock>& blocks);
    
    // Known brands per device type
    std::map<std::string, DeviceType> brand_type_map_;
    
    // State
    DeviceType last_type_ = DeviceType::UNKNOWN;
    DeviceState last_state_ = DeviceState::UNKNOWN;
    float confidence_ = 0.0f;
};

} // namespace cornea
