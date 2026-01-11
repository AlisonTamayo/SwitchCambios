package com.switchbank.mscontabilidad.controlador;

import com.switchbank.mscontabilidad.dto.CuentaDTO;
import com.switchbank.mscontabilidad.servicio.LedgerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/funding")
public class FundingController {

    private final LedgerService service;

    public FundingController(LedgerService service) {
        this.service = service;
    }

    @PostMapping("/recharge")
    public ResponseEntity<?> recargarSaldo(@RequestBody RecargaRequest req) {
        try {
            CuentaDTO cuenta = service.recargarSaldo(req.getBic(), req.getMonto(), req.getIdInstruccion());
            return ResponseEntity.ok(cuenta);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/available/{bic}/{monto}")
    public ResponseEntity<?> verificarSaldo(@PathVariable String bic, @PathVariable BigDecimal monto) {
        boolean disponible = service.verificarSaldo(bic, monto);
        return ResponseEntity.ok(Map.of(
                "bic", bic,
                "disponible", disponible,
                "montoRequerido", monto));
    }

    @lombok.Data
    public static class RecargaRequest {
        private String bic;
        private BigDecimal monto;
        private java.util.UUID idInstruccion;
    }
}
