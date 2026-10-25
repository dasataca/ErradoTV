#!/usr/bin/env python3
"""
=============================================================================
🛡️ Herramienta de Cifrado de Sobre para GarecaTV (Anti-Sniffing / Anti-Copia)
=============================================================================
Uso:
    python3 encrypt_key.py [CLAVE_BASE64] [--update-json]

Ejemplos:
    python3 encrypt_key.py "R29sYXpvb0FwcFNlY3JldEtleTIwMjRTZWN1cmUhIQ=="
    python3 encrypt_key.py "R29sYXpvb0FwcFNlY3JldEtleTIwMjRTZWN1cmUhIQ==" --update-json
"""

import sys
import os
import json
import hashlib
import subprocess

SEED = b'GarecaTV_Envelope_Secret_2026_Protection'

def encrypt_envelope(plain_key: str) -> str:
    key_bytes = hashlib.sha256(SEED).digest()
    iv = os.urandom(16)
    data = plain_key.strip().encode('utf-8')
    
    # PKCS7 padding
    pad_len = 16 - (len(data) % 16)
    padded = data + bytes([pad_len] * pad_len)
    
    proc = subprocess.Popen(
        ['openssl', 'enc', '-aes-256-cbc', '-K', key_bytes.hex(), '-iv', iv.hex()],
        stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE
    )
    ciphertext, err = proc.communicate(padded)
    if proc.returncode != 0:
        raise RuntimeError(f"Error ejecutando openssl: {err.decode()}")
        
    return iv.hex() + ciphertext.hex()

def decrypt_envelope(envelope_hex: str) -> str:
    key_bytes = hashlib.sha256(SEED).digest()
    iv_hex = envelope_hex[:32]
    cipher_bytes = bytes.fromhex(envelope_hex[32:])
    
    proc = subprocess.Popen(
        ['openssl', 'enc', '-d', '-aes-256-cbc', '-K', key_bytes.hex(), '-iv', iv_hex],
        stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE
    )
    decrypted, err = proc.communicate(cipher_bytes)
    if proc.returncode != 0:
        raise RuntimeError(f"Error descifrando: {err.decode()}")
        
    pad_len = decrypted[-1]
    return decrypted[:-pad_len].decode('utf-8')

def main():
    default_key = "R29sYXpvb0FwcFNlY3JldEtleTIwMjRTZWN1cmUhIQ=="
    update_json = "--update-json" in sys.argv
    args = [a for a in sys.argv[1:] if a != "--update-json"]
    
    raw_key = args[0] if len(args) > 0 else default_key
    
    print("\n" + "="*70)
    print("🔒 GENERADOR DE SOBRE CIFRADO PARA GARECATV")
    print("="*70)
    print(f"🔑 Clave original a proteger: {raw_key}")
    
    enc_hex = encrypt_envelope(raw_key)
    
    # Verificación síncrona
    decrypted_check = decrypt_envelope(enc_hex)
    assert decrypted_check == raw_key, "Error de verificación criptográfica"
    
    print("\n✅ ¡Clave blindada exitosamente!")
    print("\n📦 Copia esta línea para tu archivo 'version.json':")
    print("-" * 70)
    print(f'  "active_key_enc": "{enc_hex}",')
    print("-" * 70)
    
    # Si se pasó --update-json o si existe version.json en la raíz
    repo_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    version_json_path = os.path.join(repo_root, "version.json")
    if not os.path.exists(version_json_path):
        version_json_path = os.path.join(os.getcwd(), "version.json")
        
    if update_json and os.path.exists(version_json_path):
        with open(version_json_path, "r", encoding="utf-8") as f:
            vdata = json.load(f)
            
        # Reemplazar o actualizar
        vdata["active_key_enc"] = enc_hex
        if "active_key" in vdata:
            del vdata["active_key"]
            
        with open(version_json_path, "w", encoding="utf-8") as f:
            json.dump(vdata, f, indent=2, ensure_ascii=False)
            f.write("\n")
            
        print(f"\n🚀 Archivo '{version_json_path}' actualizado automáticamente con éxito.")
    else:
        print(f"\n💡 Tip: Puedes ejecutar con '--update-json' para actualizar 'version.json' automáticamente:")
        print(f"   python3 herramientas/cifrador_clave/encrypt_key.py \"{raw_key}\" --update-json")
    print("="*70 + "\n")

if __name__ == "__main__":
    main()
