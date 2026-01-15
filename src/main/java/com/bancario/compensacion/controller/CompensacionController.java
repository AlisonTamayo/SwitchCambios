package com.bancario.compensacion.controller;

import com.bancario.compensacion.model.ArchivoLiquidacion;
import com.bancario.compensacion.model.CicloCompensacion;
import com.bancario.compensacion.service.CompensacionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/compensacion")
@RequiredArgsConstructor
@Tag(name = "Microservicio de Compensación (G4)", description = "Gestión de Clearing, Settlement y Continuidad")
public class CompensacionController {

    private final CompensacionService service;


    @GetMapping("/ciclos")
    @Operation(summary = "Listar ciclos", description = "Obtiene el historial de todos los ciclos operativos.")
    public ResponseEntity<List<CicloCompensacion>> listarCiclos() {
        return ResponseEntity.ok(service.listarCiclos());
    }


    @PostMapping("/ciclos/{cicloId}/acumular")
    @Operation(summary = "INTERNAL: Acumular movimiento", 
               description = "Endpoint de alta velocidad usado por el Núcleo para registrar débitos/créditos en tiempo real.")
    public ResponseEntity<Void> acumular(
            @PathVariable Integer cicloId,
            @RequestParam String bic,
            @RequestParam BigDecimal monto,
            @RequestParam boolean esDebito) {

        service.acumularTransaccion(cicloId, bic, monto, esDebito);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/ciclos/{cicloId}/cierre")
    @Operation(summary = "EJECUTAR CIERRE DIARIO (Settlement)", 
               description = "1. Valida Suma Cero. 2. Genera XML. 3. Firma Digital (JWS). 4. Cierra el ciclo actual. 5. Abre el siguiente ciclo arrastrando saldos (Continuidad).")
    public ResponseEntity<ArchivoLiquidacion> cerrarCiclo(@PathVariable Integer cicloId) {
        return ResponseEntity.ok(service.realizarCierreDiario(cicloId));
    }
}