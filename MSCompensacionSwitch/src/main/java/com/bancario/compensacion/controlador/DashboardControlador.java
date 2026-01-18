package com.bancario.compensacion.controlador;

import com.bancario.compensacion.dto.CicloDTO;
import com.bancario.compensacion.servicio.CompensacionServicio;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/dashboard")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Tag(name = "Monitor Dashboard", description = "Monitor de estado operacional y semáforos")
public class DashboardControlador {

    private final CompensacionServicio service;

    @GetMapping("/monitor")
    @Operation(summary = "Monitor Sistema", description = "Endpoint para verificar el estado SEMÁFORO del clearing.")
    public ResponseEntity<Map<String, Object>> obtenerEstadoMonitor() {
        Map<String, Object> response = new HashMap<>();

        CicloDTO ciclo = service.listarCiclos().stream()
                .filter(c -> "ABIERTO".equals(c.getEstado()))
                .findFirst()
                .orElse(null);

        if (ciclo != null) {
            response.put("estadoSistema", "OPERATIVO");
            response.put("colorSemaforo", "VERDE");
            response.put("cicloActivo", ciclo.getNumeroCiclo());
            response.put("horaInicio", ciclo.getFechaApertura());
        } else {
            response.put("estadoSistema", "CERRADO");
            response.put("colorSemaforo", "ROJO");
            response.put("mensaje", "Esperando inicio de operaciones");
        }

        return ResponseEntity.ok(response);
    }
}
