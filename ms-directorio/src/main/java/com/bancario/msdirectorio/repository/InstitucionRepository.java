package com.bancario.msdirectorio.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import com.bancario.msdirectorio.model.Institucion;

@Repository
public interface InstitucionRepository extends MongoRepository<Institucion, String> {

    /**
     * RF-02: Búsqueda por BIN (Lookup). 
     * Spring Data MongoDB navega automáticamente por la lista 'reglasEnrutamiento'
     * y busca coincidencias en el campo 'prefijoBin'.
     */
    Optional<Institucion> findByReglasEnrutamientoPrefijoBin(String prefijoBin);

    /**
     * RF-02: Listar solo bancos operativos para el motor de enrutamiento.
     */
    List<Institucion> findByEstadoOperativo(String estadoOperativo);

    /**
     * RNF-AVA-02: Buscar bancos que tienen el circuito abierto (para monitoreo).
     */
    @Query("{ 'interruptorCircuito.estaAbierto' : true }")
    List<Institucion> findBancosConCircuitoAbierto();
}