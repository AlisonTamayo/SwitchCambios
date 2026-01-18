package com.bancario.compensacion.repositorio;

import com.bancario.compensacion.modelo.CicloCompensacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CicloCompensacionRepositorio extends JpaRepository<CicloCompensacion, Integer> {
    Optional<CicloCompensacion> findByEstado(String estado);
}
