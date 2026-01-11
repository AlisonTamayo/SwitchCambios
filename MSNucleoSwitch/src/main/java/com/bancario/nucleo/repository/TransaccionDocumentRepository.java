package com.bancario.nucleo.repository;

import com.bancario.nucleo.model.TransaccionDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransaccionDocumentRepository extends MongoRepository<TransaccionDocument, UUID> {
    Optional<TransaccionDocument> findByHashIdempotencia(String hashIdempotencia);
}
