package com.switchbank.mscontabilidad.repositorio;

import org.springframework.data.jpa.repository.JpaRepository;

import com.switchbank.mscontabilidad.modelo.Movimiento;

import java.util.List;
import java.util.UUID;

public interface MovimientoRepository extends JpaRepository<Movimiento, Long> {
    List<Movimiento> findByCuentaId(UUID idCuenta);
}