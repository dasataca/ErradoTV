#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
sync_freetv.py — Ingesta, filtrado por salud (online) y normalización del catálogo FreeTV.
Arquitectura Hexagonal: Adaptador de datos desacoplado para GarecaTV.
"""

import urllib.request
import json
import gzip
import os
import sys

BASE_URL = "https://freetv.studio/api/data"

HISPANIC_CODES = {
    'AR', 'BO', 'CL', 'CO', 'CR', 'CU', 'DO', 'EC', 'SV', 'GT', 
    'HN', 'MX', 'NI', 'PA', 'PY', 'PE', 'PR', 'ES', 'UY', 'VE'
}

# Países clave mundiales con buena infraestructura HLS
TOP_WORLD_CODES = {
    'US', 'BR', 'UK', 'GB', 'FR', 'IT', 'DE', 'PT', 'CA'
}

COUNTRY_NAMES_ES = {
    'AR': '🇦🇷 Argentina',
    'BO': '🇧🇴 Bolivia',
    'CL': '🇨🇱 Chile',
    'CO': '🇨🇴 Colombia',
    'CR': '🇨🇷 Costa Rica',
    'CU': '🇨🇺 Cuba',
    'DO': '🇩🇴 República Dominicana',
    'EC': '🇪🇨 Ecuador',
    'SV': '🇸🇻 El Salvador',
    'GT': '🇬🇹 Guatemala',
    'HN': '🇭🇳 Honduras',
    'MX': '🇲🇽 México',
    'NI': '🇳🇮 Nicaragua',
    'PA': '🇵🇦 Panamá',
    'PY': '🇵🇾 Paraguay',
    'PE': '🇵🇪 Perú',
    'PR': '🇵🇷 Puerto Rico',
    'ES': '🇪🇸 España',
    'UY': '🇺🇾 Uruguay',
    'VE': '🇻🇪 Venezuela',
    'US': '🇺🇸 Estados Unidos',
    'BR': '🇧🇷 Brasil',
    'IT': '🇮🇹 Italia',
    'FR': '🇫🇷 Francia',
    'DE': '🇩🇪 Alemania',
    'UK': '🇬🇧 Reino Unido',
    'GB': '🇬🇧 Reino Unido',
    'PT': '🇵🇹 Portugal',
    'CA': '🇨🇦 Canadá',
}

CATEGORY_NAMES_ES = {
    'news': 'Noticias',
    'sports': 'Deportes',
    'movies': 'Cine',
    'series': 'Series',
    'music': 'Música',
    'kids': 'Infantil',
    'animation': 'Animación',
    'entertainment': 'Entretenimiento',
    'documentary': 'Documental',
    'culture': 'Cultura',
    'comedy': 'Comedia',
    'cooking': 'Cocina',
    'science': 'Ciencia',
    'education': 'Educación',
    'general': 'Variedades',
    'religious': 'Religión',
    'travel': 'Viajes',
    'weather': 'Clima',
    'business': 'Negocios',
    'auto': 'Automovilismo',
    'lifestyle': 'Estilo de Vida',
    'classic': 'Clásicos',
    'family': 'Familia',
    'relax': 'Relax',
    'shop': 'Compras'
}

def fetch_json(endpoint):
    url = f"{BASE_URL}/{endpoint}"
    print(f"[*] Descargando {url} ...")
    req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'})
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.loads(resp.read().decode('utf-8'))

def main():
    try:
        catalog = fetch_json("catalog.json")
        health = fetch_json("health.json")
    except Exception as e:
        print(f"[!] Error descargando datos de FreeTV: {e}", file=sys.stderr)
        sys.exit(1)

    raw_channels = catalog.get('channels', [])
    print(f"[*] Total canales crudos en catálogo: {len(raw_channels)}")
    print(f"[*] Total streams monitoreados en health: {len(health)}")

    out_channels = []
    categories_count = {}
    countries_count = {}

    for ch in raw_channels:
        streams = ch.get('streams', [])
        # Filtrar solo streams con estado "online"
        online_streams = [s for s in streams if health.get(s.get('url', ''), {}).get('status') == 'online']
        if not online_streams:
            continue

        cc = ch.get('countryCode', '').upper()
        is_hispanic = 'spa' in ch.get('languageCodes', []) or cc in HISPANIC_CODES
        is_top_world = cc in TOP_WORLD_CODES

        # Incluir hispanos + top internacional
        if not (is_hispanic or is_top_world):
            continue

        best_stream = online_streams[0]
        stream_url = best_stream.get('url', '').strip()
        if not stream_url:
            continue

        cat_ids = ch.get('categoryIds', [])
        cat_id = cat_ids[0] if cat_ids else 'general'
        cat_es = CATEGORY_NAMES_ES.get(cat_id, 'Variedades')

        ctry_es = COUNTRY_NAMES_ES.get(cc)
        if not ctry_es:
            # Bandera genérica si no está en el mapa
            ctry_es = f"🌍 {cc}"

        lang = "Español" if is_hispanic else "Internacional"

        ch_obj = {
            'id': ch.get('id', ''),
            'name': ch.get('name', '').strip(),
            'category': cat_es,
            'country': ctry_es,
            'language': lang,
            'logo': ch.get('logoUrl') or '',
            'stream_url': stream_url,
            'stream_type': 'hls',
            'is_available': True
        }

        out_channels.append(ch_obj)
        categories_count[cat_es] = categories_count.get(cat_es, 0) + 1
        countries_count[ctry_es] = countries_count.get(ctry_es, 0) + 1

    categories_list = [{'name': k, 'count': v} for k, v in sorted(categories_count.items(), key=lambda x: x[1], reverse=True)]
    countries_list = [{'name': k, 'count': v} for k, v in sorted(countries_count.items(), key=lambda x: x[1], reverse=True)]

    payload = {
        'generated_at': catalog.get('generatedAtIso', '2026-11-04'),
        'total_channels': len(out_channels),
        'categories': categories_list,
        'countries': countries_list,
        'channels': out_channels
    }

    # Guardar en raíz y en app/src/main/assets/
    paths = [
        os.path.join(os.path.dirname(__file__), '..', 'freetv_catalog.json'),
        os.path.join(os.path.dirname(__file__), '..', 'app', 'src', 'main', 'assets', 'freetv_catalog.json')
    ]

    raw_json = json.dumps(payload, ensure_ascii=False, indent=2)
    for p in paths:
        target = os.path.abspath(p)
        os.makedirs(os.path.dirname(target), exist_ok=True)
        with open(target, 'w', encoding='utf-8') as f:
            f.write(raw_json)
        print(f"[+] Archivo guardado exitosamente: {target}")

    raw_size = len(raw_json.encode('utf-8'))
    gz_size = len(gzip.compress(raw_json.encode('utf-8'), 9))
    print(f"\n[=== RESUMEN DE PROCESAMIENTO ===]")
    print(f"Canales ONLINE curados: {len(out_channels)}")
    print(f"Categorías organizadas: {len(categories_list)}")
    print(f"Países organizados: {len(countries_list)}")
    print(f"Tamaño sin comprimir: {raw_size / 1024:.1f} KB")
    print(f"Tamaño comprimido en red (Gzip): {gz_size / 1024:.1f} KB")

if __name__ == '__main__':
    main()
