package com.bancario.msdirectorio.service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull; // Importante para la seguridad de tipos
import org.springframework.stereotype.Service;

import com.bancario.msdirectorio.model.Institucion;
import com.bancario.msdirectorio.model.InterruptorCircuito;
import com.bancario.msdirectorio.model.ReglaEnrutamiento;
import com.bancario.msdirectorio.repository.InstitucionRepository;

@Service
public class DirectorioService {

    @Autowired
    private InstitucionRepository institucionRepository;

    public Institucion registrarInstitucion(@NonNull Institucion institucion) {
        // Validación manual para eliminar el Warning y asegurar integridad
        if (institucion.get_id() == null) {
            throw new IllegalArgumentException("El BIC (_id) no puede ser nulo");
        }

        if (institucionRepository.existsById(institucion.get_id())) {
            throw new RuntimeException("El banco con BIC " + institucion.get_id() + " ya existe.");
        }

        // Inicializaciones seguras
        if (institucion.getInterruptorCircuito() == null) {
            institucion.setInterruptorCircuito(new InterruptorCircuito(false, 0, null));
        }
        
        if (institucion.getReglasEnrutamiento() == null) {
            institucion.setReglasEnrutamiento(new ArrayList<>());
        }

        return institucionRepository.save(institucion);
    }

    public List<Institucion> listarTodas() {
        return institucionRepository.findAll();
    }

    public Optional<Institucion> buscarPorBic(String bic) {
        // Si el BIC es nulo, evitamos la consulta y retornamos vacío
        if (bic == null) return Optional.empty();
        
        return institucionRepository.findById(bic)
                .filter(this::validarDisponibilidad);
    }

    public Institucion aniadirRegla(@NonNull String bic, @NonNull ReglaEnrutamiento nuevaRegla) {
        Institucion inst = institucionRepository.findById(bic)
                .orElseThrow(() -> new RuntimeException("Banco no encontrado: " + bic));
        
        if (inst.getReglasEnrutamiento() == null) {
            inst.setReglasEnrutamiento(new ArrayList<>());
        }
        
        inst.getReglasEnrutamiento().add(nuevaRegla);
        return institucionRepository.save(inst);
    }

    public Optional<Institucion> descubrirBancoPorBin(String bin) {
        if (bin == null) return Optional.empty();
        
        return institucionRepository.findByReglasEnrutamientoPrefijoBin(bin)
                .filter(this::validarDisponibilidad);
    }

    public void registrarFallo(String bic) {
        if (bic == null) return;
        
        institucionRepository.findById(bic).ifPresent(inst -> {
            InterruptorCircuito interruptor = inst.getInterruptorCircuito();
            if (interruptor == null) {
                interruptor = new InterruptorCircuito(false, 0, null);
                inst.setInterruptorCircuito(interruptor);
            }
            
            interruptor.setFallosConsecutivos(interruptor.getFallosConsecutivos() + 1);
            interruptor.setUltimoFallo(LocalDateTime.now());

            if (interruptor.getFallosConsecutivos() >= 5) {
                interruptor.setEstaAbierto(true);
            }
            
            institucionRepository.save(inst);
        });
    }

    private boolean validarDisponibilidad(@NonNull Institucion inst) {
        InterruptorCircuito interruptor = inst.getInterruptorCircuito();
        
        if (interruptor == null || !interruptor.isEstaAbierto()) {
            return true;
        }

        if (interruptor.getUltimoFallo() != null) {
            long segundos = ChronoUnit.SECONDS.between(interruptor.getUltimoFallo(), LocalDateTime.now());
            // Se mantiene el tiempo de castigo definido en el documento (60s)
            return segundos > 60;
        }

        return false;
    }
}