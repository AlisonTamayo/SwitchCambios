package com.bancario.compensacion.repositorio;

import com.bancario.compensacion.modelo.ArchivoLiquidacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ArchivoLiquidacionRepositorio extends JpaRepository<ArchivoLiquidacion, Integer> {
}
