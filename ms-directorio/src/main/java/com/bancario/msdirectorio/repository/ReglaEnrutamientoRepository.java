package com.bancario.msdirectorio.repository;

import com.bancario.msdirectorio.model.ReglaEnrutamiento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReglaEnrutamientoRepository extends JpaRepository<ReglaEnrutamiento, Integer> {
    // Buscar reglas por el prefijo BIN (ej: 450001)
    Optional<ReglaEnrutamiento> findByPrefijoBin(String prefijoBin);
}