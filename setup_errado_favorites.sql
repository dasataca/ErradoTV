-- ==============================================================================
-- TABLA DE FAVORITOS EN LA NUBE PARA SMART TV — ERRADO / GARECA TV
-- Base de Datos: Supabase (pfhjrplnfuupftgzdnop)
-- Archivo: setup_errado_favorites.sql
-- ==============================================================================

CREATE TABLE IF NOT EXISTS public.errado_user_favorites (
    email TEXT NOT NULL,
    channel_key TEXT NOT NULL,
    source TEXT NOT NULL DEFAULT 'cxtv',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (email, channel_key, source)
);

-- Índices para consultas por usuario y fuente
CREATE INDEX IF NOT EXISTS idx_user_favs_email ON public.errado_user_favorites(email);
CREATE INDEX IF NOT EXISTS idx_user_favs_source ON public.errado_user_favorites(source);

-- Habilitar Row Level Security (RLS)
ALTER TABLE public.errado_user_favorites ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Permitir acceso publico a errado_user_favorites" ON public.errado_user_favorites;
CREATE POLICY "Permitir acceso publico a errado_user_favorites"
ON public.errado_user_favorites FOR ALL TO anon, authenticated
USING (true) WITH CHECK (true);
