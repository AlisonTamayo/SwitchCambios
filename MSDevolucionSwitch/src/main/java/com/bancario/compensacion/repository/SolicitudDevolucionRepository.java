package com.bancario.compensacion.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

import com.bancario.compensacion.model.SolicitudDevolucion;

public interface SolicitudDevolucionRepository extends JpaRepository<SolicitudDevolucion, UUID> {
    List<SolicitudDevolucion> findByIdInstruccionOriginal(UUID idInstruccionOriginal);
}
