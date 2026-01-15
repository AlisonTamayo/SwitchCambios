package com.bancario.compensacion.service;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import org.springframework.stereotype.Service;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;

@Service
public class SeguridadService {

    private RSAPrivateKey privateKey;

    public SeguridadService() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            KeyPair kp = kpg.generateKeyPair();
            this.privateKey = (RSAPrivateKey) kp.getPrivate();
        } catch (Exception e) {
            throw new RuntimeException("Error fatal iniciando criptografía", e);
        }
    }

    public String firmarDocumento(String contenidoXml) {
        try {
            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                    .keyID("KEY-G4-SWITCH")
                    .contentType("application/xml")
                    .build();

            Payload payload = new Payload(contenidoXml);

            JWSObject jwsObject = new JWSObject(header, payload);

            JWSSigner signer = new RSASSASigner(privateKey);
            jwsObject.sign(signer);

            return jwsObject.serialize();
        } catch (Exception e) {
            throw new RuntimeException("Error firmando XML", e);
        }
    }
}