#include "cornea/modules/vulndb.h"
#include "cornea/log.h"

#include <iostream>

namespace cornea {

VulnDBModule::VulnDBModule() {}

VulnDBModule::~VulnDBModule() {
    shutdown();
}

bool VulnDBModule::init() {
    if (verbose_) std::cout << TAG_VULNDB << "Initialized" << std::endl;
    return true;
}

void VulnDBModule::shutdown() {
    close_db();
    if (verbose_) std::cout << TAG_VULNDB << "Shutdown" << std::endl;
}

void VulnDBModule::process_frame(uint32_t* pixels, int w, int h, 
                                  FrameResult& result) {
    if (!enabled_ || !result.valid) return;
    
    // Only lookup if we have vendor or model info
    if (result.device.vendor.empty() && result.device.model.empty()) return;
    
    // Look up vulnerabilities
    auto vulns = lookup_vulns(result.device.vendor, result.device.model);
    result.device.vulns.insert(result.device.vulns.end(), vulns.begin(), vulns.end());
    
    // Look up default credentials
    auto creds = lookup_default_creds(result.device.vendor, result.device.model);
    result.device.default_creds.insert(result.device.default_creds.end(), creds.begin(), creds.end());
    
    total_vulns_ += vulns.size();
    total_creds_ += creds.size();
}

bool VulnDBModule::open_db(const std::string& path) {
    // TODO: Open SQLite database
    // db_ = sqlite3_open(path.c_str(), &db_);
    return true;
}

void VulnDBModule::close_db() {
    // TODO: Close database
    // if (db_) sqlite3_close((sqlite3*)db_);
    db_ = nullptr;
}

bool VulnDBModule::update_db(const std::string& url) {
    // TODO: Download and update database
    return true;
}

std::vector<DeviceInfo::Vulnerability> VulnDBModule::lookup_vulns(
    const std::string& vendor,
    const std::string& model
) {
    std::vector<DeviceInfo::Vulnerability> vulns;
    
    // TODO: Query SQLite database
    // SELECT * FROM vulnerabilities 
    // WHERE vendor LIKE '%vendor%' OR model LIKE '%model%'
    
    return vulns;
}

std::vector<DeviceInfo::Credential> VulnDBModule::lookup_default_creds(
    const std::string& vendor,
    const std::string& model
) {
    std::vector<DeviceInfo::Credential> creds;
    
    // TODO: Query SQLite database
    // SELECT * FROM default_credentials
    // WHERE vendor LIKE '%vendor%' OR model LIKE '%model%'
    
    return creds;
}

} // namespace cornea

