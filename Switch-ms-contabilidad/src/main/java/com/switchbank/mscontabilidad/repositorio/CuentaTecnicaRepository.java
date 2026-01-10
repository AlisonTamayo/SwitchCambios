package com.switchbank.mscontabilidad.repositorio;


import org.springframework.data.jpa.repository.JpaRepository;

import com.switchbank.mscontabilidad.modelo.CuentaTecnica;

import java.util.Optional;
import java.util.UUID;

public interface CuentaTecnicaRepository extends JpaRepository<CuentaTecnica, UUID> {
    Optional<CuentaTecnica> findByCodigoBic(String codigoBic);
}