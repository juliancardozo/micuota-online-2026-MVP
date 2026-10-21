-- Interruptores de la plataforma que el administrador cambia sin redesplegar (modo prueba, funciones por etapa).
CREATE TABLE IF NOT EXISTS platform_settings (
    setting_key VARCHAR(80) PRIMARY KEY,
    setting_value VARCHAR(255) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
