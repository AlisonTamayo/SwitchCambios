package com.bancario.compensacion.service;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.bancario.compensacion.dto.CreateSolicitudDevolucionRequest;
import com.bancario.compensacion.dto.SolicitudDevolucionDTO;
import com.bancario.compensacion.exception.BusinessException;
import com.bancario.compensacion.exception.NotFoundException;
import com.bancario.compensacion.model.SolicitudDevolucion;
import com.bancario.compensacion.repository.CatalogoErrorRepository;
import com.bancario.compensacion.repository.SolicitudDevolucionRepository;

@Slf4j
@Service
public class SolicitudDevolucionService {

    private final SolicitudDevolucionRepository repo;
    private final CatalogoErrorRepository catalogoErrorRepository;

    public SolicitudDevolucionService(
            SolicitudDevolucionRepository repo,
            CatalogoErrorRepository catalogoErrorRepository
    ) {
        this.repo = repo;
        this.catalogoErrorRepository = catalogoErrorRepository;
    }

    @Transactional
    public SolicitudDevolucionDTO create(CreateSolicitudDevolucionRequest req) {
        log.info("Creating SolicitudDevolucion id={} original={}", req.getId(), req.getIdInstruccionOriginal());

        if (!catalogoErrorRepository.existsById(req.getCodigoMotivo())) {
            try {
                throw new BusinessException("MOTIVO_INVALIDO", "El códigoMotivo no existe en el Catálogo de Errores.");
            } catch (BusinessException e) {
                throw e;
            }
        }

        if (repo.existsById(req.getId())) {
            throw new BusinessException("DEVOLUCION_EXISTS", "La solicitud de devolución ya existe (id duplicado).");
        }

        SolicitudDevolucion e = new SolicitudDevolucion(req.getId());
        e.setIdInstruccionOriginal(req.getIdInstruccionOriginal());
        e.setCodigoMotivo(req.getCodigoMotivo());
        e.setEstado(req.getEstado());
        e.setFechaSolicitud(OffsetDateTime.now());

        SolicitudDevolucion saved = repo.save(e);
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public SolicitudDevolucionDTO getById(UUID id) {
        SolicitudDevolucion e = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("DEVOLUCION_NOT_FOUND", "Solicitud de devolución no encontrada."));
        return toDto(e);
    }

    @Transactional(readOnly = true)
    public List<SolicitudDevolucionDTO> findByOriginal(UUID idInstruccionOriginal) {
        return repo.findByIdInstruccionOriginal(idInstruccionOriginal).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public SolicitudDevolucionDTO updateEstado(UUID id, String estado) {
        log.info("Updating estado devolucion id={} estado={}", id, estado);

        if (!estado.matches("RECEIVED|REVERSED|FAILED")) {
            throw new BusinessException("ESTADO_INVALIDO", "Estado inválido. Use RECEIVED, REVERSED, FAILED.");
        }

        SolicitudDevolucion e = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("DEVOLUCION_NOT_FOUND", "Solicitud de devolución no encontrada."));
        e.setEstado(estado);

        return toDto(repo.save(e));
    }

    private SolicitudDevolucionDTO toDto(SolicitudDevolucion e) {
        return new SolicitudDevolucionDTO(
                e.getId(),
                e.getIdInstruccionOriginal(),
                e.getCodigoMotivo(),
                e.getEstado(),
                e.getFechaSolicitud()
        );
    }
}
