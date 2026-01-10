package com.bancario.nucleo.repository;

import com.bancario.nucleo.model.Transaccion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransaccionRepository extends JpaRepository<Transaccion, UUID> {
    Optional<Transaccion> findByReferenciaRed(String referenciaRed);
}
