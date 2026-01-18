package com.bancario.devolucion.mapper;

import com.bancario.devolucion.dto.CatalogoErrorDTO;
import com.bancario.devolucion.dto.SolicitudDevolucionDTO;
import com.bancario.devolucion.modelo.CatalogoError;
import com.bancario.devolucion.modelo.SolicitudDevolucion;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class DevolucionMapper {

    public SolicitudDevolucionDTO toDTO(SolicitudDevolucion entity) {
        if (entity == null)
            return null;
        return SolicitudDevolucionDTO.builder()
                .id(entity.getId())
                .idInstruccionOriginal(entity.getIdInstruccionOriginal())
                .codigoMotivo(entity.getCodigoMotivo())
                .descripcionMotivo(entity.getMotivo() != null ? entity.getMotivo().getDescripcion() : null)
                .estado(entity.getEstado())
                .fechaSolicitud(entity.getFechaSolicitud())
                .build();
    }

    public CatalogoErrorDTO toDTO(CatalogoError entity) {
        if (entity == null)
            return null;
        return CatalogoErrorDTO.builder()
                .codigo(entity.getCodigo())
                .descripcion(entity.getDescripcion())
                .build();
    }

    public List<SolicitudDevolucionDTO> toSolicitudList(List<SolicitudDevolucion> entities) {
        if (entities == null)
            return List.of();
        return entities.stream().map(this::toDTO).collect(Collectors.toList());
    }

    public List<CatalogoErrorDTO> toCatalogoList(List<CatalogoError> entities) {
        if (entities == null)
            return List.of();
        return entities.stream().map(this::toDTO).collect(Collectors.toList());
    }
}
