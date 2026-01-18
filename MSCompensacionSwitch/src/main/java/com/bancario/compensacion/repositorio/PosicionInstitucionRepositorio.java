package com.bancario.compensacion.repositorio;

import com.bancario.compensacion.modelo.PosicionInstitucion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PosicionInstitucionRepositorio extends JpaRepository<PosicionInstitucion, Integer> {
    // Buscar posición de un banco dentro de un ciclo específico
    Optional<PosicionInstitucion> findByCicloIdAndCodigoBic(Integer idCiclo, String codigoBic);

    // Listar todas las posiciones de un ciclo
    List<PosicionInstitucion> findByCicloId(Integer idCiclo);
}
