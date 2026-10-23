#!/usr/bin/env python3
"""
Extractor automático de Claves de Cifrado y Endpoints para Errado / Golazo App.
Uso:
    python3 extract_key.py [ruta_a_la_apk_o_carpeta]

Busca en los archivos DEX el método criptográfico, extrae la clave AES en Base64,
calcula la derivación SHA-256 y verifica la clave contra los servidores en vivo.
"""

import os
import sys
import glob
import base64
import hashlib
import zipfile
import tempfile
import subprocess
import urllib.request
import re

TEST_ENDPOINT = "https://errado-app.pages.dev/data/event.json"

def get_sample_payload():
    try:
        req = urllib.request.Request(TEST_ENDPOINT, headers={"User-Agent": "Mozilla/5.0"})
        with urllib.request.urlopen(req, timeout=8) as resp:
            content = resp.read().decode("utf-8", "ignore").strip()
        raw = base64.b64decode(content)
        if len(raw) > 16:
            return raw[:16].hex(), raw[16:]
    except Exception as e:
        print(f"[!] Advertencia al conectar con {TEST_ENDPOINT}: {e}")
    return None, None

def test_key(b64_key: str, iv_hex: str, cipher_bytes: bytes) -> bool:
    try:
        aes_key = hashlib.sha256(b64_key.encode("utf-8")).digest().hex()
        p = subprocess.Popen(
            ["openssl", "enc", "-d", "-aes-256-cbc", "-K", aes_key, "-iv", iv_hex],
            stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE
        )
        out, _ = p.communicate(cipher_bytes)
        text = out.decode("utf-8", "ignore")
        return "{" in text and ("events" in text or "event_date" in text)
    except Exception:
        return False

def extract_from_apk(apk_path: str, iv_hex: str, cipher_bytes: bytes):
    print(f"[*] Analizando APK: {apk_path}")
    found_keys = []
    
    with tempfile.TemporaryDirectory() as tmp_dir:
        try:
            with zipfile.ZipFile(apk_path, "r") as z:
                dex_files = [n for n in z.namelist() if n.startswith("classes") and n.endswith(".dex")]
                print(f"[*] Analizando DEX principales: {dex_files}")
                for d in dex_files:
                    z.extract(d, tmp_dir)
        except Exception as e:
            print(f"[!] Error extrayendo APK: {e}")
            return found_keys

        for dex_path in glob.glob(os.path.join(tmp_dir, "*.dex")):
            with open(dex_path, "rb") as f:
                data = f.read()

            # 1. Candidatos directos con padding Base64
            matches = re.findall(rb"[A-Za-z0-9+/]{30,50}={1,2}", data)
            for m in matches:
                cand = m.decode("ascii", errors="ignore")
                if test_key(cand, iv_hex, cipher_bytes):
                    found_keys.append(cand)

    return list(set(found_keys))

def main():
    target = sys.argv[1] if len(sys.argv) > 1 else "/home/sataca/Descargas/mta.xer.errado-2_arm64-v8a/base.apk"
    
    if os.path.isdir(target):
        apks = glob.glob(os.path.join(target, "**/*.apk"), recursive=True)
        if not apks:
            print(f"[!] No se encontraron archivos APK en {target}")
            sys.exit(1)
        target = apks[0]
        
    if not os.path.exists(target):
        print(f"[!] Archivo no encontrado: {target}")
        sys.exit(1)

    print("==================================================")
    print("   EXTRACTOR DE CLAVES CRIPTOGRÁFICAS ERRADO TV   ")
    print("==================================================")
    
    print("[*] Descargando muestra cifrada en vivo...")
    iv_hex, cipher_bytes = get_sample_payload()
    if not iv_hex:
        print("[!] No se pudo obtener la muestra de prueba del servidor.")
        sys.exit(1)

    keys = extract_from_apk(target, iv_hex, cipher_bytes)
    
    if keys:
        print("\n[+] ¡CLAVE VÁLIDA EXTRAÍDA Y VERIFICADA!")
        for k in keys:
            print(f"  • Clave Base64: {k}")
            try:
                dec_str = base64.b64decode(k).decode("utf-8", "ignore")
                print(f"  • Texto decodificado: {dec_str}")
            except Exception:
                pass
            print(f"  • Clave AES-256 (SHA-256 Hex): {hashlib.sha256(k.encode()).digest().hex()}")
            print(f"  • Estado: 100% OPERATIVA con descifrado validado.")
    else:
        print("\n[!] No se encontraron claves nuevas en los DEX principales.")

if __name__ == "__main__":
    main()
