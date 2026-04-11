package sw1.backend.flowroad.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Excepción personalizada para recursos no encontrados en MongoDB.
 * El uso de @ResponseStatus(HttpStatus.NOT_FOUND) asegura que
 * la API responda con un código 404 automáticamente.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}