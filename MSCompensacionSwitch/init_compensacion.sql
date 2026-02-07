CREATE TABLE IF NOT EXISTS cicloCompensacion (
    idCiclo SERIAL PRIMARY KEY,
    numeroCiclo INTEGER UNIQUE,
    estado VARCHAR(20),              -- OPEN, CLOSED, SETTLED
    fechaApertura TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    fechaCierre TIMESTAMP
);

CREATE TABLE IF NOT EXISTS detalleCompensacion (
    idDetalle BIGSERIAL PRIMARY KEY,
    idInstruccion UUID,
    idInstruccionOriginal UUID,      -- Para vincular reversos
    idCiclo INTEGER REFERENCES cicloCompensacion(idCiclo),
    tipoOperacion VARCHAR(10),       -- PAGO, REVERSO
    bicEmisor VARCHAR(20),           -- Clasificación rápida
    bicReceptor VARCHAR(20),         -- Clasificación rápida
    monto NUMERIC(18,2),
    estadoLiquidacion VARCHAR(20)    -- INCLUIDO, EXCLUIDO
);

CREATE TABLE IF NOT EXISTS posicionInstitucion (
    idPosicion BIGSERIAL PRIMARY KEY,
    idCiclo INTEGER REFERENCES cicloCompensacion(idCiclo),
    bic VARCHAR(20),
    totalDebitos NUMERIC(18,2),
    totalCredits NUMERIC(18,2),
    posicionNeta NUMERIC(18,2)
);

CREATE TABLE IF NOT EXISTS archivoLiquidacion (
    idArchivo BIGSERIAL PRIMARY KEY,
    idCiclo INTEGER REFERENCES cicloCompensacion(idCiclo),
    nombreArchivo VARCHAR(255),
    contenidoXml TEXT,               -- XML SIN FIRMA
    fechaGeneracion TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
