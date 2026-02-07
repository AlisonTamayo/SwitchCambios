package com.bancario.compensacion.repositorio;

import com.bancario.compensacion.modelo.DetalleCompensacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DetalleCompensacionRepositorio extends JpaRepository<DetalleCompensacion, Long> {
    List<DetalleCompensacion> findByCicloId(Integer cicloId);

    List<DetalleCompensacion> findByIdInstruccion(UUID idInstruccion);
}
