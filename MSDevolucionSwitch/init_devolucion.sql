CREATE TABLE IF NOT EXISTS solicitudDevolucion (
    idDevolucion UUID PRIMARY KEY,
    idInstruccionOriginal UUID,      -- Link a transacción fallida
    codigoMotivo VARCHAR(10),        -- ISO pacs.004
    descripcionMotivo TEXT,
    estado VARCHAR(20),              -- PENDING, REVERSED, FAILED
    fechaSolicitud TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
