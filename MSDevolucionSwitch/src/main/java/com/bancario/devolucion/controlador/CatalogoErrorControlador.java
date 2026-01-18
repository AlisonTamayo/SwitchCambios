package com.bancario.devolucion.controlador;

import com.bancario.devolucion.dto.CatalogoErrorDTO;
import com.bancario.devolucion.servicio.CatalogoErrorServicio;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/errores")
@RequiredArgsConstructor
@Tag(name = "Catálogo de Errores", description = "Códigos estandarizados ISO para motivos de devolución")
public class CatalogoErrorControlador {

    private final CatalogoErrorServicio service;

    @GetMapping
    @Operation(summary = "Listar catálogo completo")
    public ResponseEntity<List<CatalogoErrorDTO>> listar() {
        return ResponseEntity.ok(service.findAll());
    }

    @PostMapping
    @Operation(summary = "Agregar nuevo código de error")
    public ResponseEntity<CatalogoErrorDTO> crear(@RequestBody CatalogoErrorDTO dto) {
        return new ResponseEntity<>(service.create(dto), HttpStatus.CREATED);
    }
}
