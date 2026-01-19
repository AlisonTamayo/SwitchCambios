package com.switchbank.mscontabilidad.repositorio;

import org.springframework.data.jpa.repository.JpaRepository;

import com.switchbank.mscontabilidad.modelo.Movimiento;

import java.util.List;
import java.util.UUID;

public interface MovimientoRepository extends JpaRepository<Movimiento, Long> {
    List<Movimiento> findByCuentaId(UUID idCuenta);

    List<Movimiento> findByIdInstruccion(UUID idInstruccion);

    List<Movimiento> findByFechaRegistroBetween(java.time.LocalDateTime start, java.time.LocalDateTime end);

    boolean existsByTipoAndReferenciaId(com.switchbank.mscontabilidad.modelo.TipoMovimiento tipo, UUID referenciaId);
}