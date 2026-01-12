-- Script de inicialización para la base de datos de Contabilidad (v3.0)

-- Eliminar tablas viejas si existen para evitar conflictos en dev
DROP TABLE IF EXISTS Transaccion;
DROP TABLE IF EXISTS Cuenta;
-- Eliminar tablas actuales para asegurar recreación limpia
DROP TABLE IF EXISTS Movimiento;
DROP TABLE IF EXISTS CuentaTecnica;

-- 1. Tabla de Cuentas Técnicas (Bancos)
CREATE TABLE IF NOT EXISTS CuentaTecnica (
    id UUID PRIMARY KEY,
    codigoBic VARCHAR(20) NOT NULL UNIQUE,
    saldoDisponible NUMERIC(18, 2) NOT NULL DEFAULT 0.00,
    firmaIntegridad TEXT NOT NULL -- Hash SHA-256
);

-- 2. Tabla de Movimientos Inmutables
CREATE TABLE IF NOT EXISTS Movimiento (
    id BIGSERIAL PRIMARY KEY,
    idCuenta UUID NOT NULL REFERENCES CuentaTecnica(id),
    idInstruccion UUID NOT NULL,
    tipo VARCHAR(50) NOT NULL, -- DEBIT, CREDIT, RECHARGE, REVERSAL
    monto NUMERIC(18, 2) NOT NULL,
    saldoResultante NUMERIC(18, 2) NOT NULL,
    fechaRegistro TIMESTAMP NOT NULL,
    referenciaId UUID -- Para Reversos (Link a transacción original)
);

-- Índices para optimización
CREATE INDEX IF NOT EXISTS idx_movimiento_cuenta ON Movimiento(idCuenta);
CREATE INDEX IF NOT EXISTS idx_movimiento_fecha ON Movimiento(fechaRegistro);
CREATE INDEX IF NOT EXISTS idx_movimiento_instruccion ON Movimiento(idInstruccion);
CREATE INDEX IF NOT EXISTS idx_movimiento_referencia ON Movimiento(referenciaId); -- Vital para RF-07 (validación duplicados)

-- Datos Semilla (Seed Data) - BANCOS OFICIALES DEL SISTEMA
-- Nota: Inicializamos con un saldo base para pruebas.
-- Los Hash son placeholders. En producción real, estos valores deben coincidir con la lógica SHA-256 del servicio.

INSERT INTO CuentaTecnica (id, codigoBic, saldoDisponible, firmaIntegridad)
VALUES 
    -- ARCBANK: Banco Operativo
    (gen_random_uuid(), 'ARCBANK', 5000000.00, 'INITIAL_HASH_ARCBANK_SECURE'), 
    
    -- BANTEC: Banco Operativo
    (gen_random_uuid(), 'BANTEC',  8500000.00, 'INITIAL_HASH_BANTEC_SECURE'),
    
    -- ECUSOL (Banco Test)
    (gen_random_uuid(), 'ECUSOL_BK', 1000000.00, 'INITIAL_HASH_ECUSOL'),
    
    -- NEXUS (Banco Test)
    (gen_random_uuid(), 'NEXUS_BANK', 500000.00, 'INITIAL_HASH_NEXUS')
ON CONFLICT (codigoBic) DO NOTHING;
