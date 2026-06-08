#!/usr/bin/env python3
"""
verify-tools.py - Verifica cada herramienta del manifiesto contra BlackArch,
Arch Linux repos (via API), y AUR. Actualiza tool-manifest.json con:
  - available: true/false/null
  - description: descripción del paquete

Uso: python3 scripts/verify-tools.py [--manifest <ruta>] [--blackarch-list <ruta>]
"""

import argparse
import json
import os
import re
import sys
import time
import urllib.request
import urllib.error
import urllib.parse

PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT_MANIFEST = os.path.join(PROJECT_ROOT, "app", "src", "main", "assets", "tool-manifest.json")
DEFAULT_BLACKARCH_LIST = "/tmp/blackarch-tools-list.txt"

ARCH_API_URL = "https://archlinux.org/packages/search/json/"
AUR_RPC_URL = "https://aur.archlinux.org/rpc/v2/info"

ARCH_REPOS = ("core", "extra", "community")


def load_blackarch_tools(filepath):
    """Carga lista de herramientas de BlackArch: { nombre_normalizado: descripcion }"""
    tools = {}
    if not os.path.exists(filepath):
        print(f"[!] Archivo BlackArch no encontrado: {filepath}")
        return tools
    with open(filepath) as f:
        for line in f:
            line = line.strip()
            if '|' in line:
                name, desc = line.split('|', 1)
                tools[name.strip().lower()] = desc.strip()
    print(f"[+] Cargados {len(tools)} paquetes desde BlackArch tools list")
    return tools


def query_arch_api(pkg_name):
    """Consulta el API de Arch Linux para ver si existe un paquete."""
    params = urllib.parse.urlencode({"q": pkg_name, "limit": 5})
    url = f"{ARCH_API_URL}?{params}"
    try:
        req = urllib.request.Request(url, headers={"User-Agent": "AArchDroid/1.0"})
        with urllib.request.urlopen(req, timeout=15) as resp:
            data = json.loads(resp.read())
        for result in data.get("results", []):
            if result["pkgname"].lower() == pkg_name.lower():
                return (True, result.get("pkgdesc", ""))
        # Fuzzy match: check if pkg_name appears in any result
        for result in data.get("results", []):
            if pkg_name.lower() in result["pkgname"].lower():
                return (True, result.get("pkgdesc", ""))
        if data.get("results"):
            # If we got results but no exact match, check if the search was close
            for result in data.get("results", []):
                name = result["pkgname"].lower()
                if name in pkg_name.lower() or pkg_name.lower() in name:
                    return (True, result.get("pkgdesc", ""))
        return (False, "")
    except Exception as e:
        print(f"      [!] Error querying Arch API for {pkg_name}: {e}")
        return (None, "")


def query_aur_api(pkg_name):
    """Consulta el API de AUR para ver si existe un paquete."""
    params = urllib.parse.urlencode({"arg[]": pkg_name})
    url = f"{AUR_RPC_URL}?{params}"
    try:
        req = urllib.request.Request(url, headers={"User-Agent": "AArchDroid/1.0"})
        with urllib.request.urlopen(req, timeout=15) as resp:
            data = json.loads(resp.read())
        if data.get("resultcount", 0) > 0:
            for result in data.get("results", []):
                if result.get("Name", "").lower() == pkg_name.lower():
                    return (True, result.get("Description", ""))
            # If no exact match, return first result's info
            result = data["results"][0]
            return (True, result.get("Description", ""))
        return (False, "")
    except Exception as e:
        print(f"      [!] Error querying AUR API for {pkg_name}: {e}")
        return (None, "")


def normalize_pkg_name(name):
    return re.sub(r'[^a-z0-9-]', '-', name.lower()).strip('-')


def check_blackarch(tool_key, pkg_name, ba_tools):
    """Busca en BlackArch por nombre exacto y variantes."""
    names_to_try = set()
    if pkg_name:
        names_to_try.add(normalize_pkg_name(pkg_name))
    if tool_key:
        names_to_try.add(normalize_pkg_name(tool_key))

    variants = set(names_to_try)
    for n in names_to_try:
        variants.add(n.replace('-', ''))
        variants.add(n.replace('-', '_'))
    for v in variants:
        if v in ba_tools:
            return (True, ba_tools[v])
        # Substring match
        for ba_name, ba_desc in ba_tools.items():
            if v == ba_name or v in ba_name or ba_name in v:
                return (True, ba_desc)
    return (False, "")


def check_arch_api(pkg_name):
    """Busca en Arch Linux repos via API, con retry."""
    for attempt in range(3):
        result = query_arch_api(pkg_name)
        if result[0] is not None:
            return result
        time.sleep(2)
    return (False, "")


def check_aur(pkg_name):
    """Busca en AUR via API, con retry."""
    for attempt in range(3):
        result = query_aur_api(pkg_name)
        if result[0] is not None:
            return result
        time.sleep(2)
    return (False, "")


def verify_tool(tool_key, tool_info, ba_tools):
    """Verifica una herramienta contra todas las fuentes."""
    pkg_name = tool_info.get("pkg", tool_key)
    source = tool_info.get("source", "blackarch")
    category = tool_info.get("category", "")

    # Tools from github/url/ubuntu_only don't need repo verification
    if source in ("github", "url", "pip", "gem", "go"):
        return (None, "")

    existing_available = tool_info.get("available")
    existing_desc = tool_info.get("description", "")

    # 1. Check BlackArch first (for blackarch source)
    if source in ("blackarch", "arch"):
        available, desc = check_blackarch(tool_key, pkg_name, ba_tools)
        if available:
            return (True, desc or existing_desc)

    # 2. Check Arch Linux official repos via API
    if source in ("blackarch", "arch"):
        available, desc = check_arch_api(pkg_name)
        if available:
            return (True, desc or existing_desc)
        # Try alternative names
        alt_names = {
            "afl": "afl-fuzz",
            "john": "john-the-ripper",
            "johnny": "john-the-ripper",
            "python-scapy": "scapy",
            "thc-ipv6": None,  # meta package
        }
        if pkg_name in alt_names:
            alt = alt_names[pkg_name]
            if alt:
                available, desc = check_arch_api(alt)
                if available:
                    return (True, desc or existing_desc)

    # 3. Check AUR as last resort
    if source == "arch":
        available, desc = check_aur(pkg_name)
        if available:
            return (True, desc or existing_desc)

    return (False, existing_desc)


def main():
    parser = argparse.ArgumentParser(description="Verifica herramientas del manifiesto contra repos")
    parser.add_argument("--manifest", default=DEFAULT_MANIFEST)
    parser.add_argument("--blackarch-list", default=DEFAULT_BLACKARCH_LIST)
    parser.add_argument("--output")
    args = parser.parse_args()

    # Cargar manifiesto
    if not os.path.exists(args.manifest):
        print(f"[!] Manifiesto no encontrado: {args.manifest}")
        sys.exit(1)

    with open(args.manifest) as f:
        manifest = json.load(f)
    print(f"[+] Manifiesto cargado: {len(manifest)} herramientas")

    # Cargar BlackArch
    ba_tools = load_blackarch_tools(args.blackarch_list)

    # Verificar cada herramienta
    stats = {"available": 0, "unavailable": 0, "not_applicable": 0}
    total = len(manifest)
    for idx, (tool_key, tool_info) in enumerate(manifest.items(), 1):
        source = tool_info.get("source", "blackarch")
        pkg_name = tool_info.get("pkg", tool_key)

        available, description = verify_tool(tool_key, tool_info, ba_tools)
        tool_info["available"] = available
        if description and not tool_info.get("description"):
            tool_info["description"] = description

        # Stats
        if available is True:
            stats["available"] += 1
        elif available is False:
            stats["unavailable"] += 1
        else:
            stats["not_applicable"] += 1

        status = "✓" if available is True else ("✗" if available is False else "–")
        print(f"  [{idx}/{total}] {tool_key:30s} [{source:10s}] pkg={pkg_name:25s} -> {status}", end="")
        if available is True and description:
            print(f"  {description[:60]}", end="")
        print()

    # Guardar resultado
    output_path = args.output or args.manifest
    with open(output_path, 'w') as f:
        json.dump(manifest, f, indent=2, ensure_ascii=False)

    print(f"\n[+] Resultados guardados en: {output_path}")
    print(f"[+] Disponibles: {stats['available']}")
    print(f"[+] NO disponibles: {stats['unavailable']}")
    print(f"[+] No aplica (github/url/etc): {stats['not_applicable']}")

    if stats['unavailable'] > 0:
        print(f"\n[!] Herramientas no encontradas (requieren revisión manual):")
        for tool_key, tool_info in sorted(manifest.items()):
            if tool_info.get("available") is False:
                src = tool_info.get("source", "blackarch")
                pkg = tool_info.get("pkg", tool_key)
                print(f"    {tool_key:30s} source={src:10s} pkg={pkg}")


if __name__ == "__main__":
    main()
