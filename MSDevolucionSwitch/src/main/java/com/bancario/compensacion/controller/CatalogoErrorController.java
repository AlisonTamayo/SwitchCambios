package com.bancario.compensacion.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import com.bancario.compensacion.dto.CatalogoErrorDTO;
import com.bancario.compensacion.dto.CreateCatalogoErrorRequest;
import com.bancario.compensacion.service.CatalogoErrorService;

@Slf4j
@RestController
@RequestMapping("/api/v1/reference/iso20022/errors")
@Tag(name = "Catálogo ISO 20022", description = "Catálogo de errores/motivos ISO 20022")
public class CatalogoErrorController {

    private final CatalogoErrorService service;

    public CatalogoErrorController(CatalogoErrorService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear un motivo/error ISO 20022")
    public CatalogoErrorDTO create(@Valid @RequestBody CreateCatalogoErrorRequest req) {
        log.info("POST /reference/iso20022/errors codigo={}", req.getCodigo());
        return service.create(req);
    }

    @GetMapping("/{codigo}")
    @Operation(summary = "Consultar un motivo/error por código")
    public CatalogoErrorDTO get(@PathVariable String codigo) {
        return service.getByCodigo(codigo);
    }

    @GetMapping
    @Operation(summary = "Listar motivos/errores")
    public List<CatalogoErrorDTO> list() {
        return service.list();
    }
}
