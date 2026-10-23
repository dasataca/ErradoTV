#!/usr/bin/env python3
"""
Extractor y Generador de Lista M3U para Errado TV.
Descarga y descifra las transmisiones y partidos del día desde los servidores de Cloudflare Pages.
Genera un archivo 'errado_tv.m3u8' compatible con VLC, TiviMate, OTT Navigator, etc.
"""

import base64
import hashlib
import json
import subprocess
import urllib.request

KEY_B64 = "R29sYXpvb0FwcFNlY3JldEtleTIwMjRTZWN1cmUhIQ=="
REMOTE_GIST_URL = "https://gist.githubusercontent.com/dasataca/b5f27059b9db7e878e6a0fab05a7a951/raw/config.json"

try:
    req = urllib.request.Request(REMOTE_GIST_URL, headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(req, timeout=4) as resp:
        gist_data = json.loads(resp.read().decode("utf-8"))
        if gist_data.get("active_key"):
            KEY_B64 = gist_data["active_key"]
except Exception:
    pass

AES_KEY_HEX = hashlib.sha256(KEY_B64.encode("utf-8")).digest().hex()

URL_EVENTS = "https://errado-app.pages.dev/data/event.json"
URL_DESTACADO = "https://errado-app.pages.dev/data/destacado.json"

def decrypt_payload(b64_str: str) -> str:
    s = b64_str.strip()
    if s.startswith("{"):
        return s
    raw = base64.b64decode(s)
    if len(raw) < 16:
        return ""
    iv_hex = raw[:16].hex()
    cipher_bytes = raw[16:]
    p = subprocess.Popen(
        ["openssl", "enc", "-d", "-aes-256-cbc", "-K", AES_KEY_HEX, "-iv", iv_hex],
        stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE
    )
    out, _ = p.communicate(cipher_bytes)
    return out.decode("utf-8", "ignore")

def fetch_json(url: str):
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(req, timeout=10) as resp:
        content = resp.read().decode("utf-8", "ignore")
    decrypted = decrypt_payload(content)
    return json.loads(decrypted) if decrypted else {}

def resolve_config_stream(url: str):
    try:
        req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
        with urllib.request.urlopen(req, timeout=5) as resp:
            data = json.loads(resp.read().decode("utf-8", "ignore"))
            return data.get("url", url)
    except Exception:
        return url

def main():
    print("[*] Obteniendo y descifrando datos de Errado TV...")
    
    m3u_lines = ["#EXTM3U x-tvg-url=\"\""]
    
    # 1. Eventos destacados
    try:
        destacado = fetch_json(URL_DESTACADO)
        channels = destacado.get("event_tv_channel", [])
        print(f"[*] Canales destacados encontrados: {len(channels)}")
        for ch in channels:
            name = ch.get("event_name_channel", "Canal")
            url = ch.get("event_url", "")
            logo = ch.get("event_image_url", "")
            if url:
                m3u_lines.append(f'#EXTINF:-1 tvg-logo="{logo}" group-title="Destacados",{name}')
                m3u_lines.append(url)
    except Exception as e:
        print(f"[!] Error procesando destacados: {e}")

    # 2. Partidos y eventos diarios
    try:
        events_data = fetch_json(URL_EVENTS)
        date_str = events_data.get("event_date", "Agenda")
        events = events_data.get("events", [])
        print(f"[*] Partidos de la fecha ({date_str}): {len(events)}")
        
        for ev in events:
            match_name = ev.get("event_name_time", "Partido").strip()
            for k in range(1, 17):
                url = ev.get(f"event_url_{k}", "").strip()
                t = ev.get(f"event_type_url_{k}", "exo").strip()
                if url and url != "null":
                    if t == "config":
                        url = resolve_config_stream(url)
                    m3u_lines.append(f'#EXTINF:-1 group-title="Partidos en Vivo ({date_str})",{match_name} [Opción {k} - {t.upper()}]')
                    m3u_lines.append(url)
    except Exception as e:
        print(f"[!] Error procesando eventos: {e}")

    output_file = "errado_tv.m3u8"
    with open(output_file, "w", encoding="utf-8") as f:
        f.write("\n".join(m3u_lines) + "\n")

    print(f"[+] Archivo generado exitosamente: {output_file}")
    print(f"[+] Total de entradas generadas: {len(m3u_lines) // 2}")

if __name__ == "__main__":
    import argparse
    import time

    parser = argparse.ArgumentParser(description="Generador M3U para Errado TV")
    parser.add_argument("--loop", "-l", type=int, default=0, help="Minutos entre actualizaciones automáticas (ej. --loop 30)")
    args = parser.parse_args()

    if args.loop > 0:
        print(f"[*] Modo automático activado: actualizando cada {args.loop} minutos.")
        while True:
            try:
                main()
            except Exception as e:
                print(f"[!] Error en ciclo: {e}")
            print(f"[*] Próxima actualización en {args.loop} minutos. Presiona Ctrl+C para detener.\n")
            time.sleep(args.loop * 60)
    else:
        main()
