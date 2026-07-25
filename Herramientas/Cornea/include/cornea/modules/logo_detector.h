#pragma once

#include "cornea/module.h"

#include <string>
#include <vector>
#include <map>

namespace cornea {

// ── Logo Detector Module ─────────────────────────────────────────
// Identifica fabricantes de dispositivos usando template matching.
// Compara frames de cámara contra templates de logos de fabricantes.
class LogoDetectorModule : public AnalysisModule {
public:
    LogoDetectorModule();
    ~LogoDetectorModule();
    
    const char* name() const override { return "logo_detector"; }
    const char* description() const override { return "Logo-based manufacturer detection"; }
    
    bool init() override;
    void shutdown() override;
    void process_frame(uint32_t* pixels, int w, int h, 
                       FrameResult& result) override;
    
    // Template management
    bool load_template(const std::string& vendor, const std::string& image_path);
    void clear_templates();
    
    // Configuration
    void set_min_confidence(float min) { min_confidence_ = min; }
    void set_template_dir(const std::string& dir) { template_dir_ = dir; }
    float min_confidence() const { return min_confidence_; }
    
    // Stats
    int template_count() const { return templates_.size(); }
    
private:
    // Template matching
    std::vector<LogoMatch> match_templates(const uint32_t* pixels, int w, int h);
    
    // Feature detection (ORB)
    void detect_keypoints(const uint32_t* pixels, int w, int h);
    void match_features(const uint32_t* template_pixels, int tw, int th);
    
    // Image conversion
    void* to_grayscale(const uint32_t* pixels, int w, int h);
    
    // Template data
    struct Template {
        std::string vendor;
        std::string image_path;
        int width = 0;
        int height = 0;
        uint32_t* pixels = nullptr;     // BGRA pixels
        void* keypoints = nullptr;      // ORB keypoints
        void* descriptors = nullptr;    // ORB descriptors
    };
    
    std::vector<Template> templates_;
    std::map<std::string, int> vendor_index_;  // vendor → template index
    
    // State
    float min_confidence_ = 0.6f;
    std::string template_dir_ = "/data/local/aarchdroid/root/cornea/templates";
    bool initialized_ = false;
    
    // Working buffers
    void* orb_detector_ = nullptr;
    std::vector<void*> current_keypoints_;
    std::vector<void*> current_descriptors_;
};

} // namespace cornea

