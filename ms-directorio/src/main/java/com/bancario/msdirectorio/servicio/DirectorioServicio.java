package com.bancario.msdirectorio.servicio;

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

import com.bancario.msdirectorio.dto.InstitucionDTO;
import com.bancario.msdirectorio.modelo.DatosTecnicos;
import com.bancario.msdirectorio.modelo.Institucion;
import com.bancario.msdirectorio.modelo.InterruptorCircuito;
import com.bancario.msdirectorio.modelo.ReglaEnrutamiento;
import com.bancario.msdirectorio.repositorio.InstitucionRepositorio;
import com.bancario.msdirectorio.mapper.InstitucionMapper;

import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;

@Slf4j
@Service
@RequiredArgsConstructor
public class DirectorioServicio {

    private final InstitucionRepositorio institucionRepositorio;
    private final RedisTemplate<String, Object> redisTemplate;
    private final InstitucionMapper mapper;

    private static final String CACHE_KEY_PREFIX = "lookup:bin:";

    public InstitucionDTO registrarInstitucion(@NonNull InstitucionDTO dto) {

        Institucion institucion = mapper.toEntity(dto);

        if (institucion.getCodigoBic() == null) {
            throw new IllegalArgumentException("El codigoBic no puede ser nulo");
        }

        if (institucionRepositorio.findByCodigoBic(institucion.getCodigoBic()).isPresent()) {
            throw new RuntimeException("El banco con BIC " + institucion.getCodigoBic() + " ya existe.");
        }

        if (institucion.getInterruptorCircuito() == null) {
            institucion.setInterruptorCircuito(new InterruptorCircuito(false, 0, null));
        }

        if (institucion.getReglasEnrutamiento() == null) {
            institucion.setReglasEnrutamiento(new ArrayList<>());
        }

        Institucion saved = institucionRepositorio.save(institucion);
        return mapper.toDTO(saved);
    }

    public List<InstitucionDTO> listarTodas() {
        return mapper.toDTOList(institucionRepositorio.findAll());
    }

    public Optional<InstitucionDTO> buscarPorBic(String bic) {
        if (bic == null)
            return Optional.empty();

        return institucionRepositorio.findByCodigoBic(bic)
                .filter(this::validarDisponibilidad)
                .map(mapper::toDTO);
    }

    public InstitucionDTO aniadirRegla(@NonNull String bic, @NonNull InstitucionDTO.ReglaDTO nuevaReglaDTO) {
        Institucion inst = institucionRepositorio.findByCodigoBic(bic)
                .orElseThrow(() -> new RuntimeException("Banco no encontrado: " + bic));

        if (inst.getReglasEnrutamiento() == null) {
            inst.setReglasEnrutamiento(new ArrayList<>());
        }

        ReglaEnrutamiento nuevaRegla = new ReglaEnrutamiento(nuevaReglaDTO.getPrefijoBin(), nuevaReglaDTO.getAgente());
        inst.getReglasEnrutamiento().add(nuevaRegla);
        redisTemplate.delete(CACHE_KEY_PREFIX + nuevaRegla.getPrefijoBin());

        return mapper.toDTO(institucionRepositorio.save(inst));
    }

    public Optional<InstitucionDTO> descubrirBancoPorBin(String bin) {
        if (bin == null)
            return Optional.empty();
        String cacheKey = CACHE_KEY_PREFIX + bin;

        // Intentamos obtener DTO de cache
        // Si antes guardaba Entidad, esto podría fallar al deserializar si la clase
        // cambió.
        // Asumiremos cache limpio o compatible.
        Object cacheData = redisTemplate.opsForValue().get(cacheKey);

        if (cacheData instanceof InstitucionDTO) {
            return Optional.of((InstitucionDTO) cacheData);
        }

        return institucionRepositorio.findByReglasEnrutamientoPrefijoBin(bin)
                .filter(this::validarDisponibilidad)
                .map(inst -> {
                    InstitucionDTO dto = mapper.toDTO(inst);
                    redisTemplate.opsForValue().set(cacheKey, dto, Duration.ofHours(1));
                    return dto;
                });
    }

    public void registrarFallo(String bic) {
        if (bic == null)
            return;

        institucionRepositorio.findByCodigoBic(bic).ifPresent(inst -> {
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

            institucionRepositorio.save(inst);
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
                institucionRepositorio.save(inst);
                log.info(">>> CIRCUIT BREAKER CERRADO (Auto-recuperación) para banco: {}", inst.getCodigoBic());
                return true;
            }
            return false;
        }

        return false;
    }

    public InstitucionDTO actualizarParametrosRestringidos(String bic, String nuevoEstado, String nuevaUrl) {

        Institucion inst = institucionRepositorio.findByCodigoBic(bic)
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

        return mapper.toDTO(institucionRepositorio.save(inst));
    }
}
