#!/usr/bin/env python3
"""
Visor de Logs en Tiempo Real para ErradoTV desde Supabase.
Uso:
    python3 view_logs.py [--limit 50] [--watch]
"""

import sys
import json
import time
import argparse
import urllib.request

SUPABASE_URL = "https://pfhjrplnfuupftgzdnop.supabase.co/rest/v1"
SERVICE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InBmaGpycGxuZnV1cGZ0Z3pkbm9wIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc4ODk1Nzg0MSwiZXhwIjoyMTA0NTMzODQxfQ.g79nmXleslc4M7W_hmJZs-CvwkEcFfTMUQHB79RK4ME"

def fetch_logs(limit=50):
    # Probar primero en errado_tv_logs, si no en karaoke_system_logs
    for table in ["errado_tv_logs", "karaoke_system_logs?room_code=eq.ERRADO_TV"]:
        url = f"{SUPABASE_URL}/{table}&order=created_at.desc&limit={limit}" if "?" in table else f"{SUPABASE_URL}/{table}?order=created_at.desc&limit={limit}"
        req = urllib.request.Request(url, headers={
            "apikey": SERVICE_KEY,
            "Authorization": f"Bearer {SERVICE_KEY}"
        })
        try:
            with urllib.request.urlopen(req, timeout=5) as resp:
                data = json.loads(resp.read().decode())
                if data or table.startswith("errado_tv_logs"):
                    return data
        except Exception:
            continue
    return []

def print_log(row):
    created = row.get("created_at", "")[:19].replace("T", " ")
    level = row.get("level", "info").upper()
    event = row.get("event", "EVENT")
    msg = row.get("message", "")
    device = row.get("device_model") or row.get("details", {}).get("device_model", "TV")
    
    color = "\033[92m" if level == "INFO" else "\033[93m" if level == "WARN" else "\033[91m"
    reset = "\033[0m"
    
    print(f"[{created}] {color}[{level:5s}]{reset} [{event}] {msg} ({device})")
    details = row.get("details")
    if details and isinstance(details, dict) and len(details) > 0:
        details_str = json.dumps(details, ensure_ascii=False)
        if len(details_str) > 120:
            details_str = details_str[:120] + "..."
        print(f"       └── Details: {details_str}")

def main():
    parser = argparse.ArgumentParser(description="Visor de Telemetría ErradoTV")
    parser.add_argument("--limit", "-n", type=int, default=20, help="Número de logs a mostrar")
    parser.add_argument("--watch", "-w", action="store_true", help="Seguir logs en tiempo real")
    args = parser.parse_args()

    print("==================================================")
    print("      TELEMETRÍA EN VIVO — ERRADO TV (SUPABASE)   ")
    print("==================================================")

    if args.watch:
        seen_ids = set()
        print("[*] Monitoreando logs en vivo... Presiona Ctrl+C para salir.\n")
        while True:
            try:
                logs = fetch_logs(limit=20)
                for log in reversed(logs):
                    log_id = log.get("id")
                    if log_id not in seen_ids:
                        seen_ids.add(log_id)
                        print_log(log)
            except Exception as e:
                print(f"[!] Error: {e}")
            time.sleep(3)
    else:
        logs = fetch_logs(limit=args.limit)
        if not logs:
            print("[*] No se encontraron logs registrados aún.")
        else:
            for log in reversed(logs):
                print_log(log)

if __name__ == "__main__":
    main()
