#include "cornea/modules/discovery.h"

#include <iostream>
#include <sstream>
#include <regex>

namespace cornea {

bool DiscoveryModule::init() {
    std::cout << "[Discovery] Initialized" << std::endl;
    return true;
}

void DiscoveryModule::shutdown() {
    std::cout << "[Discovery] Shutdown" << std::endl;
}

void DiscoveryModule::scan(std::vector<Device>& devices) {
    if (!enabled_) return;
    
    std::cout << "[Discovery] Scanning network..." << std::endl;
    
    if (arp_scan_) scan_arp(devices);
    if (mdns_scan_) scan_mdns(devices);
    if (ssdp_scan_) scan_ssdp(devices);
    if (ping_scan_) scan_ping(devices);
    
    std::cout << "[Discovery] Found " << devices.size() << " devices" << std::endl;
}

void DiscoveryModule::scan_arp(std::vector<Device>& devices) {
    std::string output;
    if (exec_command("arp -a", output)) {
        // Parse ARP output
        std::istringstream iss(output);
        std::string line;
        while (std::getline(iss, line)) {
            // Parse: hostname (ip) at mac on interface [ether] on interface
            std::regex re(R"(\((\d+\.\d+\.\d+\.\d+)\)\s+at\s+([0-9a-fA-F:]+))");
            std::smatch match;
            if (std::regex_search(line, match, re)) {
                Device dev;
                dev.ip = match[1].str();
                dev.mac = match[2].str();
                dev.vendor = get_mac_vendor(dev.mac);
                dev.is_alive = true;
                devices.push_back(dev);
            }
        }
    }
}

void DiscoveryModule::scan_mdns(std::vector<Device>& devices) {
    // TODO: Implement mDNS discovery
    // Would use avahi-browse or raw multicast
}

void DiscoveryModule::scan_ssdp(std::vector<Device>& devices) {
    // TODO: Implement SSDP/UPnP discovery
    // Would send M-SEARCH multicast and parse responses
}

void DiscoveryModule::scan_ping(std::vector<Device>& devices) {
    // TODO: Implement ping sweep
    // Would use ICMP echo requests
}

bool DiscoveryModule::exec_command(const std::string& cmd, std::string& output) {
    FILE* pipe = popen(cmd.c_str(), "r");
    if (!pipe) return false;
    
    char buffer[4096];
    while (fgets(buffer, sizeof(buffer), pipe)) {
        output += buffer;
    }
    
    pclose(pipe);
    return true;
}

std::string DiscoveryModule::get_mac_vendor(const std::string& mac) {
    // TODO: Lookup OUI prefix in database
    return "";
}

} // namespace cornea

