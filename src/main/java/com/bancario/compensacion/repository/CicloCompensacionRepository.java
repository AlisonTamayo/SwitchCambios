package com.bancario.compensacion.repository;

import com.bancario.compensacion.model.CicloCompensacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CicloCompensacionRepository extends JpaRepository<CicloCompensacion, Integer> {
}
