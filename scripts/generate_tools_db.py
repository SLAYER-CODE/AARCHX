#!/usr/bin/env python3
import json
import sqlite3
import os
from collections import defaultdict

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_DIR = os.path.abspath(os.path.join(SCRIPT_DIR, ".."))

MANIFEST_PATH = os.path.join(PROJECT_DIR, "app", "src", "main", "assets", "tool-manifest.json")
OUTPUT_DIR = os.path.join(PROJECT_DIR, "app", "src", "main", "assets", "databases")
OUTPUT_PATH = os.path.join(OUTPUT_DIR, "tools.db")

os.makedirs(OUTPUT_DIR, exist_ok=True)

with open(MANIFEST_PATH, "r") as f:
    manifest = json.load(f)

conn = sqlite3.connect(OUTPUT_PATH)
c = conn.cursor()

# --- tools table ---
c.execute("""
    CREATE TABLE IF NOT EXISTS 'tools' (
        'toolKey' TEXT PRIMARY KEY,
        'displayName' TEXT,
        'description' TEXT,
        'source' TEXT,
        'category' TEXT,
        'installCommand' TEXT,
        'uninstallCommand' TEXT,
        'estimatedSizeBytes' INTEGER,
        'actualSizeBytes' INTEGER,
        'status' TEXT,
        'installPath' TEXT,
        'installedAt' INTEGER,
        'errorLog' TEXT
    )
""")

# --- categories table ---
c.execute("""
    CREATE TABLE IF NOT EXISTS 'categories' (
        'name' TEXT PRIMARY KEY,
        'totalTools' INTEGER,
        'installedTools' INTEGER,
        'totalSizeMb' INTEGER,
        'installedSizeMb' INTEGER
    )
""")

def build_commands(key, entry):
    source = entry.get("source", "blackarch")
    pkg = entry.get("pkg", key)
    url = entry.get("url", "")
    
    if source in ("blackarch", "arch"):
        install = f"pacman --color always --disable-download-timeout -S --noconfirm {pkg}"
        uninstall = f"pacman --color always --disable-download-timeout -Rns --noconfirm {pkg}"
    elif source == "github":
        install = f"sh /data/data/org.aarchdroid/files/scripts/install-tool.sh {key}"
        uninstall = f"rm -rf /opt/{key}"
    elif source == "local":
        install = "exit 0"
        uninstall = "echo Pre-installed system tool"
    elif source == "url":
        install = f"mkdir -p /opt/{key} && wget -q \"{url}\" -O /opt/{key}/{key} && chmod +x /opt/{key}/{key}"
        uninstall = f"rm -rf /opt/{key}"
    else:
        install = f"pacman --color always --disable-download-timeout -S --noconfirm {pkg}"
        uninstall = f"pacman --color always --disable-download-timeout -Rns --noconfirm {pkg}"
    
    return install, uninstall

cat_tools = defaultdict(list)
count = 0

for key, entry in manifest.items():
    source = entry.get("source", "")
    category = entry.get("category", "")
    description = entry.get("description", "")
    pkg = entry.get("pkg", "")
    size_bytes = entry.get("size_bytes", 0)

    install_cmd, uninstall_cmd = build_commands(key, entry)
    cat_tools[category].append(key)

    initial_status = "installed" if source == "local" else "not_installed"
    c.execute(
        "INSERT INTO 'tools' (toolKey, displayName, description, source, category, installCommand, uninstallCommand, estimatedSizeBytes, status) "
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
        (key, key, description, source, category, install_cmd, uninstall_cmd, size_bytes, initial_status)
    )
    count += 1

# populate categories
for cat_name, tools_list in cat_tools.items():
    total_mb = 0
    for k in tools_list:
        sz = manifest[k].get("size_bytes", 0)
        total_mb += sz // (1024 * 1024)

    c.execute(
        "INSERT INTO 'categories' (name, totalTools, installedTools, totalSizeMb, installedSizeMb) "
        "VALUES (?, ?, 0, ?, 0)",
        (cat_name, len(tools_list), total_mb)
    )

conn.commit()
conn.close()

print(f"Generated {OUTPUT_PATH}")
print(f"  tools: {count} rows")
print(f"  categories: {len(cat_tools)} rows")
for cat_name in sorted(cat_tools.keys()):
    print(f"    {cat_name}: {len(cat_tools[cat_name])} tools")
