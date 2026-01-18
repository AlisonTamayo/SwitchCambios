package com.bancario.devolucion.repositorio;

import com.bancario.devolucion.modelo.SolicitudDevolucion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface SolicitudDevolucionRepositorio extends JpaRepository<SolicitudDevolucion, UUID> {
    java.util.List<SolicitudDevolucion> findByIdInstruccionOriginal(UUID idInstruccionOriginal);
}
