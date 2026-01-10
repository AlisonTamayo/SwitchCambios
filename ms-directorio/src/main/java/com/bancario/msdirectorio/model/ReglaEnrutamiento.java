package com.bancario.msdirectorio.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Entity
@Table(name = "ReglaEnrutamiento")
@Getter
@Setter
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"}) 
public class ReglaEnrutamiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id; // Integer para Serial (Requerimiento 9)

    @Column(name = "prefijoBin", length = 6, nullable = false)
    private String prefijoBin;

    // Relación Hijos a Padres (Requerimiento 1)
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "codigoBic", nullable = false)
    private Institucion institucion;

    // CONSTRUCTOR VACÍO (Requerimiento 7)
    public ReglaEnrutamiento() {
    }

    // CONSTRUCTOR SOLO CLAVE PRIMARIA (Requerimiento 8)
    public ReglaEnrutamiento(Integer id) {
        this.id = id;
    }

    // EQUALS & HASHCODE SOLO PK (Requerimiento 11)
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReglaEnrutamiento that = (ReglaEnrutamiento) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    // TOSTRING COMPLETO (Requerimiento 12)
    @Override
    public String toString() {
        return "ReglaEnrutamiento{" +
                "id=" + id +
                ", prefijoBin='" + prefijoBin + '\'' +
                ", institucion=" + (institucion != null ? institucion.getCodigoBic() : "null") +
                '}';
    }
}