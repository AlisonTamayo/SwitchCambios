package com.bancario.msdirectorio.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import com.bancario.msdirectorio.model.DatosTecnicos;
import com.bancario.msdirectorio.model.Institucion;
import com.bancario.msdirectorio.model.InterruptorCircuito;
import com.bancario.msdirectorio.model.ReglaEnrutamiento;
import com.bancario.msdirectorio.repository.InstitucionRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class DirectorioService {

    private final InstitucionRepository institucionRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    public DirectorioService(InstitucionRepository institucionRepository,
            RedisTemplate<String, Object> redisTemplate) {
        this.institucionRepository = institucionRepository;
        this.redisTemplate = redisTemplate;
    }

    private static final String CACHE_KEY_PREFIX = "lookup:bin:";

    public Institucion registrarInstitucion(@NonNull Institucion institucion) {

        if (institucion.getCodigoBic() == null) {
            throw new IllegalArgumentException("El codigoBic no puede ser nulo");
        }

        if (institucionRepository.findByCodigoBic(institucion.getCodigoBic()).isPresent()) {
            throw new RuntimeException("El banco con BIC " + institucion.getCodigoBic() + " ya existe.");
        }

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
        if (bic == null)
            return Optional.empty();

        return institucionRepository.findByCodigoBic(bic)
                .filter(this::validarDisponibilidad);
    }

    public Institucion aniadirRegla(@NonNull String bic, @NonNull ReglaEnrutamiento nuevaRegla) {
        Institucion inst = institucionRepository.findByCodigoBic(bic)
                .orElseThrow(() -> new RuntimeException("Banco no encontrado: " + bic));

        if (inst.getReglasEnrutamiento() == null) {
            inst.setReglasEnrutamiento(new ArrayList<>());
        }

        inst.getReglasEnrutamiento().add(nuevaRegla);
        redisTemplate.delete(CACHE_KEY_PREFIX + nuevaRegla.getPrefijoBin());

        return institucionRepository.save(inst);
    }

    public Optional<Institucion> descubrirBancoPorBin(String bin) {
        if (bin == null)
            return Optional.empty();
        String cacheKey = CACHE_KEY_PREFIX + bin;

        Institucion cacheData = (Institucion) redisTemplate.opsForValue().get(cacheKey);
        if (cacheData != null) {
            return Optional.of(cacheData).filter(this::validarDisponibilidad);
        }

        return institucionRepository.findByReglasEnrutamientoPrefijoBin(bin)
                .filter(this::validarDisponibilidad)
                .map(inst -> {

                    redisTemplate.opsForValue().set(cacheKey, inst, Duration.ofHours(1));
                    return inst;
                });
    }

    public void registrarFallo(String bic) {
        if (bic == null)
            return;

        institucionRepository.findByCodigoBic(bic).ifPresent(inst -> {
            InterruptorCircuito interruptor = inst.getInterruptorCircuito();
            if (interruptor == null) {
                interruptor = new InterruptorCircuito(false, 0, null);
                inst.setInterruptorCircuito(interruptor);
            }

            interruptor.setFallosConsecutivos(interruptor.getFallosConsecutivos() + 1);
            interruptor.setUltimoFallo(LocalDateTime.now(ZoneOffset.UTC));

            if (interruptor.getFallosConsecutivos() >= 5) {
                interruptor.setEstaAbierto(true);
                log.error(">>> CIRCUIT BREAKER ACTIVADO para banco: {}", bic);

                invalidarCacheDelBanco(inst);
            }

            institucionRepository.save(inst);
        });
    }

    private void invalidarCacheDelBanco(Institucion inst) {
        if (inst.getReglasEnrutamiento() != null) {
            inst.getReglasEnrutamiento().forEach(r -> redisTemplate.delete(CACHE_KEY_PREFIX + r.getPrefijoBin()));
        }
    }

    private boolean validarDisponibilidad(@NonNull Institucion inst) {
        InterruptorCircuito interruptor = inst.getInterruptorCircuito();

        if (interruptor == null || !interruptor.isEstaAbierto()) {
            return true;
        }

        if (interruptor.getUltimoFallo() != null) {
            long segundos = ChronoUnit.SECONDS.between(interruptor.getUltimoFallo(), LocalDateTime.now(ZoneOffset.UTC));

            if (segundos > 30) {
                interruptor.setEstaAbierto(false);
                interruptor.setFallosConsecutivos(0);
                institucionRepository.save(inst);
                log.info(">>> CIRCUIT BREAKER CERRADO (Auto-recuperación) para banco: {}", inst.getCodigoBic());
                return true;
            }
            return false;
        }

        return false;
    }

    public Institucion actualizarParametrosRestringidos(String bic, String nuevoEstado, String nuevaUrl) {

        Institucion inst = institucionRepository.findByCodigoBic(bic)
                .orElseThrow(() -> new RuntimeException("Institución no encontrada"));

        if (nuevoEstado != null) {
            try {

                Institucion.Estado estadoEnum = Institucion.Estado.valueOf(nuevoEstado.toUpperCase());
                inst.setEstadoOperativo(estadoEnum.name());
            } catch (IllegalArgumentException e) {
                throw new RuntimeException("Estado inválido. Use: ONLINE, OFFLINE, MANT, SUSPENDIDO o SOLO_RECIBIR");
            }
        }

        if (nuevaUrl != null && !nuevaUrl.isBlank()) {
            inst.setUrlDestino(nuevaUrl);
        }

        invalidarCacheDelBanco(inst);

        return institucionRepository.save(inst);
    }
}