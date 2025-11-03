package br.gov.pb.der.netnotify.controller;

import java.util.Base64;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/debug")
public class DebugController {
    private static final Logger logger = LoggerFactory.getLogger(DebugController.class);

    @PostMapping("/decode-jwt")
    public Map<String, Object> decodeJwt(@RequestHeader("Authorization") String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return Map.of("error", "No Bearer token found");
        }

        String token = authHeader.substring(7);
        String[] parts = token.split("\\.");

        if (parts.length != 3) {
            return Map.of("error", "Invalid JWT format");
        }

        try {
            // Decodificar header
            String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]));
            // Decodificar payload
            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]));

            ObjectMapper mapper = new ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> header = mapper.readValue(headerJson, Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = mapper.readValue(payloadJson, Map.class);

            logger.info("🔍 [DEBUG] JWT Header: {}", header);
            logger.info("🔍 [DEBUG] JWT Payload: {}", payload);

            return Map.of(
                "header", header,
                "payload", payload,
                "signature_present", true
            );
        } catch (Exception e) {
            logger.error("❌ Erro ao decodificar JWT: {}", e.getMessage(), e);
            return Map.of("error", e.getMessage());
        }
    }
}
