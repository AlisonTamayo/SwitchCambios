package com.bancario.msdirectorio.model;

import lombok.*;
import java.time.LocalDateTime;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class InterruptorCircuito {
    private boolean estaAbierto;
    private int fallosConsecutivos;
    private LocalDateTime ultimoFallo;
}