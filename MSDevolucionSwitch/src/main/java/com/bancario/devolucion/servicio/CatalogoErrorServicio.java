package com.bancario.devolucion.servicio;

import com.bancario.devolucion.dto.CatalogoErrorDTO;
import com.bancario.devolucion.mapper.DevolucionMapper;
import com.bancario.devolucion.modelo.CatalogoError;
import com.bancario.devolucion.repositorio.CatalogoErrorRepositorio;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CatalogoErrorServicio {

    private final CatalogoErrorRepositorio repo;
    private final DevolucionMapper mapper;

    @Transactional(readOnly = true)
    public List<CatalogoErrorDTO> findAll() {
        return mapper.toCatalogoList(repo.findAll());
    }

    @Transactional
    public CatalogoErrorDTO create(CatalogoErrorDTO dto) {
        if (repo.existsById(dto.getCodigo())) {
            throw new RuntimeException("El código de error ya existe.");
        }
        CatalogoError entity = new CatalogoError(dto.getCodigo());
        entity.setDescripcion(dto.getDescripcion());
        return mapper.toDTO(repo.save(entity));
    }
}
