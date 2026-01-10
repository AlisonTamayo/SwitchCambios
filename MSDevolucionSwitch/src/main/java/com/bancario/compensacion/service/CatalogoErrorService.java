package com.bancario.compensacion.service;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bancario.compensacion.repository.CatalogoErrorRepository;

import java.util.List;

import com.bancario.compensacion.dto.CatalogoErrorDTO;
import com.bancario.compensacion.dto.CreateCatalogoErrorRequest;
import com.bancario.compensacion.exception.BusinessException;
import com.bancario.compensacion.exception.NotFoundException;
import com.bancario.compensacion.model.CatalogoError;

@Slf4j
@Service
public class CatalogoErrorService {

    private final CatalogoErrorRepository repo;

    public CatalogoErrorService(CatalogoErrorRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public CatalogoErrorDTO create(CreateCatalogoErrorRequest req) {
        log.info("Creating CatalogoError codigo={}", req.getCodigo());

        if (repo.existsById(req.getCodigo())) {
            throw new BusinessException("CATALOGO_ERROR_EXISTS", "El código ya existe en el catálogo.");
        }

        CatalogoError e = new CatalogoError(req.getCodigo());
        e.setDescripcion(req.getDescripcion());

        CatalogoError saved = repo.save(e);
        return new CatalogoErrorDTO(saved.getCodigo(), saved.getDescripcion());
    }

    @Transactional(readOnly = true)
    public CatalogoErrorDTO getByCodigo(String codigo) {
        CatalogoError e = repo.findById(codigo)
                .orElseThrow(() -> new NotFoundException("CATALOGO_ERROR_NOT_FOUND", "Código no encontrado."));
        return new CatalogoErrorDTO(e.getCodigo(), e.getDescripcion());
    }

    @Transactional(readOnly = true)
    public List<CatalogoErrorDTO> list() {
        return repo.findAll().stream()
                .map(e -> new CatalogoErrorDTO(e.getCodigo(), e.getDescripcion()))
                .toList();
    }
}
