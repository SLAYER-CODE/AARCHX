#pragma once

#include "cornea/module.h"

#include <string>

namespace cornea {

// ── Vulnerability Database Module ────────────────────────────────
// Consulta base de datos SQLite de vulnerabilidades conocidas.
// Busca por: vendor+model, CVE ID, servicio+version.
class VulnDBModule : public AnalysisModule {
public:
    VulnDBModule();
    ~VulnDBModule();
    
    const char* name() const override { return "vulndb"; }
    const char* description() const override { return "SQLite vulnerability database lookup"; }
    
    bool init() override;
    void shutdown() override;
    void process_frame(uint32_t* pixels, int w, int h, 
                       FrameResult& result) override;
    
    // DB management
    bool open_db(const std::string& path);
    void close_db();
    bool update_db(const std::string& url);
    
    // Query methods
    std::vector<DeviceInfo::Vulnerability> lookup_vulns(
        const std::string& vendor,
        const std::string& model
    );
    
    std::vector<DeviceInfo::Credential> lookup_default_creds(
        const std::string& vendor,
        const std::string& model
    );
    
    // Stats
    int total_vulns() const { return total_vulns_; }
    int total_creds() const { return total_creds_; }
    
private:
    // State
    void* db_ = nullptr;  // sqlite3*
    int total_vulns_ = 0;
    int total_creds_ = 0;
};

} // namespace cornea

