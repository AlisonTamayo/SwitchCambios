package com.bancario.devolucion.repositorio;

import com.bancario.devolucion.modelo.CatalogoError;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CatalogoErrorRepositorio extends JpaRepository<CatalogoError, String> {
}
