-- ==============================================================================
-- TABLA DE USUARIOS Y CONTROL MULTI-DISPOSITIVO — GARECATV
-- Base de Datos: Supabase (pfhjrplnfuupftgzdnop)
-- Archivo: setup_gareca_users.sql
-- ==============================================================================

-- 1. Crear tabla principal de usuarios y control de sesiones
CREATE TABLE IF NOT EXISTS public.gareca_users (
    email TEXT PRIMARY KEY,
    status TEXT NOT NULL DEFAULT 'enabled', -- 'enabled' (activo) o 'disabled' (bloqueado)
    max_devices INT NOT NULL DEFAULT 1,     -- Cantidad de dispositivos permitidos simultáneos (configurable por admin)
    active_device_id TEXT,                  -- Identificador único del dispositivo actualmente activo
    device_model TEXT,                      -- Modelo de la Smart TV (ej. "SkyworthDigital FlowBox-F2")
    device_brand TEXT,                      -- Fabricante (ej. "FLOW", "Xiaomi", "Sony")
    android_version TEXT,                   -- Versión del SO (ej. "Android 14 (API 34)")
    app_version TEXT DEFAULT '1.5.0',       -- Versión de GarecaTV instalada
    last_login_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Índices de alto rendimiento
CREATE INDEX IF NOT EXISTS idx_gareca_users_status 
ON public.gareca_users (status);

CREATE INDEX IF NOT EXISTS idx_gareca_users_last_login 
ON public.gareca_users (last_login_at DESC);

-- Habilitar Row Level Security (RLS)
ALTER TABLE public.gareca_users ENABLE ROW LEVEL SECURITY;

-- Limpieza segura de políticas previas
DO $$
BEGIN
    DROP POLICY IF EXISTS "Permitir acceso publico a gareca_users" ON public.gareca_users;
END $$;

-- Política de acceso (permite consultas, inserción y actualización desde la app)
CREATE POLICY "Permitir acceso publico a gareca_users"
ON public.gareca_users
FOR ALL
TO anon, authenticated
USING (true)
WITH CHECK (true);

-- Insertar usuario administrador inicial por defecto
INSERT INTO public.gareca_users (email, status, max_devices, device_model)
VALUES ('admin@garecatv.com', 'enabled', 5, 'Master Admin Console')
ON CONFLICT (email) DO NOTHING;

-- Notificar a PostgREST para recargar el esquema inmediatamente
NOTIFY pgrst, 'reload schema';
