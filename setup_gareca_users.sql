-- ==============================================================================
-- TABLA DE USUARIOS Y CONTROL MULTI-DISPOSITIVO CON BCRYPT SEGURO — GARECATV
-- Base de Datos: Supabase (pfhjrplnfuupftgzdnop)
-- Archivo: setup_gareca_users.sql
-- Instrucciones: Ejecuta todo este script en Supabase -> SQL Editor -> Run
-- ==============================================================================

-- 1. Habilitar extensión pgcrypto para hashing seguro bcrypt
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- 2. Crear tabla principal visible en el Table Editor de Supabase
CREATE TABLE IF NOT EXISTS public.gareca_users (
    email TEXT PRIMARY KEY,
    password_hash TEXT,                     -- Hash seguro bcrypt ($2a$10$...). NULL si accede con cuenta Google
    auth_provider TEXT NOT NULL DEFAULT 'password', -- 'google' o 'password'
    status TEXT NOT NULL DEFAULT 'enabled', -- 'enabled' (activo) o 'disabled' (suspendido)
    max_devices INT NOT NULL DEFAULT 1,     -- Cantidad de pantallas simultáneas permitidas
    active_device_id TEXT,                  -- ID único de la Smart TV activa
    device_model TEXT,                      -- Ej. "SkyworthDigital FlowBox-F2", "Xiaomi MiBox"
    device_brand TEXT,                      -- Ej. "FLOW", "Xiaomi"
    android_version TEXT,                   -- Ej. "Android 14 (API 34)"
    app_version TEXT DEFAULT '1.5.0',       -- Versión instalada
    last_login_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Índices de alto rendimiento
CREATE INDEX IF NOT EXISTS idx_gareca_users_status ON public.gareca_users(status);
CREATE INDEX IF NOT EXISTS idx_gareca_users_last_login ON public.gareca_users(last_login_at DESC);

-- Habilitar Row Level Security (RLS)
ALTER TABLE public.gareca_users ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Permitir acceso publico a gareca_users" ON public.gareca_users;
CREATE POLICY "Permitir acceso publico a gareca_users"
ON public.gareca_users FOR ALL TO anon, authenticated
USING (true) WITH CHECK (true);

-- 3. Función segura para inicio de sesión con Credenciales (Bcrypt)
CREATE OR REPLACE FUNCTION public.fn_gareca_login_credentials(
    p_email TEXT,
    p_password TEXT,
    p_device_id TEXT,
    p_device_model TEXT,
    p_device_brand TEXT,
    p_android_version TEXT
)
RETURNS JSONB AS $$
DECLARE
    v_user RECORD;
    v_clean_email TEXT;
BEGIN
    v_clean_email := LOWER(TRIM(p_email));
    SELECT * INTO v_user FROM public.gareca_users WHERE LOWER(email) = v_clean_email;
    
    IF NOT FOUND THEN
        RETURN jsonb_build_object('status', 'error', 'message', 'Usuario o correo no encontrado');
    END IF;
    
    -- Si el usuario se registró con Google y no tiene contraseña
    IF v_user.password_hash IS NULL THEN
        RETURN jsonb_build_object('status', 'error', 'message', 'Esta cuenta está vinculada a Google. Usa el botón "Acceder con Google" abajo.');
    END IF;
    
    -- Verificar contraseña con bcrypt seguro (pgcrypto)
    IF v_user.password_hash != crypt(p_password, v_user.password_hash) THEN
        RETURN jsonb_build_object('status', 'error', 'message', 'Contraseña incorrecta');
    END IF;
    
    -- Verificar si está suspendido/deshabilitado
    IF LOWER(v_user.status) = 'disabled' THEN
        RETURN jsonb_build_object('status', 'disabled', 'message', 'Tu cuenta ha sido deshabilitada por el administrador.');
    END IF;
    
    -- Actualizar dispositivo activo y última conexión
    UPDATE public.gareca_users
    SET active_device_id = p_device_id,
        device_model = p_device_model,
        device_brand = p_device_brand,
        android_version = p_android_version,
        last_login_at = NOW()
    WHERE email = v_user.email;
    
    RETURN jsonb_build_object(
        'status', 'success',
        'email', v_user.email,
        'max_devices', v_user.max_devices,
        'active_device_id', p_device_id
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 4. Función para inicio de sesión con Cuenta Google del TV
CREATE OR REPLACE FUNCTION public.fn_gareca_login_google(
    p_email TEXT,
    p_device_id TEXT,
    p_device_model TEXT,
    p_device_brand TEXT,
    p_android_version TEXT
)
RETURNS JSONB AS $$
DECLARE
    v_user RECORD;
    v_clean_email TEXT;
BEGIN
    v_clean_email := LOWER(TRIM(p_email));
    SELECT * INTO v_user FROM public.gareca_users WHERE LOWER(email) = v_clean_email;
    
    IF NOT FOUND THEN
        -- Auto-registro inicial de la cuenta Google
        INSERT INTO public.gareca_users (
            email, password_hash, auth_provider, status, max_devices,
            active_device_id, device_model, device_brand, android_version, last_login_at
        ) VALUES (
            v_clean_email, NULL, 'google', 'enabled', 1,
            p_device_id, p_device_model, p_device_brand, p_android_version, NOW()
        );
        
        RETURN jsonb_build_object(
            'status', 'success',
            'email', v_clean_email,
            'max_devices', 1,
            'active_device_id', p_device_id
        );
    END IF;
    
    -- Si ya existe, verificar si está deshabilitada
    IF LOWER(v_user.status) = 'disabled' THEN
        RETURN jsonb_build_object('status', 'disabled', 'message', 'Tu cuenta ha sido deshabilitada por el administrador.');
    END IF;
    
    -- Actualizar dispositivo activo y última conexión
    UPDATE public.gareca_users
    SET active_device_id = p_device_id,
        device_model = p_device_model,
        device_brand = p_device_brand,
        android_version = p_android_version,
        last_login_at = NOW()
    WHERE email = v_user.email;
    
    RETURN jsonb_build_object(
        'status', 'success',
        'email', v_user.email,
        'max_devices', v_user.max_devices,
        'active_device_id', p_device_id
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 5. Insertar usuario administrador con contraseña cifrada bcrypt seguro (cost 10)
INSERT INTO public.gareca_users (
    email, password_hash, auth_provider, status, max_devices, device_model
) VALUES (
    'admin@garecatv.com',
    crypt('GarecaTV2024Secure!', gen_salt('bf', 10)),
    'password',
    'enabled',
    5,
    'Master Console'
)
ON CONFLICT (email) DO UPDATE 
SET password_hash = crypt('GarecaTV2024Secure!', gen_salt('bf', 10)),
    status = 'enabled';

NOTIFY pgrst, 'reload schema';
