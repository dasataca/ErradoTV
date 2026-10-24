-- ==============================================================================
-- TABLA DE USUARIOS Y CONTROL MULTI-DISPOSITIVO CON BCRYPT SEGURO — ERRADO USERS
-- Base de Datos: Supabase (pfhjrplnfuupftgzdnop)
-- Archivo: setup_errado_users.sql
-- Instrucciones: Ejecuta todo este script en Supabase -> SQL Editor -> Run
-- ==============================================================================

-- 1. Habilitar extensión pgcrypto para hashing seguro bcrypt
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- 2. Crear tabla principal visible en el Table Editor de Supabase
CREATE TABLE IF NOT EXISTS public.errado_users (
    email TEXT PRIMARY KEY,
    password_hash TEXT,                             -- Hash seguro bcrypt ($2a$10$...). NULL si es cuenta Google
    auth_provider TEXT NOT NULL DEFAULT 'password', -- 'google' o 'password'
    status TEXT NOT NULL DEFAULT 'enabled',         -- 'enabled' (activo) o 'disabled' (suspendido)
    max_devices INT NOT NULL DEFAULT 1,             -- Pantallas simultáneas permitidas (configurable por admin)
    active_device_id TEXT,                          -- ID único de la Smart TV activa
    device_model TEXT,                              -- Modelo de TV (ej. "SkyworthDigital FlowBox-F2", "Xiaomi MiBox")
    device_brand TEXT,                              -- Fabricante (ej. "FLOW", "Xiaomi", "Sony")
    android_version TEXT,                           -- Versión de Android
    app_version TEXT DEFAULT '1.5.2',               -- Versión de la app
    last_login_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Índices de alto rendimiento
CREATE INDEX IF NOT EXISTS idx_errado_users_status ON public.errado_users(status);
CREATE INDEX IF NOT EXISTS idx_errado_users_last_login ON public.errado_users(last_login_at DESC);

-- Habilitar Row Level Security (RLS)
ALTER TABLE public.errado_users ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Permitir acceso publico a errado_users" ON public.errado_users;
CREATE POLICY "Permitir acceso publico a errado_users"
ON public.errado_users FOR ALL TO anon, authenticated
USING (true) WITH CHECK (true);

-- 3. Función segura para inicio de sesión con Credenciales (Bcrypt en base de datos)
CREATE OR REPLACE FUNCTION public.fn_errado_login_credentials(
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
    SELECT * INTO v_user FROM public.errado_users WHERE LOWER(email) = v_clean_email;
    
    IF NOT FOUND THEN
        RETURN jsonb_build_object('status', 'error', 'message', 'Usuario o correo no encontrado');
    END IF;
    
    IF v_user.password_hash IS NULL THEN
        RETURN jsonb_build_object('status', 'error', 'message', 'Esta cuenta está vinculada a Google. Usa el botón "Acceder con Google" abajo.');
    END IF;
    
    -- Verificación criptográfica segura con bcrypt
    IF v_user.password_hash != crypt(p_password, v_user.password_hash) THEN
        RETURN jsonb_build_object('status', 'error', 'message', 'Contraseña incorrecta');
    END IF;
    
    -- Si el administrador lo suspendió
    IF LOWER(v_user.status) = 'disabled' THEN
        RETURN jsonb_build_object('status', 'disabled', 'message', 'Tu cuenta ha sido deshabilitada por el administrador.');
    END IF;
    
    -- Actualizar dispositivo activo y fecha
    UPDATE public.errado_users
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

-- 4. Función segura para inicio de sesión con Cuenta Google del TV
CREATE OR REPLACE FUNCTION public.fn_errado_login_google(
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
    SELECT * INTO v_user FROM public.errado_users WHERE LOWER(email) = v_clean_email;
    
    IF NOT FOUND THEN
        -- Auto-registro inicial de cuenta Google con 1 dispositivo
        INSERT INTO public.errado_users (
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
    
    -- Actualizar dispositivo activo y fecha
    UPDATE public.errado_users
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

-- 5. Crear usuario administrador inicial con contraseña cifrada bcrypt
INSERT INTO public.errado_users (
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

-- 6. Recargar esquema para PostgREST
NOTIFY pgrst, 'reload schema';
