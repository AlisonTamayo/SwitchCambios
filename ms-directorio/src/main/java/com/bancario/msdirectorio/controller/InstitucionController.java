package com.bancario.msdirectorio.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    private final DirectorioService directorioService;

    public InstitucionController(DirectorioService directorioService) {
        this.directorioService = directorioService;
    }

    @Operation(summary = "Registrar o actualizar un participante (Incluye Datos Técnicos e Interruptor)")
    @PostMapping("/instituciones")
    public ResponseEntity<?> registrarInstitucion(@RequestBody Institucion institucion) {
        try {
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

    @Operation(summary = "Añadir una regla de BIN a una institución existente")
    @PostMapping("/instituciones/{bic}/reglas")
    public ResponseEntity<Institucion> agregarRegla(@PathVariable String bic, @RequestBody ReglaEnrutamiento regla) {
        try {

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
                    if (inst.getInterruptorCircuito() != null && inst.getInterruptorCircuito().isEstaAbierto()) {
                        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(inst);
                    }
                    return ResponseEntity.ok(inst);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "REPORT: Registrar fallo técnico para control de Circuit Breaker")
    @PostMapping("/instituciones/{bic}/reportar-fallo")
    public ResponseEntity<Void> reportarFallo(@PathVariable String bic) {
        directorioService.registrarFallo(bic);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "RF-02: Actualización técnica restringida (Estado y API Key)")
    @PatchMapping("/instituciones/{bic}/operaciones")
    public ResponseEntity<Institucion> actualizarOperaciones(
            @PathVariable String bic,
            @RequestParam(required = false) String nuevoEstado,
            @RequestParam(required = false) String nuevaUrl) {

        try {
            Institucion actualizada = directorioService.actualizarParametrosRestringidos(bic, nuevoEstado, nuevaUrl);
            return ResponseEntity.ok(actualizada);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }

    }
}