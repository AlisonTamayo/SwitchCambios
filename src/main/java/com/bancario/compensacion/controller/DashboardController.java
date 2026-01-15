package com.bancario.compensacion.controller;

import com.bancario.compensacion.model.CicloCompensacion;
import com.bancario.compensacion.service.CompensacionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/dashboard")
@CrossOrigin(origins = "*") 
public class DashboardController {

    @Autowired
    private CompensacionService service;

    @GetMapping("/monitor")
    public ResponseEntity<Map<String, Object>> obtenerEstadoMonitor() {
        Map<String, Object> response = new HashMap<>();
        
      
        CicloCompensacion ciclo = service.listarCiclos().stream()
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