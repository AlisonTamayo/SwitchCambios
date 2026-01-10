package com.bancario.compensacion.exception;

import java.time.OffsetDateTime;

@lombok.Getter
@lombok.Setter
@lombok.AllArgsConstructor
public class ApiErrorResponse {
    private String code;        
    private String message;    
    private String path;       
    private OffsetDateTime timestamp;
}