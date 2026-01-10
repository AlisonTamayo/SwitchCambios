package com.bancario.msdirectorio.repository;

import com.bancario.msdirectorio.model.Institucion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InstitucionRepository extends JpaRepository<Institucion, String> {
    // Spring Data genera la query automáticamente por el nombre del método
    boolean existsByCodigoBic(String codigoBic);
}