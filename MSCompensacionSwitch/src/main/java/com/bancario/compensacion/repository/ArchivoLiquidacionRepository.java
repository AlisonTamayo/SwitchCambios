package com.bancario.compensacion.repository;

import com.bancario.compensacion.model.ArchivoLiquidacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ArchivoLiquidacionRepository extends JpaRepository<ArchivoLiquidacion, Integer> {
    List<ArchivoLiquidacion> findByCicloId(Integer cicloId);
}
