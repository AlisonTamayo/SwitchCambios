package com.bancario.devolucion.servicio;

import com.bancario.devolucion.dto.SolicitudCreacionDTO;
import com.bancario.devolucion.dto.SolicitudDevolucionDTO;
import com.bancario.devolucion.mapper.DevolucionMapper;
import com.bancario.devolucion.modelo.SolicitudDevolucion;
import com.bancario.devolucion.repositorio.CatalogoErrorRepositorio;
import com.bancario.devolucion.repositorio.SolicitudDevolucionRepositorio;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SolicitudDevolucionServicio {

    private final SolicitudDevolucionRepositorio repo;
    private final CatalogoErrorRepositorio catalogoErrorRepository;
    private final DevolucionMapper mapper;

    @Transactional
    public SolicitudDevolucionDTO create(SolicitudCreacionDTO req) {
        log.info("Creando Devolución id={} original={}", req.getId(), req.getIdInstruccionOriginal());

        log.info("Validando motivo '{}'", req.getCodigoMotivo());
        if (!catalogoErrorRepository.existsById(req.getCodigoMotivo())) {
            log.warn("Motivo '{}' desconocido. Mapeando a MS03 para procesar la devolución.", req.getCodigoMotivo());
            req.setCodigoMotivo("MS03");
        }

        if (repo.existsById(req.getId())) {
            throw new RuntimeException("DEVOLUCION_EXISTS: La solicitud de devolución ya existe.");
        }

        SolicitudDevolucion e = new SolicitudDevolucion();
        e.setId(req.getId());
        e.setIdInstruccionOriginal(req.getIdInstruccionOriginal());
        e.setCodigoMotivo(req.getCodigoMotivo());
        e.setEstado(req.getEstado());
        e.setFechaSolicitud(OffsetDateTime.now());

        SolicitudDevolucion saved = repo.save(e);
        log.info("Devolución creada exitosamente: {}", saved.getId());
        return mapper.toDTO(saved);
    }

    @Transactional(readOnly = true)
    public SolicitudDevolucionDTO getById(UUID id) {
        log.info("Consultando devolución por ID: {}", id);
        SolicitudDevolucion e = repo.findById(id)
                .orElseThrow(() -> new RuntimeException("DEVOLUCION_NOT_FOUND: Solicitud no encontrada."));
        return mapper.toDTO(e);
    }

    @Transactional(readOnly = true)
    public List<SolicitudDevolucionDTO> findByOriginal(UUID idInstruccionOriginal) {
        log.info("Buscando devoluciones para instrucción original: {}", idInstruccionOriginal);
        List<SolicitudDevolucion> list = repo.findByIdInstruccionOriginal(idInstruccionOriginal);
        return mapper.toSolicitudList(list);
    }

    @Transactional
    public SolicitudDevolucionDTO updateEstado(UUID id, String estado) {
        log.info("Actualizando estado devolución id={} estado={}", id, estado);

        if (!estado.matches("RECEIVED|REVERSED|FAILED")) {
            throw new RuntimeException("ESTADO_INVALIDO: Use RECEIVED, REVERSED, FAILED.");
        }

        SolicitudDevolucion e = repo.findById(id)
                .orElseThrow(() -> new RuntimeException("DEVOLUCION_NOT_FOUND: Solicitud no encontrada."));
        e.setEstado(estado);

        return mapper.toDTO(repo.save(e));
    }
}
