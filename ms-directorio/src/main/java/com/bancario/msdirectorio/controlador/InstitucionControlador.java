package com.bancario.msdirectorio.controlador;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.bancario.msdirectorio.dto.InstitucionDTO;
import com.bancario.msdirectorio.servicio.DirectorioServicio;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Tag(name = "Directorio Bancario", description = "Gestión de participantes bajo modelo MongoDB e ISO 20022")
public class InstitucionControlador {

    private final DirectorioServicio directorioServicio;

    @Operation(summary = "Registrar o actualizar un participante (Incluye Datos Técnicos e Interruptor)")
    @PostMapping("/instituciones")
    public ResponseEntity<InstitucionDTO> registrarInstitucion(@RequestBody InstitucionDTO institucion) {
        InstitucionDTO guardada = directorioServicio.registrarInstitucion(institucion);
        return new ResponseEntity<>(guardada, HttpStatus.CREATED);
    }

    @Operation(summary = "Listar directorio completo")
    @GetMapping("/instituciones")
    public ResponseEntity<List<InstitucionDTO>> listar() {
        return ResponseEntity.ok(directorioServicio.listarTodas());
    }

    @Operation(summary = "Obtener detalle de un banco por su BIC (_id)")
    @GetMapping("/instituciones/{bic}")
    public ResponseEntity<InstitucionDTO> obtenerPorBic(@PathVariable String bic) {
        return directorioServicio.buscarPorBic(bic)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Añadir una regla de BIN a una institución existente")
    @PostMapping("/instituciones/{bic}/reglas")
    public ResponseEntity<InstitucionDTO> agregarRegla(@PathVariable String bic,
            @RequestBody InstitucionDTO.ReglaDTO regla) {
        InstitucionDTO actualizada = directorioServicio.aniadirRegla(bic, regla);
        return ResponseEntity.ok(actualizada);
    }

    @Operation(summary = "LOOKUP: Descubrir destino por BIN (Lógica central del Switch)")
    @GetMapping("/lookup/{bin}")
    public ResponseEntity<InstitucionDTO> lookup(@PathVariable String bin) {
        return directorioServicio.descubrirBancoPorBin(bin)
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
        directorioServicio.registrarFallo(bic);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "RF-02: Actualización técnica restringida (Estado y API Key)")
    @PatchMapping("/instituciones/{bic}/operaciones")
    public ResponseEntity<InstitucionDTO> actualizarOperaciones(
            @PathVariable String bic,
            @RequestParam(required = false) String nuevoEstado,
            @RequestParam(required = false) String nuevaUrl) {

        InstitucionDTO actualizada = directorioServicio.actualizarParametrosRestringidos(bic, nuevoEstado, nuevaUrl);
        return ResponseEntity.ok(actualizada);
    }
}
