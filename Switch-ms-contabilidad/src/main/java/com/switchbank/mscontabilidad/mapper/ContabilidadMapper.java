package com.switchbank.mscontabilidad.mapper;

import com.switchbank.mscontabilidad.dto.CuentaDTO;
import com.switchbank.mscontabilidad.dto.MovimientoDTO;
import com.switchbank.mscontabilidad.modelo.CuentaTecnica;
import com.switchbank.mscontabilidad.modelo.Movimiento;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class ContabilidadMapper {

    public CuentaDTO toDTO(CuentaTecnica entidad) {
        if (entidad == null)
            return null;
        return CuentaDTO.builder()
                .id(entidad.getId())
                .codigoBic(entidad.getCodigoBic())
                .saldoDisponible(entidad.getSaldoDisponible())
                .firmaIntegridad(entidad.getFirmaIntegridad())
                .build();
    }

    public MovimientoDTO toDTO(Movimiento entidad) {
        if (entidad == null)
            return null;
        return MovimientoDTO.builder()
                .id(entidad.getId())
                .idInstruccion(entidad.getIdInstruccion())
                .tipo(entidad.getTipo().name())
                .monto(entidad.getMonto())
                .saldoResultante(entidad.getSaldoResultante())
                .fechaRegistro(entidad.getFechaRegistro())
                .referenciaId(entidad.getReferenciaId())
                .codigoBicCuenta(entidad.getCuenta() != null ? entidad.getCuenta().getCodigoBic() : null)
                .build();
    }

    public List<MovimientoDTO> toDTOList(List<Movimiento> entidades) {
        if (entidades == null)
            return List.of();
        return entidades.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
}
