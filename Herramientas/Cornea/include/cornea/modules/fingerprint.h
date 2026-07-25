#pragma once

#include "cornea/module.h"

namespace cornea {

// ── Fingerprint Module ───────────────────────────────────────────
// Identifica dispositivos descubiertos usando múltiples técnicas:
//   - HTTP User-Agent / Server header
//   - SNMP sysDescr / sysObjectID
//   - TCP/IP stack fingerprinting (OS detection)
//   - Port pattern matching
//   - mDNS service records
//   - Manufacturer OUI (MAC prefix)
class FingerprintModule : public AnalysisModule {
public:
    const char* name() const override { return "fingerprint"; }
    const char* description() const override { return "Device fingerprinting and identification"; }
    
    bool init() override;
    void shutdown() override;
    void scan(std::vector<Device>& devices) override;
    
    // Fingerprint methods
    void set_http_probe(bool en) { http_probe_ = en; }
    void set_snmp_probe(bool en) { snmp_probe_ = en; }
    void set_os_detect(bool en) { os_detect_ = en; }
    void set_port_scan(bool en) { port_scan_ = en; }
    
private:
    // Individual fingerprinting techniques
    void fingerprint_http(Device& device);
    void fingerprint_snmp(Device& device);
    void fingerprint_os(Device& device);
    void fingerprint_ports(Device& device);
    void fingerprint_mdns(Device& device);
    
    // Identification helpers
    DeviceType identify_type(const Device& device);
    std::string identify_os(const std::string& banner, const std::string& ua);
    std::string identify_model(const std::string& banner, const std::string& mac_vendor);
    
    // Common port patterns for device types
    struct PortPattern {
        uint16_t port;
        std::string service;
        DeviceType suggests_type;
    };
    static const std::vector<PortPattern> PORT_PATTERNS;
    
    // Config
    bool http_probe_ = true;
    bool snmp_probe_ = true;
    bool os_detect_ = true;
    bool port_scan_ = true;
};

} // namespace cornea

