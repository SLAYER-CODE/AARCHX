#pragma once

#include "cornea/module.h"

namespace cornea {

// ── Discovery Module ─────────────────────────────────────────────
// Descubre dispositivos activos en la red usando múltiples técnicas:
//   - ARP scan (requiere root)
//   - mDNS/Avahi (zero-conf)
//   - SSDP/UPnP discovery
//   - DHCP snooping
//   - ICMP ping sweep
class DiscoveryModule : public AnalysisModule {
public:
    const char* name() const override { return "discovery"; }
    const char* description() const override { return "Network device discovery"; }
    
    bool init() override;
    void shutdown() override;
    void scan(std::vector<Device>& devices) override;
    
    // Discovery methods (can be enabled/disabled individually)
    void set_arp_scan(bool en) { arp_scan_ = en; }
    void set_mdns_scan(bool en) { mdns_scan_ = en; }
    void set_ssdp_scan(bool en) { ssdp_scan_ = en; }
    void set_ping_scan(bool en) { ping_scan_ = en; }
    
private:
    // Individual scan techniques
    void scan_arp(std::vector<Device>& devices);
    void scan_mdns(std::vector<Device>& devices);
    void scan_ssdp(std::vector<Device>& devices);
    void scan_ping(std::vector<Device>& devices);
    
    // Helpers
    bool exec_command(const std::string& cmd, std::string& output);
    std::string get_mac_vendor(const std::string& mac);
    
    // Config
    bool arp_scan_ = true;
    bool mdns_scan_ = true;
    bool ssdp_scan_ = true;
    bool ping_scan_ = true;
};

} // namespace cornea

