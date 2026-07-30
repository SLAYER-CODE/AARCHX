#include "cornea/modules/visual_detector.h"
#include "cornea/log.h"
#include <iostream>
#include <algorithm>
#include <cmath>
#include <cstring>

namespace cornea {

const char* VisualDetectorModule::COCO_NAMES[] = {
    "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck",
    "boat", "traffic light", "fire hydrant", "stop sign", "parking meter", "bench",
    "bird", "cat", "dog", "horse", "sheep", "cow", "elephant", "bear", "zebra",
    "giraffe", "backpack", "umbrella", "handbag", "tie", "suitcase", "frisbee",
    "skis", "snowboard", "sports ball", "kite", "baseball bat", "baseball glove",
    "skateboard", "surfboard", "tennis racket", "bottle", "wine glass", "cup",
    "fork", "knife", "spoon", "bowl", "banana", "apple", "sandwich", "orange",
    "broccoli", "carrot", "hot dog", "pizza", "donut", "cake", "chair", "couch",
    "potted plant", "bed", "dining table", "toilet", "tv", "laptop", "mouse",
    "remote", "keyboard", "cell phone", "microwave", "oven", "toaster", "sink",
    "refrigerator", "book", "clock", "vase", "scissors", "teddy bear",
    "hair drier", "toothbrush"
};

const int VisualDetectorModule::COCO_COUNT = sizeof(COCO_NAMES) / sizeof(COCO_NAMES[0]);

VisualDetectorModule::VisualDetectorModule() {}

VisualDetectorModule::~VisualDetectorModule() {
    shutdown();
}

bool VisualDetectorModule::init() {
    std::cout << TAG_VISUAL << "Initializing ncnn YOLOv8n..." << std::endl;

    net_.opt.use_vulkan_compute = false;
    net_.opt.use_winograd_convolution = true;
    net_.opt.use_sgemm_convolution = true;
    net_.opt.use_int8_inference = false;
    net_.opt.num_threads = 2;
    net_.opt.lightmode = true;

    // Try to load model from params or default path
    std::string param_path = get_param("model_param");
    std::string bin_path = get_param("model_bin");
    
    if (param_path.empty()) {
        const char* search_paths[] = {
            "/root/cornea/yolov8n.param",
            "/data/local/aarchdroid/root/cornea/yolov8n.param",
            "yolov8n.param"
        };
        for (const char* p : search_paths) {
            FILE* f = fopen(p, "r");
            if (f) {
                fclose(f);
                param_path = p;
                bin_path = std::string(p, strlen(p) - 5) + "bin";  // .param -> .bin
                break;
            }
        }
    }

    model_loaded_ = load_model(param_path, bin_path);

    if (model_loaded_) {
        std::cout << TAG_VISUAL << "Model loaded: " << param_path << std::endl;
    } else {
        std::cout << TAG_VISUAL << "Model NOT loaded - visual detection disabled" << std::endl;
    }

    return true;
}

void VisualDetectorModule::shutdown() {
    if (model_loaded_) {
        net_.clear();
        model_loaded_ = false;
        if (verbose_) std::cout << TAG_VISUAL << "Shutdown" << std::endl;
    }
}

bool VisualDetectorModule::load_model(const std::string& param_path, const std::string& bin_path) {
    int ret = net_.load_param(param_path.c_str());
    if (ret != 0) {
        std::cerr << TAG_VISUAL << "Failed to load param: " << param_path << " (ret=" << ret << ")" << std::endl;
        return false;
    }

    ret = net_.load_model(bin_path.c_str());
    if (ret != 0) {
        std::cerr << TAG_VISUAL << "Failed to load model: " << bin_path << " (ret=" << ret << ")" << std::endl;
        return false;
    }

    return true;
}

void VisualDetectorModule::process_frame(uint32_t* pixels, int w, int h, FrameResult& result) {
    if (!enabled_ || !pixels || !model_loaded_) return;

    detections_ = detect(pixels, w, h);
    result.visual_detections = detections_;

    if (!detections_.empty()) {
        const auto& best = detections_[0];
        result.valid = true;

        if (best.class_id == 63) {
            result.device.type = DeviceType::PHONE;
            result.device.type_label = "Cell Phone";
        } else if (best.class_id == 64) {
            result.device.type = DeviceType::COMPUTER;
            result.device.type_label = "Laptop";
        } else if (best.class_id == 62) {
            result.device.type = DeviceType::COMPUTER;
            result.device.type_label = "Keyboard/Computer";
        } else if (best.class_id == 67) {
            result.device.type = DeviceType::TV;
            result.device.type_label = "Remote/TV";
        } else if (best.class_id == 65) {
            result.device.type = DeviceType::TV;
            result.device.type_label = "TV";
        } else if (best.class_id == 72) {
            result.device.type = DeviceType::PRINTER;
            result.device.type_label = "Vase/Printer";
        } else {
            result.device.type_label = best.class_name;
        }

        result.device.classification_confidence = best.confidence;

        if (verbose_)
            std::cout << TAG_VISUAL << best.class_name
                      << " (" << (int)(best.confidence * 100) << "%)"
                      << " bbox:[" << (int)best.x << "," << (int)best.y
                      << " " << (int)best.w << "x" << (int)best.h << "]"
                      << std::endl;
    }
}

void VisualDetectorModule::preprocess(const uint32_t* src, int src_w, int src_h, ncnn::Mat& input) {
    int target = input_size_;
    float scale = std::min((float)target / src_w, (float)target / src_h);
    int new_w = (int)(src_w * scale);
    int new_h = (int)(src_h * scale);

    std::vector<unsigned char> resized_rgb(new_w * new_h * 3);

    for (int y = 0; y < new_h; y++) {
        for (int x = 0; x < new_w; x++) {
            int sx = (int)(x / scale);
            int sy = (int)(y / scale);
            if (sx >= src_w) sx = src_w - 1;
            if (sy >= src_h) sy = src_h - 1;

            uint32_t pixel = src[sy * src_w + sx];
            int dst_idx = (y * new_w + x) * 3;
            resized_rgb[dst_idx + 0] = (pixel >> 16) & 0xFF;
            resized_rgb[dst_idx + 1] = (pixel >> 8) & 0xFF;
            resized_rgb[dst_idx + 2] = pixel & 0xFF;
        }
    }

    input = ncnn::Mat::from_pixels(resized_rgb.data(), ncnn::Mat::PIXEL_RGB, new_w, new_h);

    int pad_left = (target - new_w) / 2;
    int pad_top = (target - new_h) / 2;

    if (pad_left > 0 || pad_top > 0) {
        ncnn::Mat padded(target, target, 3);
        padded.fill(114.0f);

        for (int c = 0; c < 3; c++) {
            const float* src_ch = input.channel(c);
            float* dst_ch = padded.channel(c);
            for (int y = 0; y < new_h; y++) {
                memcpy(dst_ch + (y + pad_top) * target + pad_left,
                       src_ch + y * new_w,
                       new_w * sizeof(float));
            }
        }
        input = padded;
    }

    float norm_vals[3] = {1.0f / 255.0f, 1.0f / 255.0f, 1.0f / 255.0f};
    input.substract_mean_normalize(0, norm_vals);
}

std::vector<VisualDetection> VisualDetectorModule::detect(const uint32_t* pixels, int w, int h) {
    ncnn::Mat input;
    preprocess(pixels, w, h, input);

    ncnn::Extractor ex = net_.create_extractor();
    ex.set_light_mode(true);
    ex.input("in0", input);

    ncnn::Mat output;
    int ret = ex.extract("out0", output);
    if (ret != 0) {
        std::cerr << TAG_VISUAL << "extract() failed: ret=" << ret << std::endl;
        return {};
    }

    if (output.dims < 2 || output.w == 0 || output.h == 0) {
        std::cerr << TAG_VISUAL << "invalid output dims: " << output.dims
                  << " w=" << output.w << " h=" << output.h << std::endl;
        return {};
    }

    return postprocess(output, w, h);
}

std::vector<VisualDetection> VisualDetectorModule::postprocess(const ncnn::Mat& output, int img_w, int img_h) {
    std::vector<VisualDetection> detections;

    // output is (w=8400, h=84) — decoded bbox [0:4] + class scores [4:84]
    int num_anchors = output.w;
    int num_features = output.h;
    int num_classes = num_features - 4;

    float scale = std::min((float)input_size_ / img_w, (float)input_size_ / img_h);
    float inv_scale = 1.0f / scale;
    int new_w = (int)(img_w * scale);
    int new_h = (int)(img_h * scale);
    int pad_left = (input_size_ - new_w) / 2;
    int pad_top = (input_size_ - new_h) / 2;

    const float* data = (const float*)output.data;

    if (verbose_)
        std::cout << TAG_VISUAL << "output:" << num_features << "x" << num_anchors
                  << " dims=" << output.dims << " thresh:" << conf_threshold_ << std::endl;

    int pre_nms = 0;
    for (int i = 0; i < num_anchors; i++) {
        float max_score = 0.0f;
        int max_class = 0;

        for (int c = 4; c < num_features; c++) {
            float score = data[c * num_anchors + i];
            if (score > max_score) {
                max_score = score;
                max_class = c - 4;
            }
        }

        if (max_score < conf_threshold_) continue;
        pre_nms++;

        // Model outputs decoded bbox in pixel coords (model input space)
        float cx = data[0 * num_anchors + i];
        float cy = data[1 * num_anchors + i];
        float bw = data[2 * num_anchors + i];
        float bh = data[3 * num_anchors + i];

        // Unletterbox to original image coords
        float x1 = (cx - bw / 2.0f - pad_left) * inv_scale;
        float y1 = (cy - bh / 2.0f - pad_top) * inv_scale;
        float w  = bw * inv_scale;
        float h  = bh * inv_scale;

        x1 = std::max(0.0f, std::min(x1, (float)img_w));
        y1 = std::max(0.0f, std::min(y1, (float)img_h));
        w  = std::max(0.0f, std::min(w, (float)img_w - x1));
        h  = std::max(0.0f, std::min(h, (float)img_h - y1));

        if (verbose_ && pre_nms <= 5) {
            std::cout << TAG_VISUAL << "det#" << pre_nms
                      << " cls=" << max_class << "(" << ((max_class < COCO_COUNT) ? COCO_NAMES[max_class] : "?") << ")"
                      << " conf=" << (int)(max_score * 100) << "%"
                      << " xywh:[" << (int)cx << "," << (int)cy << " " << (int)bw << "x" << (int)bh << "]"
                      << " final:[" << (int)x1 << "," << (int)y1 << " " << (int)w << "x" << (int)h << "]"
                      << (w < 10 || h < 10 ? " SKIP" : " OK")
                      << std::endl;
        }

        if (w < 10 || h < 10) continue;

        VisualDetection det;
        det.class_id = max_class;
        det.class_name = (max_class < COCO_COUNT) ? COCO_NAMES[max_class] : "unknown";
        det.confidence = max_score;
        det.x = x1;
        det.y = y1;
        det.w = w;
        det.h = h;
        detections.push_back(det);
    }

    if (verbose_)
        std::cout << TAG_VISUAL << "anchors:" << num_anchors
                  << " classes:" << num_classes
                  << " above_thresh:" << pre_nms
                  << " size_filtered:" << (int)detections.size()
                  << " thresh:" << conf_threshold_ << std::endl;

    std::sort(detections.begin(), detections.end(),
              [](const VisualDetection& a, const VisualDetection& b) {
                  return a.confidence > b.confidence;
              });

    nms(detections, nms_threshold_);

    if (verbose_)
        std::cout << TAG_VISUAL << "after_nms:" << (int)detections.size() << std::endl;

    return detections;
}

void VisualDetectorModule::nms(std::vector<VisualDetection>& dets, float threshold) {
    for (size_t i = 0; i < dets.size(); i++) {
        if (dets[i].confidence < 0.0f) continue;

        for (size_t j = i + 1; j < dets.size(); j++) {
            if (dets[j].confidence < 0.0f) continue;
            if (dets[i].class_id != dets[j].class_id) continue;

            float ix1 = dets[i].x, iy1 = dets[i].y;
            float ix2 = dets[i].x + dets[i].w, iy2 = dets[i].y + dets[i].h;
            float jx1 = dets[j].x, jy1 = dets[j].y;
            float jx2 = dets[j].x + dets[j].w, jy2 = dets[j].y + dets[j].h;

            float xx1 = std::max(ix1, jx1);
            float yy1 = std::max(iy1, jy1);
            float xx2 = std::min(ix2, jx2);
            float yy2 = std::min(iy2, jy2);

            float inter = std::max(0.0f, xx2 - xx1) * std::max(0.0f, yy2 - yy1);
            float area_i = dets[i].w * dets[i].h;
            float area_j = dets[j].w * dets[j].h;
            float iou = inter / (area_i + area_j - inter);

            if (iou > threshold) {
                dets[j].confidence = -1.0f;
            }
        }
    }

    dets.erase(
        std::remove_if(dets.begin(), dets.end(),
                       [](const VisualDetection& d) { return d.confidence < 0.0f; }),
        dets.end());
}

}
