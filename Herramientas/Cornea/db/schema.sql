-- Cornea Vulnerability Database Schema
-- SQLite3 compatible

-- ══════════════════════════════════════════════════════════════════
-- VENDORS (fabricantes de dispositivos)
-- ══════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS vendors (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL UNIQUE,              -- e.g. "TP-Link", "Hikvision"
    prefix TEXT,                            -- OUI prefix (AA:BB:CC)
    website TEXT,
    category TEXT                           -- router, camera, phone, iot, etc.
);

-- ══════════════════════════════════════════════════════════════════
-- DEVICES (modelos de dispositivos)
-- ══════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS devices (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    vendor_id INTEGER REFERENCES vendors(id),
    model TEXT NOT NULL,                    -- e.g. "Archer C7", "DS-2CD2042WD-I"
    type TEXT,                              -- phone, iot, router, camera, etc.
    os_pattern TEXT,                        -- Pattern to match in banners
    default_http_port INTEGER DEFAULT 80,
    notes TEXT
);

-- ══════════════════════════════════════════════════════════════════
-- VULNERABILITIES (vulnerabilidades conocidas)
-- ══════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS vulnerabilities (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    cve_id TEXT,                            -- CVE-YYYY-NNNN or NULL
    vendor_id INTEGER REFERENCES vendors(id),
    device_id INTEGER REFERENCES devices(id),
    severity TEXT NOT NULL,                 -- CRITICAL, HIGH, MEDIUM, LOW, INFO
    title TEXT NOT NULL,                    -- Short description
    description TEXT,                       -- Full detail
    solution TEXT,                          -- Remediation steps
    affected_firmware TEXT,                 -- Firmware version pattern (regex)
    cvss_score REAL,                        -- CVSS v3 score (0.0-10.0)
    published_date TEXT,                    -- ISO date
    source_url TEXT                         -- Reference URL
);

-- ══════════════════════════════════════════════════════════════════
-- DEFAULT CREDENTIALS (credenciales por defecto)
-- ══════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS default_credentials (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    vendor_id INTEGER REFERENCES vendors(id),
    device_id INTEGER REFERENCES devices(id),
    service TEXT NOT NULL,                  -- ssh, http, telnet, ftp, rtsp, etc.
    username TEXT NOT NULL,
    password TEXT NOT NULL,
    source TEXT,                            -- manufacturer, common, leaked
    notes TEXT
);

-- ══════════════════════════════════════════════════════════════════
-- SERVICE SIGNATURES (para fingerprinting)
-- ══════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS service_signatures (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    port INTEGER NOT NULL,
    protocol TEXT DEFAULT 'TCP',            -- TCP, UDP
    service_name TEXT NOT NULL,             -- http, ssh, rtsp, etc.
    banner_pattern TEXT,                    -- Regex to match banner
    vendor_hint TEXT,                       -- Suggested vendor if matched
    device_type_hint TEXT                   -- Suggested device type
);

-- ══════════════════════════════════════════════════════════════════
-- OUI VENDORS (MAC address prefix → manufacturer)
-- ══════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS oui_vendors (
    prefix TEXT PRIMARY KEY,                -- AA:BB:CC (first 3 bytes)
    manufacturer TEXT NOT NULL,
    address TEXT
);

-- ══════════════════════════════════════════════════════════════════
-- SCAN HISTORY (historial de escaneos)
-- ══════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS scan_history (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    timestamp TEXT NOT NULL,                -- ISO datetime
    interface TEXT,
    network_range TEXT,
    total_hosts INTEGER,
    alive_hosts INTEGER,
    vulns_found INTEGER
);

CREATE TABLE IF NOT EXISTS scan_devices (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    scan_id INTEGER REFERENCES scan_history(id),
    ip TEXT,
    mac TEXT,
    vendor TEXT,
    device_type TEXT,
    hostname TEXT,
    os_info TEXT,
    open_ports TEXT,                        -- JSON array of ports
    vulns TEXT,                             -- JSON array of vuln IDs
    first_seen TEXT,
    last_seen TEXT
);

-- ══════════════════════════════════════════════════════════════════
-- INDEXES
-- ══════════════════════════════════════════════════════════════════
CREATE INDEX IF NOT EXISTS idx_vulns_vendor ON vulnerabilities(vendor_id);
CREATE INDEX IF NOT EXISTS idx_vulns_severity ON vulnerabilities(severity);
CREATE INDEX IF NOT EXISTS idx_creds_vendor ON default_credentials(vendor_id);
CREATE INDEX IF NOT EXISTS idx_creds_service ON default_credentials(service);
CREATE INDEX IF NOT EXISTS idx_signatures_port ON service_signatures(port);
CREATE INDEX IF NOT EXISTS idx_devices_vendor ON devices(vendor_id);

