package com.switchbank.mscontabilidad.controlador;

import com.switchbank.mscontabilidad.dto.CuentaDTO;
import com.switchbank.mscontabilidad.dto.RecargaRequest;
import com.switchbank.mscontabilidad.servicio.ContabilidadServicio;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/funding")
@RequiredArgsConstructor
@Tag(name = "Fondeo y Liquidez", description = "Operaciones de Recarga y Verificación de Fondos")
public class FondeoControlador {

    private final ContabilidadServicio service;

    @PostMapping("/recharge")
    @Operation(summary = "Recargar Fondos", description = "Inyecta liquidez en la cuenta de un banco (Simulación Banco Central).")
    public ResponseEntity<CuentaDTO> recargarSaldo(@RequestBody RecargaRequest req) {
        return ResponseEntity.ok(service.recargarSaldo(req.getBic(), req.getMonto(), req.getIdInstruccion()));
    }

    @GetMapping("/available/{bic}/{monto}")
    @Operation(summary = "Verificar Disponibilidad", description = "Check booleano de fondos suficientes.")
    public ResponseEntity<Map<String, Object>> verificarSaldo(@PathVariable String bic,
            @PathVariable BigDecimal monto) {
        boolean disponible = service.verificarSaldo(bic, monto);
        return ResponseEntity.ok(Map.of(
                "bic", bic,
                "disponible", disponible,
                "montoRequerido", monto));
    }
}
