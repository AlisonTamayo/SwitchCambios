package com.bancario.msdirectorio.repository;

import com.bancario.msdirectorio.model.InterruptorCircuito;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InterruptorRepository extends JpaRepository<InterruptorCircuito, String> {
}