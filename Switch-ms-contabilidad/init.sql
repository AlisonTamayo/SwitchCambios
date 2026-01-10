-- Script de inicialización para la base de datos de Contabilidad

-- Crear las tablas si no existen
CREATE TABLE IF NOT EXISTS Cuenta (
    id SERIAL PRIMARY KEY,
    numeroCuenta VARCHAR(255) NOT NULL UNIQUE,
    referenciaClienteId VARCHAR(255) NOT NULL,
    saldo NUMERIC(19, 2) NOT NULL DEFAULT 0.00,
    version BIGINT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS Transaccion (
    id BIGSERIAL PRIMARY KEY,
    cuentaId INTEGER NOT NULL REFERENCES Cuenta(id),
    monto NUMERIC(19, 2) NOT NULL,
    tipo VARCHAR(50) NOT NULL,
    referenciaUuid VARCHAR(255) NOT NULL,
    fechaCreacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Índices para mejorar el rendimiento
CREATE INDEX IF NOT EXISTS idx_cuenta_numero ON Cuenta(numeroCuenta);
CREATE INDEX IF NOT EXISTS idx_transaccion_cuenta ON Transaccion(cuentaId);
CREATE INDEX IF NOT EXISTS idx_transaccion_fecha ON Transaccion(fechaCreacion);

-- Datos de prueba (opcional)
INSERT INTO Cuenta (numeroCuenta, referenciaClienteId, saldo) 
VALUES 
    ('1234567890', 'CLI-001', 1000.00),
    ('0987654321', 'CLI-002', 2500.50)
ON CONFLICT (numeroCuenta) DO NOTHING;
