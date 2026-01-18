package com.bancario.compensacion.mapper;

import com.bancario.compensacion.dto.CicloDTO;
import com.bancario.compensacion.dto.PosicionDTO;
import com.bancario.compensacion.dto.ArchivoDTO;
import com.bancario.compensacion.modelo.ArchivoLiquidacion;
import com.bancario.compensacion.modelo.CicloCompensacion;
import com.bancario.compensacion.modelo.PosicionInstitucion;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class CompensacionMapper {

    public CicloDTO toDTO(CicloCompensacion entity) {
        if (entity == null)
            return null;
        return CicloDTO.builder()
                .id(entity.getId())
                .numeroCiclo(entity.getNumeroCiclo())
                .descripcion(entity.getDescripcion())
                .estado(entity.getEstado())
                .fechaApertura(entity.getFechaApertura())
                .fechaCierre(entity.getFechaCierre())
                .build();
    }

    public PosicionDTO toDTO(PosicionInstitucion entity) {
        if (entity == null)
            return null;
        return PosicionDTO.builder()
                .id(entity.getId())
                .idCiclo(entity.getCiclo() != null ? entity.getCiclo().getId() : null)
                .codigoBic(entity.getCodigoBic())
                .saldoInicial(entity.getSaldoInicial())
                .totalDebitos(entity.getTotalDebitos())
                .totalCreditos(entity.getTotalCreditos())
                .neto(entity.getNeto()) // Asegurar que neto siempre se calcule o venga de DB
                .build();
    }

    public ArchivoDTO toDTO(ArchivoLiquidacion entity) {
        if (entity == null)
            return null;
        return ArchivoDTO.builder()
                .id(entity.getId())
                .nombre(entity.getNombre())
                .xmlContenido(entity.getXmlContenido()) // Cuidado si es muy grande, tal vez omitir en listas
                .firmaJws(entity.getFirmaJws())
                .canalEnvio(entity.getCanalEnvio())
                .estado(entity.getEstado())
                .fechaGeneracion(entity.getFechaGeneracion())
                .build();
    }

    public List<PosicionDTO> toPosicionList(List<PosicionInstitucion> entities) {
        if (entities == null)
            return List.of();
        return entities.stream().map(this::toDTO).collect(Collectors.toList());
    }
}
