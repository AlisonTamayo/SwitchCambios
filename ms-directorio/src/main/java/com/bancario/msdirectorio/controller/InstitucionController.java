package com.bancario.msdirectorio.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bancario.msdirectorio.model.Institucion;
import com.bancario.msdirectorio.model.ReglaEnrutamiento;
import com.bancario.msdirectorio.service.DirectorioService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
@Tag(name = "Directorio Bancario", description = "Gestión de participantes bajo modelo MongoDB e ISO 20022")
public class InstitucionController {

    @Autowired
    private DirectorioService directorioService;

    // ==========================================
    // GESTIÓN DE INSTITUCIONES (Participantes)
    // ==========================================

    @Operation(summary = "Registrar o actualizar un participante (Incluye Datos Técnicos e Interruptor)")
    @PostMapping("/instituciones")
    public ResponseEntity<?> registrarInstitucion(@RequestBody Institucion institucion) {
        try {
            // El _id ahora es el BIC, aseguramos integridad en el servicio
            Institucion guardada = directorioService.registrarInstitucion(institucion);
            return new ResponseEntity<>(guardada, HttpStatus.CREATED);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error al registrar: " + e.getMessage());
        }
    }

    @Operation(summary = "Listar directorio completo")
    @GetMapping("/instituciones")
    public ResponseEntity<List<Institucion>> listar() {
        return ResponseEntity.ok(directorioService.listarTodas());
    }

    @Operation(summary = "Obtener detalle de un banco por su BIC (_id)")
    @GetMapping("/instituciones/{bic}")
    public ResponseEntity<Institucion> obtenerPorBic(@PathVariable String bic) {
        return directorioService.buscarPorBic(bic)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ==========================================
    // ENRUTAMIENTO Y REGLAS (Lookup ISO 20022)
    // ==========================================

    @Operation(summary = "Añadir una regla de BIN a una institución existente")
    @PostMapping("/instituciones/{bic}/reglas")
    public ResponseEntity<Institucion> agregarRegla(@PathVariable String bic, @RequestBody ReglaEnrutamiento regla) {
        try {
            // Ahora la regla se añade a la lista interna del documento Institucion
            Institucion actualizada = directorioService.aniadirRegla(bic, regla);
            return ResponseEntity.ok(actualizada);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(summary = "LOOKUP: Descubrir destino por BIN (Lógica central del Switch)")
    @GetMapping("/lookup/{bin}")
    public ResponseEntity<Institucion> lookup(@PathVariable String bin) {
        return directorioService.descubrirBancoPorBin(bin)
                .map(inst -> {
                    // Validamos si el Circuit Breaker está abierto antes de responder
                    if (inst.getInterruptorCircuito() != null && inst.getInterruptorCircuito().isEstaAbierto()) {
                        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(inst);
                    }
                    return ResponseEntity.ok(inst);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ==========================================
    // RESILIENCIA (Circuit Breaker)
    // ==========================================

    @Operation(summary = "REPORT: Registrar fallo técnico para control de Circuit Breaker")
    @PostMapping("/instituciones/{bic}/reportar-fallo")
    public ResponseEntity<Void> reportarFallo(@PathVariable String bic) {
        // Incrementa fallosConsecutivos y abre el circuito si llega al umbral (5 fallos)
        directorioService.registrarFallo(bic);
        return ResponseEntity.ok().build();
    }
}