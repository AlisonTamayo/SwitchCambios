CREATE TABLE IF NOT EXISTS cuentaTecnica (
    idCuenta UUID PRIMARY KEY,
    bic VARCHAR(20) UNIQUE NOT NULL,
    saldoDisponible NUMERIC(18,2) NOT NULL,
    fondosBloqueados NUMERIC(18,2) DEFAULT 0.00, -- Reserva para QUEUED
    firmaIntegridad VARCHAR(128),               -- Hash de seguridad de fila
    ultimaConciliacion TIMESTAMP
);

CREATE TABLE IF NOT EXISTS movimiento (
    idMovimiento BIGSERIAL PRIMARY KEY,
    idCuenta UUID REFERENCES cuentaTecnica(idCuenta),
    idInstruccion UUID,              -- Referencia lógica al Núcleo
    tipo VARCHAR(10),                -- DEBIT, CREDIT
    monto NUMERIC(18,2),
    saldoResultante NUMERIC(18,2),
    fechaRegistro TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Seed Data (Initial Data)
INSERT INTO cuentaTecnica (idCuenta, bic, saldoDisponible, fondosBloqueados, firmaIntegridad)
VALUES 
    (gen_random_uuid(), 'ARCBANK', 5000000.00, 0.00, 'INITIAL_HASH_ARCBANK'), 
    (gen_random_uuid(), 'BANTEC',  8500000.00, 0.00, 'INITIAL_HASH_BANTEC'),
    (gen_random_uuid(), 'ECUSOL_BK', 1000000.00, 0.00, 'INITIAL_HASH_ECUSOL'),
    (gen_random_uuid(), 'NEXUS_BANK', 500000.00, 0.00, 'INITIAL_HASH_NEXUS')
ON CONFLICT (bic) DO NOTHING;
