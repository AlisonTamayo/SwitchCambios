package com.bancario.msdirectorio.mapper;

import com.bancario.msdirectorio.dto.InstitucionDTO;
import com.bancario.msdirectorio.modelo.Institucion;
import com.bancario.msdirectorio.modelo.InterruptorCircuito;
import com.bancario.msdirectorio.modelo.ReglaEnrutamiento;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class InstitucionMapper {

    public InstitucionDTO toDTO(Institucion entity) {
        if (entity == null)
            return null;

        return InstitucionDTO.builder()
                .id(entity.getId())
                .codigoBic(entity.getCodigoBic())
                .nombre(entity.getNombre())
                .urlDestino(entity.getUrlDestino())
                .llavePublica(entity.getLlavePublica())
                .estadoOperativo(entity.getEstadoOperativo())
                .reglasEnrutamiento(mapReglasToDTO(entity.getReglasEnrutamiento()))
                .interruptorCircuito(mapCBToDTO(entity.getInterruptorCircuito()))
                .build();
    }

    public Institucion toEntity(InstitucionDTO dto) {
        if (dto == null)
            return null;

        Institucion entity = new Institucion();
        entity.setId(dto.getId());
        entity.setCodigoBic(dto.getCodigoBic());
        entity.setNombre(dto.getNombre());
        entity.setUrlDestino(dto.getUrlDestino());
        entity.setLlavePublica(dto.getLlavePublica());
        entity.setEstadoOperativo(dto.getEstadoOperativo());
        entity.setReglasEnrutamiento(mapReglasToEntity(dto.getReglasEnrutamiento()));
        entity.setInterruptorCircuito(mapCBToEntity(dto.getInterruptorCircuito()));
        return entity;
    }

    private List<InstitucionDTO.ReglaDTO> mapReglasToDTO(List<ReglaEnrutamiento> reglas) {
        if (reglas == null)
            return null;
        return reglas.stream()
                .map(r -> InstitucionDTO.ReglaDTO.builder()
                        .prefijoBin(r.getPrefijoBin())
                        .agente(r.getAgente())
                        .build())
                .collect(Collectors.toList());
    }

    private List<ReglaEnrutamiento> mapReglasToEntity(List<InstitucionDTO.ReglaDTO> dtos) {
        if (dtos == null)
            return null;
        return dtos.stream()
                .map(d -> new ReglaEnrutamiento(d.getPrefijoBin(), d.getAgente()))
                .collect(Collectors.toList());
    }

    private InstitucionDTO.CircuitBreakerDTO mapCBToDTO(InterruptorCircuito cb) {
        if (cb == null)
            return null;
        return InstitucionDTO.CircuitBreakerDTO.builder()
                .estaAbierto(cb.isEstaAbierto())
                .fallosConsecutivos(cb.getFallosConsecutivos())
                .ultimoFallo(cb.getUltimoFallo())
                .build();
    }

    private InterruptorCircuito mapCBToEntity(InstitucionDTO.CircuitBreakerDTO dto) {
        if (dto == null)
            return null;
        return new InterruptorCircuito(dto.isEstaAbierto(), dto.getFallosConsecutivos(), dto.getUltimoFallo());
    }

    public List<InstitucionDTO> toDTOList(List<Institucion> entities) {
        if (entities == null)
            return List.of();
        return entities.stream().map(this::toDTO).collect(Collectors.toList());
    }
}
