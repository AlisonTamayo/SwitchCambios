package com.bancario.msdirectorio.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import com.bancario.msdirectorio.model.Institucion;

@Repository
public interface InstitucionRepository extends MongoRepository<Institucion, String> {

    Optional<Institucion> findByReglasEnrutamientoPrefijoBin(String prefijoBin);

    Optional<Institucion> findByCodigoBic(String codigoBic);

    List<Institucion> findByEstadoOperativo(String estadoOperativo);

    @Query("{ 'interruptorCircuito.estaAbierto' : true }")
    List<Institucion> findBancosConCircuitoAbierto();
}