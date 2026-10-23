-- ==============================================================================
-- TABLA DE TELEMETRÍA Y LOGS REMOTOS PARA ERRADO TV
-- Base de Datos: Supabase (pfhjrplnfuupftgzdnop)
-- Archivo: supabase_setup.sql
-- ==============================================================================

-- 1. Crear tabla principal para los logs del televisor y flujos
CREATE TABLE IF NOT EXISTS public.errado_tv_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    level TEXT NOT NULL DEFAULT 'info', -- 'info', 'warn', 'error', 'debug'
    event TEXT NOT NULL,                -- 'app_start', 'load_events', 'play_stream', 'player_error', etc.
    message TEXT NOT NULL,              -- Descripción legible
    device_model TEXT,                  -- Ej: "Xiaomi MiBox 4", "Sony Bravia", "Amazon FireTV"
    device_brand TEXT,                  -- Fabricante
    android_version TEXT,               -- Ej: "Android 12 (API 31)"
    app_version TEXT DEFAULT '1.0.0',   -- Versión de ErradoTV
    details JSONB DEFAULT '{}'::jsonb,  -- Metadata detallada (URLs de stream, errores técnicos, stacktrace)
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Índices de alto rendimiento para búsqueda y filtrado
CREATE INDEX IF NOT EXISTS idx_errado_tv_logs_created_at 
ON public.errado_tv_logs (created_at DESC);

CREATE INDEX IF NOT EXISTS idx_errado_tv_logs_level 
ON public.errado_tv_logs (level, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_errado_tv_logs_event 
ON public.errado_tv_logs (event, created_at DESC);

-- Habilitar Row Level Security (RLS)
ALTER TABLE public.errado_tv_logs ENABLE ROW LEVEL SECURITY;

-- Limpieza segura de políticas previas si ya existieran
DO $$
BEGIN
    DROP POLICY IF EXISTS "Permitir insercion anonima de logs errado_tv" ON public.errado_tv_logs;
    DROP POLICY IF EXISTS "Permitir lectura publica de logs errado_tv" ON public.errado_tv_logs;
END $$;

-- Política de inserción para la app (anónima y autenticada)
CREATE POLICY "Permitir insercion anonima de logs errado_tv"
ON public.errado_tv_logs
FOR INSERT
TO anon, authenticated
WITH CHECK (true);

-- Política de lectura para panel de análisis
CREATE POLICY "Permitir lectura publica de logs errado_tv"
ON public.errado_tv_logs
FOR SELECT
TO anon, authenticated
USING (true);

-- ==============================================================================
-- 2. FUNCIÓN Y DISPARADOR DE AUTO-LIMPIEZA (LÍMITE ESTRICTO DE 1,000 REGISTROS)
-- Cada vez que se insertan registros, se eliminan los más antiguos manteniendo
-- únicamente los 1,000 más recientes para que la base de datos nunca se sature.
-- ==============================================================================

CREATE OR REPLACE FUNCTION public.fn_cleanup_errado_tv_logs()
RETURNS TRIGGER AS $$
BEGIN
    DELETE FROM public.errado_tv_logs
    WHERE id NOT IN (
        SELECT id FROM public.errado_tv_logs
        ORDER BY created_at DESC
        LIMIT 1000
    );
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS tr_cleanup_errado_tv_logs ON public.errado_tv_logs;
CREATE TRIGGER tr_cleanup_errado_tv_logs
AFTER INSERT ON public.errado_tv_logs
FOR EACH STATEMENT
EXECUTE FUNCTION public.fn_cleanup_errado_tv_logs();

-- Notificar a PostgREST para recargar el esquema inmediatamente
NOTIFY pgrst, 'reload schema';
