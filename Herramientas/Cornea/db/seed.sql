-- Cornea Vulnerability Database - Initial Data
-- Common IoT/Router/Phone default credentials and known vulnerabilities

-- ══════════════════════════════════════════════════════════════════
-- VENDORS
-- ══════════════════════════════════════════════════════════════════
INSERT OR IGNORE INTO vendors (name, prefix, category) VALUES
('TP-Link', '50:C7:BF', 'router'),
('Hikvision', '28:57:BE', 'camera'),
('Dahua', '3C:EF:8C', 'camera'),
('Huawei', '00:E0:FC', 'router'),
('Xiaomi', '64:09:80', 'iot'),
('Samsung', 'F8:04:2E', 'phone'),
('Apple', 'A4:83:E7', 'phone'),
('Netgear', '9C:3D:CF', 'router'),
('D-Link', '1C:7E:E5', 'router'),
('Ubiquiti', 'FC:EC:DA', 'router'),
('Shenzhen', 'AC:CF:5C', 'iot'),
('Espressif', '30:AE:A4', 'iot'),
('Raspberry Pi', 'B8:27:EB', 'computer'),
('Axis', '00:40:8C', 'camera'),
('MikroTik', '4C:5E:0C', 'router'),
('Synology', '00:11:32', 'server'),
('QNAP', '24:5E:BE', 'server'),
('Canon', '00:1E:8F', 'printer'),
('HP', '3C:D9:2B', 'printer'),
('LG', '10:68:3F', 'tv'),
('Sony', '00:04:1F', 'tv'),
('Google', 'F4:F5:D8', 'iot'),
('Amazon', 'F0:F0:A4', 'iot');

-- ══════════════════════════════════════════════════════════════════
-- DEVICES
-- ══════════════════════════════════════════════════════════════════
INSERT OR IGNORE INTO devices (vendor_id, model, type, os_pattern) VALUES
(1, 'Archer C7', 'router', 'TP-LINK.*Archer'),
(2, 'DS-2CD2042WD-I', 'camera', 'Hikvision'),
(3, 'IPC-HDW5231', 'camera', 'Dahua'),
(4, 'HG8245H', 'router', 'Huawei.*HG'),
(5, 'Mi Router 4A', 'router', 'Xiaomi.*Router'),
(8, 'R7000', 'router', 'Netgear.*R7000'),
(9, 'DIR-825', 'router', 'D-Link.*DIR'),
(10, 'EdgeRouter', 'router', 'Ubiquiti.*EdgeRouter'),
(15, 'RB951Ui-2HnD', 'router', 'MikroTik'),
(16, 'DS218+', 'server', 'Synology'),
(17, 'TS-453D', 'server', 'QNAP'),
(18, 'imageRUNNER', 'printer', 'Canon'),
(19, 'LaserJet', 'printer', 'HP'),
(20, 'webOS TV', 'tv', 'LG.*webOS'),
(21, 'Bravia', 'tv', 'Sony.*Bravia'),
(22, 'Chromecast', 'iot', 'Google.*Chromecast'),
(23, 'Echo', 'iot', 'Amazon.*Echo');

-- ══════════════════════════════════════════════════════════════════
-- DEFAULT CREDENTIALS (CRITICAL - contraseñas por defecto)
-- ══════════════════════════════════════════════════════════════════

-- TP-Link
INSERT OR IGNORE INTO default_credentials (vendor_id, service, username, password, source) VALUES
(1, 'http', 'admin', 'admin', 'manufacturer'),
(1, 'http', 'admin', '1234', 'manufacturer'),
(1, 'telnet', 'admin', 'admin', 'manufacturer'),
(1, 'ssh', 'root', 'root', 'common');

-- Hikvision
INSERT OR IGNORE INTO default_credentials (vendor_id, service, username, password, source) VALUES
(2, 'http', 'admin', '12345', 'manufacturer'),
(2, 'rtsp', 'admin', '12345', 'manufacturer'),
(2, 'telnet', 'root', '12345', 'manufacturer'),
(2, 'ssh', 'root', '12345', 'leaked');

-- Dahua
INSERT OR IGNORE INTO default_credentials (vendor_id, service, username, password, source) VALUES
(3, 'http', 'admin', 'admin', 'manufacturer'),
(3, 'rtsp', 'admin', 'admin', 'manufacturer'),
(3, 'telnet', 'root', 'admin', 'manufacturer'),
(3, 'ssh', 'root', 'admin', 'common');

-- Huawei
INSERT OR IGNORE INTO default_credentials (vendor_id, service, username, password, source) VALUES
(4, 'http', 'admin', 'admin', 'manufacturer'),
(4, 'http', 'telecomadmin', 'nE7jA%5m', 'manufacturer'),
(4, 'telnet', 'root', 'admin', 'manufacturer'),
(4, 'ssh', 'root', 'admin', 'common');

-- Netgear
INSERT OR IGNORE INTO default_credentials (vendor_id, service, username, password, source) VALUES
(8, 'http', 'admin', 'password', 'manufacturer'),
(8, 'http', 'admin', '1234', 'manufacturer'),
(8, 'telnet', 'admin', 'password', 'manufacturer'),
(8, 'ssh', 'root', 'password', 'common');

-- D-Link
INSERT OR IGNORE INTO default_credentials (vendor_id, service, username, password, source) VALUES
(9, 'http', 'admin', '', 'manufacturer'),
(9, 'http', 'admin', 'admin', 'manufacturer'),
(9, 'http', 'user', 'user', 'manufacturer'),
(9, 'telnet', 'admin', '', 'manufacturer');

-- Ubiquiti
INSERT OR IGNORE INTO default_credentials (vendor_id, service, username, password, source) VALUES
(10, 'http', 'ubnt', 'ubnt', 'manufacturer'),
(10, 'ssh', 'ubnt', 'ubnt', 'manufacturer'),
(10, 'telnet', 'ubnt', 'ubnt', 'manufacturer');

-- MikroTik
INSERT OR IGNORE INTO default_credentials (vendor_id, service, username, password, source) VALUES
(15, 'http', 'admin', '', 'manufacturer'),
(15, 'ssh', 'admin', '', 'manufacturer'),
(15, 'telnet', 'admin', '', 'manufacturer'),
(15, 'winbox', 'admin', '', 'manufacturer');

-- Synology
INSERT OR IGNORE INTO default_credentials (vendor_id, service, username, password, source) VALUES
(16, 'http', 'admin', 'admin', 'manufacturer'),
(16, 'ssh', 'admin', 'admin', 'common'),
(16, 'ftp', 'anonymous', '', 'common');

-- QNAP
INSERT OR IGNORE INTO default_credentials (vendor_id, service, username, password, source) VALUES
(17, 'http', 'admin', 'admin', 'manufacturer'),
(17, 'ssh', 'admin', 'admin', 'common'),
(17, 'ftp', 'admin', 'admin', 'manufacturer');

-- Generic IoT (common defaults)
INSERT OR IGNORE INTO default_credentials (service, username, password, source, notes) VALUES
('http', 'admin', 'admin', 'common', 'Generic IoT default'),
('http', 'admin', 'password', 'common', 'Generic IoT default'),
('http', 'admin', '1234', 'common', 'Generic IoT default'),
('http', 'root', 'root', 'common', 'Generic IoT default'),
('http', 'user', 'user', 'common', 'Generic IoT default'),
('ssh', 'root', 'root', 'common', 'Generic Linux default'),
('ssh', 'root', 'toor', 'common', 'BSD default'),
('ssh', 'root', 'password', 'common', 'Generic default'),
('telnet', 'admin', 'admin', 'common', 'Generic default'),
('telnet', 'root', 'root', 'common', 'Generic default'),
('ftp', 'anonymous', '', 'common', 'Anonymous FTP'),
('rtsp', 'admin', 'admin', 'common', 'Generic camera default'),
('rtsp', 'root', 'root', 'common', 'Generic camera default');

-- ══════════════════════════════════════════════════════════════════
-- VULNERABILITIES (CVEs conocidos)
-- ══════════════════════════════════════════════════════════════════
INSERT OR IGNORE INTO vulnerabilities (cve_id, vendor_id, severity, title, description, solution, cvss_score) VALUES
-- Hikvision
('CVE-2021-36260', 2, 'CRITICAL', 'Hikvision Backdoor Command Injection',
 'Critical RCE vulnerability in Hikvision IP cameras allowing unauthenticated command injection via crafted HTTP messages.',
 'Update to latest firmware immediately.', 9.8),

('CVE-2017-7921', 2, 'CRITICAL', 'Hikvision Authentication Bypass',
 'Missing authentication allowing access to camera video stream and configuration.',
 'Update firmware.', 9.8),

-- Dahua
('CVE-2021-33044', 3, 'CRITICAL', 'Dahua Authentication Bypass',
 'Dahua IP cameras allow authentication bypass via specific HTTP request.',
 'Update firmware.', 9.8),

('CVE-2021-33045', 3, 'HIGH', 'Dahua Remote Code Execution',
 'Dahua products allow remote code execution through crafted requests.',
 'Update firmware.', 8.8),

-- Huawei
('CVE-2020-16846', 4, 'CRITICAL', 'Huawei Router Command Injection',
 'Huawei routers allow command injection via web interface.',
 'Update firmware and change default credentials.', 9.8),

-- TP-Link
('CVE-2023-1389', 1, 'CRITICAL', 'TP-Link Archer Command Injection',
 'TP-Link Archer routers vulnerable to command injection in the diagnostic functions.',
 'Update to latest firmware.', 8.8),

-- Netgear
('CVE-2023-20198', 8, 'CRITICAL', 'Netgear Router Pre-Auth RCE',
 'Netgear routers allow unauthenticated remote code execution.',
 'Update firmware immediately.', 9.8),

-- Generic IoT
(NULL, NULL, 'HIGH', 'Default Credentials Active',
 'Device is using factory default credentials which are publicly known.',
 'Change all default passwords immediately.', 7.5),

(NULL, NULL, 'MEDIUM', 'UPnP Enabled',
 'Universal Plug and Play is enabled, potentially exposing services to the internet.',
 'Disable UPnP if not needed.', 5.3),

(NULL, NULL, 'MEDIUM', 'Telnet Service Enabled',
 'Telnet transmits credentials in plaintext. Use SSH instead.',
 'Disable Telnet and use SSH.', 5.3),

(NULL, NULL, 'LOW', 'HTTP Interface Exposed',
 'Device web interface is accessible without HTTPS encryption.',
 'Enable HTTPS and disable HTTP.', 3.7),

(NULL, NULL, 'INFO', 'mDNS/Bonjour Broadcast',
 'Device is broadcasting its identity via mDNS, revealing manufacturer and model.',
 'Disable mDNS if not needed for discovery.', 0.0);

-- ══════════════════════════════════════════════════════════════════
-- SERVICE SIGNATURES (para fingerprinting)
-- ══════════════════════════════════════════════════════════════════
INSERT OR IGNORE INTO service_signatures (port, service_name, banner_pattern, vendor_hint, device_type_hint) VALUES
(22, 'ssh', 'SSH.*OpenSSH', NULL, NULL),
(23, 'telnet', NULL, NULL, NULL),
(53, 'dns', NULL, 'router', NULL),
(80, 'http', 'Server:.*Apache|nginx|lighttpd', NULL, NULL),
(80, 'http', 'Server:.*TP-LINK', 'TP-Link', 'router'),
(80, 'http', 'Server:.*Hikvision', 'Hikvision', 'camera'),
(80, 'http', 'Server:.*Dahua', 'Dahua', 'camera'),
(80, 'http', 'Server:.*GoAhead', NULL, 'iot'),
(443, 'https', NULL, NULL, NULL),
(554, 'rtsp', 'RTSP.*Hikvision', 'Hikvision', 'camera'),
(554, 'rtsp', 'RTSP.*Dahua', 'Dahua', 'camera'),
(554, 'rtsp', NULL, NULL, 'camera'),
(1900, 'ssdp', NULL, NULL, NULL),
(5353, 'mdns', NULL, NULL, NULL),
(8080, 'http-alt', NULL, NULL, NULL),
(8443, 'https-alt', NULL, NULL, NULL),
(37215, 'huawei', 'Huawei', 'Huawei', 'router'),
(49152, 'upnp', NULL, NULL, NULL);

