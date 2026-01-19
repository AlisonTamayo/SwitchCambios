package com.bancario.compensacion.repositorio;

import com.bancario.compensacion.modelo.PosicionInstitucion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PosicionInstitucionRepositorio extends JpaRepository<PosicionInstitucion, Integer> {

    Optional<PosicionInstitucion> findByCicloIdAndCodigoBic(Integer idCiclo, String codigoBic);

    List<PosicionInstitucion> findByCicloId(Integer idCiclo);
}
