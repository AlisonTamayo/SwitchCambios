package com.bancario.msdirectorio.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReglaEnrutamiento {
    private String prefijoBin;
    private String agente; 
}