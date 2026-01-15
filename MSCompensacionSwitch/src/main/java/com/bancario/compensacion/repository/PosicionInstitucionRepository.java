package com.bancario.compensacion.repository;

import com.bancario.compensacion.model.PosicionInstitucion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PosicionInstitucionRepository extends JpaRepository<PosicionInstitucion, Integer> {
    
    Optional<PosicionInstitucion> findByCicloIdAndCodigoBic(Integer cicloId, String codigoBic);

    List<PosicionInstitucion> findByCicloId(Integer cicloId);
}