package com.bancario.compensacion.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bancario.compensacion.model.CatalogoError;

public interface CatalogoErrorRepository extends JpaRepository<CatalogoError, String> { }
