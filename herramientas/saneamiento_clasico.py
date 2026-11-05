#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
saneamiento_clasico.py — Motor de saneamiento, aceleración y verificación de salud de la Señal Clásica.
Aplica el mismo pipeline de alto rendimiento de la Señal Extendida a la Señal Clásica:
1. Verificación global contra health monitor (23,000+ flujos HLS).
2. Upgrades automáticos de enlaces web a HLS directos .m3u8 desde fuentes verificadas.
3. Sondeo multi-hilo concurrente (ThreadPoolExecutor) a 0.8s para canales no indexados.
4. Marcado estricto de is_available: true/false para relegar señales caídas al fondo.
"""

import urllib.request
import json
import re
import os
import sys
import time
import concurrent.futures

BASE_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
CXTV_CATALOG_PATH = os.path.join(BASE_DIR, 'cxtv_catalog.json')
ASSETS_CATALOG_PATH = os.path.join(BASE_DIR, 'app/src/main/assets/cxtv_catalog.json')

def norm(s):
    return re.sub(r'[^a-z0-9]', '', (s or '').lower())

def fetch_json(url):
    req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'})
    with urllib.request.urlopen(req, timeout=25) as resp:
        return json.loads(resp.read().decode('utf-8'))

def check_hls_stream(url, timeout=2.5):
    """Realiza un chequeo HTTP rápido para determinar disponibilidad de señal."""
    headers = {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
        'Range': 'bytes=0-1024'
    }
    # Intentar primero HEAD
    try:
        req = urllib.request.Request(url, headers=headers, method='HEAD')
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            if resp.status in (200, 206, 301, 302, 307, 308):
                return True, resp.status
    except Exception:
        pass

    # Si HEAD falla o es rechazado (muchos CDN bloquean HEAD), intentar GET con Range pequeño
    try:
        req = urllib.request.Request(url, headers=headers)
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            if resp.status in (200, 206, 301, 302, 307, 308):
                return True, resp.status
    except Exception as e:
        return False, str(e)[:40]

    return False, "unknown_error"

def main():
    print("==================================================")
    print("🚀 INICIANDO SANEAMIENTO Y ACELERACIÓN DE SEÑAL CLÁSICA")
    print("==================================================")

    if not os.path.exists(CXTV_CATALOG_PATH):
        print(f"[!] Error: No se encontró {CXTV_CATALOG_PATH}", file=sys.stderr)
        sys.exit(1)

    with open(CXTV_CATALOG_PATH, 'r', encoding='utf-8') as f:
        cxtv_data = json.load(f)

    channels = cxtv_data.get('channels', [])
    print(f"[*] Total de canales en Señal Clásica: {len(channels)}")

    # 1. Descarga del monitor de salud global
    print("[*] Descargando base global de salud de streams (health monitor)...")
    try:
        health_data = fetch_json("https://freetv.studio/api/data/health.json")
        print(f"[*] Base de salud descargada: {len(health_data)} señales monitoreadas.")
    except Exception as e:
        print(f"[!] Advertencia: No se pudo descargar health.json: {e}")
        health_data = {}

    # 2. Descarga del catálogo para upgrades a HLS directos
    print("[*] Descargando catálogo de referencia para rescate y upgrade de canales...")
    ftv_online_streams = {}
    try:
        ftv_cat = fetch_json("https://freetv.studio/api/data/catalog.json")
        for ch in ftv_cat.get('channels', []):
            streams = ch.get('streams', [])
            online = [s for s in streams if health_data.get(s.get('url', ''), {}).get('status') == 'online']
            if online:
                k = norm(ch.get('name', ''))
                if k:
                    ftv_online_streams[k] = online[0]['url']
        print(f"[*] Mapeados {len(ftv_online_streams)} canales con HLS directo verificado.")
    except Exception as e:
        print(f"[!] Advertencia: No se pudo mapear catálogo de referencia: {e}")

    # 3. Aplicar mejoras y resolver estado de cada canal
    upgraded_count = 0
    to_probe = []
    resolved = {}

    for ch in channels:
        ch_id = ch.get('id', '')
        name = ch.get('name', '')
        url = ch.get('stream_url', '')
        st_type = ch.get('stream_type', 'none')
        k = norm(name)

        # Chequear si podemos upgradear a HLS directo
        curr_health = health_data.get(url, {}).get('status', 'unknown')
        if (curr_health == 'offline' or st_type == 'web') and k in ftv_online_streams:
            new_url = ftv_online_streams[k]
            ch['stream_url'] = new_url
            ch['stream_type'] = 'exo'
            ch['is_available'] = True
            resolved[ch_id] = True
            upgraded_count += 1
            continue

        # Si es YouTube, está disponible de inmediato
        if st_type == 'youtube':
            ch['is_available'] = True
            resolved[ch_id] = True
            continue

        # Si está en el health monitor
        if curr_health == 'online':
            ch['is_available'] = True
            resolved[ch_id] = True
            continue
        elif curr_health == 'offline':
            ch['is_available'] = False
            resolved[ch_id] = False
            continue

        # Si aún es desconocido y es exo o web, agregar a la cola de sondeo concurrente
        to_probe.append(ch)

    print(f"[*] Canales resueltos por base global: {len(resolved)}")
    print(f"[*] Canales con upgrade directo a HLS: {upgraded_count}")
    print(f"[*] Canales a sondear en vivo concurrentemente: {len(to_probe)}")

    # 4. Sondeo concurrente multi-hilo
    start_time = time.time()
    def probe_worker(channel):
        url = channel.get('stream_url', '')
        ok, detail = check_hls_stream(url)
        return channel, ok, detail

    probe_online = 0
    probe_offline = 0

    with concurrent.futures.ThreadPoolExecutor(max_workers=45) as executor:
        future_to_ch = {executor.submit(probe_worker, ch): ch for ch in to_probe}
        for future in concurrent.futures.as_completed(future_to_ch):
            ch, is_ok, detail = future.result()
            ch['is_available'] = is_ok
            if is_ok:
                probe_online += 1
            else:
                probe_offline += 1

    elapsed = time.time() - start_time
    print(f"[*] Sondeo en vivo finalizado en {elapsed:.1f} segundos.")
    print(f"    - Online detectados: {probe_online}")
    print(f"    - Offline/caídos detectados: {probe_offline}")

    # 5. Estadísticas globales del catálogo saneado
    total_avail = sum(1 for c in channels if c.get('is_available', True))
    total_unavail = len(channels) - total_avail
    avail_pct = (total_avail / len(channels)) * 100

    print("--------------------------------------------------")
    print(f"📊 RESULTADOS FINALES DE SEÑAL CLÁSICA:")
    print(f"   🟢 Canales 100% Online (cabecera inmediata): {total_avail} ({avail_pct:.1f}%)")
    print(f"   ⚪ Canales Caídos (relegados al fondo): {total_unavail}")
    print(f"   ⚡ Canales HLS directos ExoPlayer: {sum(1 for c in channels if c.get('stream_type') == 'exo')}")
    print(f"   🔴 Canales YouTube Live: {sum(1 for c in channels if c.get('stream_type') == 'youtube')}")
    print("--------------------------------------------------")

    # 6. Actualizar timestamp y guardar archivos
    cxtv_data['generated_at'] = time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime())
    cxtv_data['total_available'] = total_avail
    cxtv_data['total_unavailable'] = total_unavail

    with open(CXTV_CATALOG_PATH, 'w', encoding='utf-8') as f:
        json.dump(cxtv_data, f, ensure_ascii=False, indent=2)

    with open(ASSETS_CATALOG_PATH, 'w', encoding='utf-8') as f:
        json.dump(cxtv_data, f, ensure_ascii=False, indent=2)

    print(f"✅ Archivo raíz actualizado: {CXTV_CATALOG_PATH}")
    print(f"✅ Archivo assets actualizado: {ASSETS_CATALOG_PATH}")
    print("🚀 Saneamiento completado con éxito.")

if __name__ == '__main__':
    main()
