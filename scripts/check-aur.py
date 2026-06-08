#!/usr/bin/env python3
"""
check-aur.py - Verifica qué herramientas del manifiesto existen en AUR.

Uso: python3 scripts/check-aur.py
"""

import json
import os
import sys
import time
import urllib.request
import urllib.parse

PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MANIFEST_PATH = os.path.join(PROJECT_ROOT, "app", "src", "main", "assets", "tool-manifest.json")
AUR_API = "https://aur.archlinux.org/rpc/v2/info"


def check_aur(pkg):
    params = urllib.parse.urlencode({"arg[]": pkg})
    url = f"{AUR_API}?{params}"
    try:
        req = urllib.request.Request(url, headers={"User-Agent": "AArchDroid/1.0"})
        with urllib.request.urlopen(req, timeout=15) as resp:
            data = json.loads(resp.read())
        if data.get("resultcount", 0) > 0:
            results = data.get("results", [])
            for r in results:
                if r.get("Name", "").lower() == pkg.lower():
                    return (True, r.get("Name", ""), r.get("Description", ""))
            r = results[0]
            return (True, r.get("Name", ""), r.get("Description", ""))
        return (False, None, None)
    except Exception as e:
        return (None, None, None)


def main():
    with open(MANIFEST_PATH) as f:
        manifest = json.load(f)

    missing = []
    for tool_key, tool_info in manifest.items():
        if tool_info.get("available") is False and tool_info.get("source") in ("blackarch", "arch"):
            missing.append((tool_key, tool_info.get("pkg", tool_key)))

    print(f"[+] Herramientas marcadas como no disponibles: {len(missing)}")
    print(f"[+] Consultando AUR...\n")

    for tool_key, pkg_name in missing:
        time.sleep(0.25)
        result, aur_name, aur_desc = check_aur(pkg_name)
        if result is True:
            print(f"[AUR] {tool_key:30s} pkg={pkg_name:25s} -> AUR: {aur_name}")
            print(f"      Desc: {(aur_desc or '')[:80]}")
        elif result is False:
            print(f"[MISS] {tool_key:30s} pkg={pkg_name:25s} -> NO EN AUR")
        else:
            print(f"[ERR]  {tool_key:30s} pkg={pkg_name:25s} -> Error en consulta")

    print("\n[+] Done.")


if __name__ == "__main__":
    main()
