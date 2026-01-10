package com.bancario.compensacion.repository;

import com.bancario.compensacion.model.PosicionInstitucion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PosicionInstitucionRepository extends JpaRepository<PosicionInstitucion, Integer> {
    List<PosicionInstitucion> findByCicloId(Integer cicloId);
}
