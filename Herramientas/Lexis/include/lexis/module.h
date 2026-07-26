#pragma once

#include "lexis/types.h"

#include <string>
#include <vector>
#include <map>

namespace lexis {

// ── Analysis Module Interface ────────────────────────────────────
// Módulos de análisis de documentos:
//   - OCR: Reconocer texto en frames de cámara
//   - Document Analyzer: Clasificar documento y extraer campos
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
    // Procesar frame BGRA y extraer información del documento
    // pixels es non-const porque algunos módulos dibujan overlay
    virtual void process_frame(uint32_t* pixels, int w, int h, 
                               FrameResult& result) {}
    
    // ── Configuration ────────────────────────────────────────────
    virtual void set_enabled(bool en) { enabled_ = en; }
    virtual bool enabled() const { return enabled_; }
    
    virtual void set_param(const std::string& key, const std::string& value) {
        params_[key] = value;
    }
    virtual std::string get_param(const std::string& key) const {
        auto it = params_.find(key);
        return it != params_.end() ? it->second : "";
    }
    
protected:
    bool enabled_ = true;
    std::map<std::string, std::string> params_;
};

} // namespace lexis
