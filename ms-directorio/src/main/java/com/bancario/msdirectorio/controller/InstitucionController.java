package com.bancario.msdirectorio.controller;

import com.bancario.msdirectorio.model.Institucion;
import com.bancario.msdirectorio.model.ReglaEnrutamiento;
import com.bancario.msdirectorio.service.DirectorioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*") // Permite peticiones de cualquier origen (CORS)
@Tag(name = "Directorio Bancario", description = "Gestión de participantes y reglas de enrutamiento")
public class InstitucionController {

    @Autowired
    private DirectorioService directorioService;

    // ==========================================
    // GESTIÓN DE BANCOS
    // ==========================================

    @Operation(summary = "Registrar un nuevo Banco Participante")
    @PostMapping("/instituciones")
    public ResponseEntity<?> crearInstitucion(@RequestBody Institucion institucion) { // Nota el <?>
        try {
            Institucion nueva = directorioService.registrarInstitucion(institucion);
            return new ResponseEntity<>(nueva, HttpStatus.CREATED);
        } catch (RuntimeException e) {
            e.printStackTrace(); // Esto imprimirá el error en la consola de Docker
            // Esto enviará el texto del error a tu Postman/IntelliJ
            return ResponseEntity.badRequest().body("ERROR: " + e.getMessage());
        }
    }

    @Operation(summary = "Listar todos los bancos")
    @GetMapping("/instituciones")
    public ResponseEntity<List<Institucion>> listarInstituciones() {
        return ResponseEntity.ok(directorioService.listarTodas()); // 200 OK
    }

    @Operation(summary = "Buscar banco por código BIC")
    @GetMapping("/instituciones/{bic}")
    public ResponseEntity<Institucion> obtenerBanco(@PathVariable String bic) {
        return directorioService.buscarPorBic(bic)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build()); // 404 Not Found
    }

    // ==========================================
    // GESTIÓN DE REGLAS Y LOOKUP
    // ==========================================

    @Operation(summary = "Asignar un rango de BIN a un Banco")
    @PostMapping("/reglas")
    public ResponseEntity<ReglaEnrutamiento> crearRegla(@RequestBody ReglaEnrutamiento regla) {
        try {
            ReglaEnrutamiento nueva = directorioService.registrarRegla(regla);
            return new ResponseEntity<>(nueva, HttpStatus.CREATED);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @Operation(summary = "LOOKUP: Descubrir URL destino dado un BIN (Usado por el Switch)")
    @GetMapping("/lookup/{bin}")
    public ResponseEntity<Institucion> buscarDestino(@PathVariable String bin) {
        return directorioService.descubrirBancoPorBin(bin)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "INTERNAL: Reportar fallo de conexión con un banco (Usado por el Switch)")
    @PostMapping("/instituciones/{bic}/reportar-fallo")
    public ResponseEntity<Void> reportarFallo(@PathVariable String bic) {
        // Aquí llamaríamos a la lógica de aumentar contador en el servicio
        // Por ahora lo dejamos preparado para cuando integremos el Switch
        // directorioService.registrarFallo(bic); <--- Esto lo descomentamos luego
        return ResponseEntity.ok().build();
    }

}