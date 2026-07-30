#pragma once

#include "cornea/types.h"

#include <string>
#include <vector>
#include <map>

namespace cornea {

// ── Analysis Module Interface ────────────────────────────────────
// Módulos de análisis visual de Cornea:
//   - OCR: Reconocer texto en frames de cámara
//   - Logo Detector: Identificar fabricantes por logo
//   - VulnDB: Buscar vulnerabilidades en SQLite
//   - Diagnostics: Mostrar info en overlay
class AnalysisModule {
public:
    virtual ~AnalysisModule() = default;
    
    // Identidad
    virtual const char* name() const = 0;
    virtual const char* description() const = 0;
    
    // Lifecycle
    virtual bool init() { return true; }
    virtual void shutdown() {}
    
    // ── Processing ───────────────────────────────────────────────
    // Procesar frame BGRA y extraer información
    // pixels es non-const porque algunos módulos dibujan overlay
    virtual void process_frame(uint32_t* pixels, int w, int h, 
                               FrameResult& result) {}
    
    // ── Configuration ────────────────────────────────────────────
    virtual void set_enabled(bool en) { enabled_ = en; }
    virtual bool enabled() const { return enabled_; }
    
    virtual void set_verbose(bool v) { verbose_ = v; }
    virtual bool verbose() const { return verbose_; }
    
    virtual void set_param(const std::string& key, const std::string& value) {
        params_[key] = value;
    }
    virtual std::string get_param(const std::string& key) const {
        auto it = params_.find(key);
        return it != params_.end() ? it->second : "";
    }
    
protected:
    bool enabled_ = true;
    bool verbose_ = false;
    std::map<std::string, std::string> params_;
};

} // namespace cornea

