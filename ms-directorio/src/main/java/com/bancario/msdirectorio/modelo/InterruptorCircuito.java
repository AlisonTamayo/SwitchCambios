package com.bancario.msdirectorio.modelo;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InterruptorCircuito {
    private boolean estaAbierto;
    private int fallosConsecutivos;
    private LocalDateTime ultimoFallo;
}
