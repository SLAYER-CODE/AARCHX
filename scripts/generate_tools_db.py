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

cat_tools = defaultdict(list)
count = 0

for key, entry in manifest.items():
    source = entry.get("source", "")
    category = entry.get("category", "")
    description = entry.get("description", "")
    pkg = entry.get("pkg", "")
    size_bytes = entry.get("size_bytes", 0)
    size_mb = size_bytes // (1024 * 1024)

    cat_tools[category].append(key)

    c.execute(
        "INSERT INTO 'tools' (toolKey, displayName, description, source, category, estimatedSizeBytes, status) "
        "VALUES (?, ?, ?, ?, ?, ?, 'not_installed')",
        (key, key, description, source, category, size_bytes)
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
