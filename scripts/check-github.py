#!/usr/bin/env python3
"""
check-github.py - Busca repositorios GitHub para herramientas no encontradas
en BlackArch/Arch/AUR.

Uso: python3 scripts/check-github.py [--token GITHUB_TOKEN]

Sin token: 60 requests/hora (GitHub API rate limit)
Con token: 5000 requests/hora (recomendado)
"""

import json
import os
import sys
import time
import urllib.request
import urllib.error
import json as jsonlib

PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MANIFEST_PATH = os.path.join(PROJECT_ROOT, "app", "src", "main", "assets", "tool-manifest.json")

GITHUB_API = "https://api.github.com"

# Posibles organizaciones/autores por categoría
KNOWN_ORGS = {
    "wireless_hacking": ["aircrack-ng", "securifi", "wi-fi"],
    "exploitation": ["rapid7", "offensive-security", "exploit-database"],
    "website_hacking": ["projectdiscovery", "codingo", "s0md3v"],
    "bug_bounty": ["projectdiscovery", "tomnomnom", "hahwul", "s0md3v"],
    "bluetooth": ["mikeryan", "carlescufi"],
    "voip": ["telefonica"],
    "scada": ["sejmou"],
    "mainframe": [],
    "macos_iphone": ["xpn", "googleprojectzero"],
    "packet_crafting": [],
    "mobile_hacking": [],
    "password_attacks": [],
    "network_scanning": [],
    "crypto": [],
    "mitm": [],
    "forensics": [],
    "fuzzing": [],
    "recon": ["projectdiscovery"],
}

# Herramientas que son parte de paquetes mayores (no repos individuales)
PARENT_PACKAGES = {
    "fake-advertise6": "thc-ipv6",
    "fake-dhcps6": "thc-ipv6",
    "fake-mld26": "thc-ipv6",
    "fake-mld6": "thc-ipv6",
    "fake-mldrouter6": "thc-ipv6",
    "fake-router26": "thc-ipv6",
    "fake-router6": "thc-ipv6",
    "fake-solicitate6": "thc-ipv6",
    "flood-advertise6": "thc-ipv6",
    "flood-dhcpc6": "thc-ipv6",
    "flood-mld26": "thc-ipv6",
    "flood-mld6": "thc-ipv6",
    "flood-mldrouter6": "thc-ipv6",
    "flood-redir6": "thc-ipv6",
    "flood-router26": "thc-ipv6",
    "flood-router6": "thc-ipv6",
    "flood-rs6": "thc-ipv6",
    "flood-solicitate6": "thc-ipv6",
    "flood-unreach6": "thc-ipv6",
    "fuzz-ip6": "thc-ipv6",
    "inverse-lookup6": "thc-ipv6",
    "kill-router6": "thc-ipv6",
    "psk-crack": "reaver",
    "wash": "reaver",
    "mbtget": "mbtget",
    "snmpwn": "snmpwn",
}
# PATRONES: herramientas conocidas con repos GitHub específicos
KNOWN_GITHUB = {
    "apple-bleee": "https://github.com/RAI-cyber/Apple-BLEEE",
    "aron": "https://github.com/danaugns/aron",
    "bgp-cli": "https://github.com/sandelu/mpls-tools",
    "bitb": "https://github.com/hikame/bitb",
    "blescan": "https://github.com/omriiluz/BLEScan",
    "cicspwn": "https://github.com/Malcrove/cicspwn",
    "cicsshot": "https://github.com/Coalfire-Research/nih_cics_emulator",
    "cryptomobile": "https://github.com/RUB-SysSec/cryptomobile",
    "diameter-enum": "https://github.com/networkedsystems/NAPH",
    "enodeb": "https://github.com/lmangani/enodeb",
    "expliot": "https://github.com/tcrs/expliot_framework",
    "find-all-links": "https://github.com/tomnomnom/hacks",
    "godoh": "https://github.com/crosbymichael/godoh",
    "hashboy": "https://github.com/k4m1k4/hashboy",
    "ismtp": "https://github.com/neffuzz/ismtp",
    "js-alert": None,  # simple HTML/JS test, not a github tool
    "mainframe-bruter": "https://github.com/ChrisPritchard/mainframe-bruter",
    "maintp": "https://github.com/networkedsystems/NAPH",
    "mbtget": "https://github.com/jwrr/mbtget",
    "mfdos": "https://github.com/Coalfire-Research/nih_cics_emulator",
    "mfterm": "https://github.com/nfc-tools/mfterm",
    "mme-enodeb": "https://github.com/emrekindir/MME-eNodeB-Simulator",
    "mpls-tun": "https://github.com/sandelu/mpls-tools",
    "netebcdicat": "https://github.com/mikeandwan/NetEbcdicat",
    "nimcrypt2": "https://github.com/icyguider/Nimcrypt2",
    "nodexp": "https://github.com/0xedward/nodexp",
    "nomore403": "https://github.com/devploit/nomore403",
    "on-the-fly": None,
    "pgw": "https://github.com/marcosRavelo/LTE-5G-Security-Tools",
    "phatso": "https://github.com/evilsocket/phatso",
    "protos-test-suite": "https://github.com/emrebdr/Protos-Test-Suite",
    "psikotik": None,
    "psk-crack": None,  # parte de hostapd-utils
    "rbcd": "https://github.com/tothi/rbcd-attack",
    "rtpflood": "https://github.com/telefonica/RTPFlood",
    "s1ap-enum": "https://github.com/networkedsystems/NAPH",
    "s7scan": "https://github.com/yanhayes78/S7Scan",
    "scada-tools": None,  # meta-paquete
    "sgw": "https://github.com/marcosRavelo/LTE-5G-Security-Tools",
    "sixnet-tools": None,
    "snmpwn": "https://github.com/hatlord/snmpwn",
    "ssh-auditor": "https://github.com/ncsa/ssh-auditor",
    "svcrack": "https://github.com/hempflower/svcrack",
    "svwar": "https://github.com/hempflower/svwar",
    "tpx-brute": "https://github.com/ChrisPritchard/tpx-brute",
    "udpfloodvlan": None,
    "wacker": "https://github.com/blaze-infosec/wacker",
    "wbk": None,
    "xxetimes": None,
    "zos-privesc": "https://github.com/ChrisPritchard/zos-privesc",
    # thc-ipv6 tools - son parte del paquete thc-ipv6
    "fake-advertise6": None,
    "fake-dhcps6": None,
    "fake-mld26": None,
    "fake-mld6": None,
    "fake-mldrouter6": None,
    "fake-router26": None,
    "fake-router6": None,
    "fake-solicitate6": None,
    "flood-advertise6": None,
    "flood-dhcpc6": None,
    "flood-mld26": None,
    "flood-mld6": None,
    "flood-mldrouter6": None,
    "flood-redir6": None,
    "flood-router26": None,
    "flood-router6": None,
    "flood-rs6": None,
    "flood-solicitate6": None,
    "flood-unreach6": None,
    "fuzz-ip6": None,
    "inverse-lookup6": None,
    "kill-router6": None,
    "parasite6": None,
    "randicmp6": None,
    "redir6": None,
    "rsmurf6": None,
    "smurf6": None,
}


def check_github_repo(url, token=None):
    """Verifica si un repo de GitHub existe."""
    if not url:
        return False
    # Convertir URL de GitHub a API
    # https://github.com/user/repo -> https://api.github.com/repos/user/repo
    parts = url.replace("https://github.com/", "").strip("/").split("/")
    if len(parts) < 2:
        return False
    owner, repo = parts[0], parts[1]
    api_url = f"{GITHUB_API}/repos/{owner}/{repo}"

    headers = {"Accept": "application/vnd.github.v3+json", "User-Agent": "AArchDroid/1.0"}
    if token:
        headers["Authorization"] = f"token {token}"

    try:
        req = urllib.request.Request(api_url, headers=headers)
        with urllib.request.urlopen(req, timeout=15) as resp:
            data = jsonlib.loads(resp.read())
            return (True, data.get("description", ""))
    except urllib.error.HTTPError as e:
        if e.code == 404:
            return (False, "")
        elif e.code == 403:
            remaining = e.headers.get("X-RateLimit-Remaining", "0")
            reset = e.headers.get("X-RateLimit-Reset", "0")
            print(f"  [!] Rate limit exceeded! Remaining: {remaining}, Reset: {reset}")
            return (None, "")
        print(f"  [!] HTTP {e.code} for {url}")
        return (False, "")
    except Exception as e:
        print(f"  [!] Error: {e}")
        return (None, "")


def guess_github_urls(tool_key):
    """Genera posibles URLs de GitHub para una herramienta."""
    urls = []
    name = tool_key.lower()
    # Patrón: github.com/toolname/toolname
    urls.append(f"https://github.com/{name}/{name}")
    # Patrón: github.com/toolname/tool
    for suf in ["tool", "tools", "framework", "-tool", "-tools"]:
        urls.append(f"https://github.com/{name}/{name}{suf}")
    # Patrón: github.com/author/toolname - probar autores comunes
    for author in ["xer0dayz", "s0md3v", "tomnomnom", "projectdiscovery", "codingo",
                   "hahwul", "devploit", "coalfire", "ChrisPritchard", "tothi",
                   "hatlord", "evilsocket", "ncsa", "blaze-infosec"]:
        urls.append(f"https://github.com/{author}/{name}")
    return urls


def main():
    import argparse
    parser = argparse.ArgumentParser(description="Busca repos GitHub para herramientas no encontradas")
    parser.add_argument("--token", help="GitHub personal access token (opcional)")
    parser.add_argument("--check-all", action="store_true",
                       help="Verificar todas las URLs contra GitHub API (lento)")
    args = parser.parse_args()

    if args.check_all and not args.token:
        print("[!] Sin token solo 60 req/hora. Usá --token o esperá.")
        print("[!] Con --check-all se harán muchas consultas API.")
        resp = input("  ¿Continuar de todas formas? (s/N): ").strip().lower()
        if resp != 's':
            print("[.] Abortado.")
            return

    with open(MANIFEST_PATH) as f:
        manifest = json.load(f)

    missing = []
    for tool_key, tool_info in manifest.items():
        if tool_info.get("available") is False and tool_info.get("source") in ("blackarch", "arch"):
            missing.append((tool_key, tool_info))

    print(f"[+] Herramientas a revisar: {len(missing)}\n")

    results = {}
    for tool_key, tool_info in missing:
        pkg_name = tool_info.get("pkg", tool_key)
        category = tool_info.get("category", "")

        print(f"[{tool_key}]")
        print(f"    pkg={pkg_name}, categoria={category}")

        # 1. Ver si tenemos URL conocida
        known_url = KNOWN_GITHUB.get(tool_key)
        if known_url:
            if args.check_all and known_url:
                exists, desc = check_github_repo(known_url, args.token)
                if exists is True:
                    print(f"    ✓ CONFIRMADO: {known_url}")
                    results[tool_key] = known_url
                elif exists is False:
                    print(f"    ✗ URL conocida no existe: {known_url}")
                    results[tool_key] = None
                else:
                    print(f"    ? No se pudo verificar: {known_url}")
                    results[tool_key] = known_url
                time.sleep(0.5)
            else:
                print(f"    ? Posible: {known_url}")
                results[tool_key] = known_url
        else:
            # Generar URLs candidatas
            candidates = guess_github_urls(tool_key)
            if candidates:
                print(f"    ? Posibles candidatos (sin verificar):")
                for url in candidates[:5]:
                    print(f"      - {url}")
            else:
                print(f"    ? Sin candidatos conocidos")
            results[tool_key] = None

        print()

    # Guardar candidatos a archivo
    candidates_file = os.path.join(PROJECT_ROOT, "scripts", "github-candidates.txt")
    with open(candidates_file, 'w') as f:
        f.write("# GitHub candidates for review\n")
        f.write("# Formato: tool_key -> URL1, URL2, ...\n\n")
        for tool_key, tool_info in missing:
            parent = PARENT_PACKAGES.get(tool_key)
            if parent:
                f.write(f"{tool_key} -> [PART OF] {parent}\n")
                continue
            known_url = KNOWN_GITHUB.get(tool_key)
            if known_url:
                f.write(f"{tool_key} -> {known_url}  [KNOWN]\n")
            else:
                candidates = guess_github_urls(tool_key)
                if candidates:
                    f.write(f"{tool_key} -> {'; '.join(candidates[:8])}\n")
                else:
                    f.write(f"{tool_key} -> \n")

    print(f"\n[+] Candidatos guardados en: {candidates_file}")

    # Resumen
    print("=" * 60)
    print("RESUMEN:")
    print("=" * 60)
    found = {k: v for k, v in results.items() if v}
    not_found = {k: v for k, v in results.items() if not v}
    print(f"\n  Con GitHub conocido: {len(found)}")
    for k, v in found.items():
        print(f"    {k:30s} -> {v}")
    print(f"\n  Sin GitHub conocido: {len(not_found)}")
    for k in not_found:
        print(f"    {k}")

    print(f"\n[+] Sugerencia: ejecutá con --check-all y --token para verificar contra API")


if __name__ == "__main__":
    main()
