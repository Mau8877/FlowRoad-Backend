package sw1.backend.flowroad.services.document.onlyoffice;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import sw1.backend.flowroad.config.OnlyOfficeProperties;

@Service
@RequiredArgsConstructor
public class OnlyOfficeJwtService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final OnlyOfficeProperties onlyOfficeProperties;
    private final ObjectMapper objectMapper;

    public String sign(Map<String, Object> payload) {
        if (!StringUtils.hasText(onlyOfficeProperties.getJwtSecret())) {
            return null;
        }

        try {
            String header = encodeJson(Map.of("alg", "HS256", "typ", "JWT"));
            String claims = encodeJson(payload);
            String signingInput = header + "." + claims;
            return signingInput + "." + signInput(signingInput);
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo firmar el token de ONLYOFFICE.", ex);
        }
    }

    public Map<String, Object> verify(String token) {
        return verify(token, true);
    }

    public Map<String, Object> verify(String token, boolean requireExpiration) {
        if (!StringUtils.hasText(token)) {
            throw new IllegalArgumentException("El token de descarga es obligatorio.");
        }

        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw new IllegalArgumentException("Token de descarga invalido.");
            }

            String signingInput = parts[0] + "." + parts[1];
            String expectedSignature = signInput(signingInput);
            if (!constantTimeEquals(expectedSignature, parts[2])) {
                throw new IllegalArgumentException("Token de descarga invalido.");
            }

            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            Map<String, Object> payload = objectMapper.readValue(
                    payloadBytes,
                    new TypeReference<LinkedHashMap<String, Object>>() {
                    });

            Object exp = payload.get("exp");
            if (requireExpiration && !(exp instanceof Number)) {
                throw new IllegalArgumentException("Token de descarga sin expiracion.");
            }

            if (exp instanceof Number number && number.longValue() < Instant.now().getEpochSecond()) {
                throw new IllegalArgumentException("Token de descarga expirado.");
            }

            return payload;
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Token de descarga invalido.", ex);
        }
    }

    private String encodeJson(Map<String, Object> payload) throws Exception {
        byte[] bytes = objectMapper.writeValueAsBytes(payload);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String signInput(String signingInput) throws Exception {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(
                onlyOfficeProperties.getJwtSecret().getBytes(StandardCharsets.UTF_8),
                HMAC_ALGORITHM));
        byte[] signature = mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
    }

    private boolean constantTimeEquals(String left, String right) {
        byte[] leftBytes = left.getBytes(StandardCharsets.UTF_8);
        byte[] rightBytes = right.getBytes(StandardCharsets.UTF_8);

        if (leftBytes.length != rightBytes.length) {
            return false;
        }

        int result = 0;
        for (int i = 0; i < leftBytes.length; i++) {
            result |= leftBytes[i] ^ rightBytes[i];
        }
        return result == 0;
    }
}
