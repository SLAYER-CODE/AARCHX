#include "cornea/tracker.h"

#include <algorithm>
#include <cmath>

namespace cornea {

Tracker::Tracker() {}
Tracker::~Tracker() {}

void Tracker::predict() {
    for (auto& t : tracks_) {
        if (use_kalman_)
            t.predict();
        t.stale_count++;
    }
    remove_stale_tracks();
}

void Tracker::update(const std::vector<VisualDetection>& detections) {
    if (detections.empty()) return;

    std::vector<bool> matched_det(detections.size(), false);
    std::vector<bool> matched_trk(tracks_.size(), false);

    // Pass 1: match by class + IoU (strict)
    for (size_t di = 0; di < detections.size(); di++) {
        if (matched_det[di]) continue;

        int best_trk = -1;
        float best_iou = iou_threshold_;

        for (size_t ti = 0; ti < tracks_.size(); ti++) {
            if (matched_trk[ti]) continue;
            if (tracks_[ti].class_id != detections[di].class_id) continue;

            float iou = compute_iou(tracks_[ti], detections[di]);
            if (iou > best_iou) {
                best_iou = iou;
                best_trk = (int)ti;
            }
        }

        if (best_trk >= 0) {
            update_track(best_trk, detections[di]);
            matched_det[di] = true;
            matched_trk[best_trk] = true;
        }
    }

    // Pass 2: match remaining by IoU only (any class)
    for (size_t di = 0; di < detections.size(); di++) {
        if (matched_det[di]) continue;

        int best_trk = -1;
        float best_iou = 0.2f;

        for (size_t ti = 0; ti < tracks_.size(); ti++) {
            if (matched_trk[ti]) continue;

            float iou = compute_iou(tracks_[ti], detections[di]);
            if (iou > best_iou) {
                best_iou = iou;
                best_trk = (int)ti;
            }
        }

        if (best_trk >= 0) {
            update_track(best_trk, detections[di]);
            matched_det[di] = true;
            matched_trk[best_trk] = true;
        }
    }

    // Create tracks for unmatched detections
    for (size_t i = 0; i < detections.size(); i++) {
        if (!matched_det[i])
            create_track(detections[i]);
    }

    remove_stale_tracks();
}

std::vector<TrackedObject> Tracker::tracks() const {
    std::vector<TrackedObject> result;
    result.reserve(tracks_.size());
    for (const auto& t : tracks_) {
        if (t.age >= min_age_ && t.confidence >= min_conf_)
            result.push_back(t.to_tracked());
    }
    return result;
}

void Tracker::reset() {
    tracks_.clear();
    next_id_ = 1;
}

int Tracker::find_match(const VisualDetection& det) const {
    int best = -1;
    float best_iou = iou_threshold_;
    for (size_t i = 0; i < tracks_.size(); i++) {
        float iou = compute_iou(tracks_[i], det);
        if (iou > best_iou) {
            best_iou = iou;
            best = (int)i;
        }
    }
    return best;
}

void Tracker::create_track(const VisualDetection& det) {
    InternalTrack t;
    t.id = next_id_++;
    t.update(det);
    tracks_.push_back(t);
}

void Tracker::update_track(int idx, const VisualDetection& det) {
    tracks_[idx].update(det);
}

void Tracker::remove_stale_tracks() {
    tracks_.erase(
        std::remove_if(tracks_.begin(), tracks_.end(),
            [this](const InternalTrack& t) {
                return t.stale_count > max_stale_;
            }),
        tracks_.end()
    );
}

float Tracker::compute_iou(const InternalTrack& t, const VisualDetection& d) const {
    float tx = t.cx() - t.bw() * 0.5f;
    float ty = t.cy() - t.bh() * 0.5f;
    return box_iou(tx, ty, t.bw(), t.bh(), d.x, d.y, d.w, d.h);
}

float Tracker::box_iou(float ax, float ay, float aw, float ah,
                        float bx, float by, float bw, float bh) const {
    float ix = std::max(ax, bx);
    float iy = std::max(ay, by);
    float iw = std::min(ax + aw, bx + bw) - ix;
    float ih = std::min(ay + ah, by + bh) - iy;

    if (iw <= 0.0f || ih <= 0.0f) return 0.0f;

    float inter = iw * ih;
    float area_a = aw * ah;
    float area_b = bw * bh;
    float uni = area_a + area_b - inter;

    return (uni > 0.0f) ? inter / uni : 0.0f;
}

} // namespace cornea
