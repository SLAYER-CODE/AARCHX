#pragma once

#include "cornea/module.h"
#include <ncnn/net.h>
#include <string>
#include <vector>

namespace cornea {

class VisualDetectorModule : public AnalysisModule {
public:
    VisualDetectorModule();
    ~VisualDetectorModule() override;

    const char* name() const override { return "visual_detector"; }
    const char* description() const override { return "YOLOv8 object detection (ncnn)"; }

    bool init() override;
    void shutdown() override;
    void process_frame(uint32_t* pixels, int w, int h, FrameResult& result) override;

    const std::vector<VisualDetection>& last_detections() const { return detections_; }

private:
    bool load_model(const std::string& param_path, const std::string& bin_path);
    std::vector<VisualDetection> detect(const uint32_t* pixels, int w, int h);
    void preprocess(const uint32_t* src, int src_w, int src_h, ncnn::Mat& input);
    std::vector<VisualDetection> postprocess(const ncnn::Mat& output, int img_w, int img_h);

    static void nms(std::vector<VisualDetection>& dets, float threshold);

    ncnn::Net net_;
    bool model_loaded_ = false;
    int input_size_ = 640;
    float conf_threshold_ = 0.25f;
    float nms_threshold_ = 0.45f;

    std::vector<VisualDetection> detections_;

    static const char* COCO_NAMES[];
    static const int COCO_COUNT;
};

}
