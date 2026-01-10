package com.bancario.compensacion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateCatalogoErrorRequest {

    @NotBlank(message = "codigo is required")
    @Size(max = 10, message = "codigo max length is 10")
    private String codigo;

    @NotBlank(message = "descripcion is required")
    private String descripcion;

    public CreateCatalogoErrorRequest() { }
}