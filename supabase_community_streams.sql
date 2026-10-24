-- ==============================================================================
-- TABLA EXCLUSIVA COMUNITARIA PARA ERRADO TV
-- Base de Datos: Supabase (pfhjrplnfuupftgzdnop)
-- Tabla: public.errado_community_streams
-- ==============================================================================

-- 1. Crear tabla exclusiva con stream_url como clave única para evitar duplicados
CREATE TABLE IF NOT EXISTS public.errado_community_streams (
    stream_url TEXT PRIMARY KEY,
    status TEXT NOT NULL DEFAULT 'unavailable', -- 'unavailable', 'active'
    event_title TEXT,
    failure_reason TEXT,
    failure_count INT NOT NULL DEFAULT 1,
    device_model TEXT,
    device_brand TEXT,
    last_reported_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 2. Índices de aceleración para Smart TVs
CREATE INDEX IF NOT EXISTS idx_errado_community_status 
ON public.errado_community_streams (status, last_reported_at DESC);

-- 3. Habilitar Seguridad a Nivel de Fila (RLS)
ALTER TABLE public.errado_community_streams ENABLE ROW LEVEL SECURITY;

-- 4. Limpieza segura de políticas previas si existieran
DO $$
BEGIN
    DROP POLICY IF EXISTS "Permitir acceso total a errado_community_streams" ON public.errado_community_streams;
END $$;

-- 5. Política para permitir lectura y reporte comunitario (anon y authenticated)
CREATE POLICY "Permitir acceso total a errado_community_streams"
ON public.errado_community_streams
FOR ALL
TO anon, authenticated
USING (true)
WITH CHECK (true);

-- 6. Recargar el caché del esquema de Supabase/PostgREST inmediatamente
NOTIFY pgrst, 'reload schema';
