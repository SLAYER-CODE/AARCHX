#pragma once

#include "cornea/types.h"

#include <vector>
#include <string>

namespace cornea {

struct Kalman1D {
    float x = 0.0f;
    float v = 0.0f;
    float p = 1.0f;
    float q = 0.01f;
    float r = 0.1f;

    Kalman1D() = default;
    Kalman1D(float q, float r) : q(q), r(r) {}

    void predict() {
        x += v;
        p += q;
    }

    void update(float z) {
        float k = p / (p + r);
        float residual = z - x;
        v += k * residual * 0.25f;
        x += k * residual;
        p = (1.0f - k) * p;
    }

    void init(float z) {
        x = z;
        v = 0.0f;
        p = 1.0f;
    }
};

struct InternalTrack {
    int id = 0;
    int class_id = 0;
    std::string class_name;
    float confidence = 0.0f;

    Kalman1D kf_cx, kf_cy;
    Kalman1D kf_w, kf_h;

    int age = 0;
    int stale_count = 0;

    InternalTrack() {
        kf_cx = Kalman1D(0.01f, 0.1f);
        kf_cy = Kalman1D(0.01f, 0.1f);
        kf_w  = Kalman1D(0.005f, 0.05f);
        kf_h  = Kalman1D(0.005f, 0.05f);
    }

    void predict() {
        kf_cx.predict();
        kf_cy.predict();
        kf_w.predict();
        kf_h.predict();
    }

    void update(const VisualDetection& det) {
        float cx = det.x + det.w * 0.5f;
        float cy = det.y + det.h * 0.5f;

        if (age == 0) {
            kf_cx.init(cx); kf_cy.init(cy);
            kf_w.init(det.w); kf_h.init(det.h);
            confidence = det.confidence;
            class_id = det.class_id;
            class_name = det.class_name;
        } else {
            kf_cx.update(cx); kf_cy.update(cy);
            kf_w.update(det.w); kf_h.update(det.h);

            float alpha = 0.3f;
            confidence = confidence * (1.0f - alpha) + det.confidence * alpha;

            if (det.class_id == class_id || det.confidence > confidence * 1.5f) {
                class_id = det.class_id;
                class_name = det.class_name;
            }
        }

        stale_count = 0;
        age++;
    }

    float cx() const { return kf_cx.x; }
    float cy() const { return kf_cy.x; }
    float bw() const { return kf_w.x; }
    float bh() const { return kf_h.x; }

    TrackedObject to_tracked() const {
        TrackedObject t;
        t.track_id = id;
        t.class_id = class_id;
        t.class_name = class_name;
        t.confidence = confidence;
        t.x = cx() - bw() * 0.5f;
        t.y = cy() - bh() * 0.5f;
        t.w = bw();
        t.h = bh();
        t.age = age;
        t.stale_count = stale_count;
        return t;
    }
};

class Tracker {
public:
    Tracker();
    ~Tracker();

    void predict();
    void update(const std::vector<VisualDetection>& detections);
    std::vector<TrackedObject> tracks() const;
    void reset();

    void set_iou_threshold(float t) { iou_threshold_ = t; }
    void set_max_stale(int s) { max_stale_ = s; }
    void set_min_age(int a) { min_age_ = a; }
    void set_min_confidence(float c) { min_conf_ = c; }
    void set_use_kalman(bool k) { use_kalman_ = k; }

    int track_count() const { return (int)tracks_.size(); }

private:
    std::vector<InternalTrack> tracks_;
    int next_id_ = 1;

    float iou_threshold_ = 0.3f;
    int max_stale_ = 5;
    int min_age_ = 3;
    float min_conf_ = 0.15f;
    bool use_kalman_ = true;

    int find_match(const VisualDetection& det) const;
    void create_track(const VisualDetection& det);
    void update_track(int idx, const VisualDetection& det);
    void remove_stale_tracks();

    float compute_iou(const InternalTrack& t, const VisualDetection& d) const;
    float box_iou(float ax, float ay, float aw, float ah,
                  float bx, float by, float bw, float bh) const;
};

} // namespace cornea
