package sw1.backend.flowroad.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // 1. RECURSO NO ENCONTRADO (Fundamental para Organizations, Departments y
    // Cargos)
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleResourceNotFound(ResourceNotFoundException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", "Recurso no encontrado");
        error.put("message", ex.getMessage());
        return new ResponseEntity<>(error, HttpStatus.NOT_FOUND); // Devuelve 404
    }

    // 2. ERRORES DE LÓGICA DE AUTENTICACIÓN / NEGOCIO
    @ExceptionHandler(AuthException.class)
    public ResponseEntity<Map<String, String>> handleAuthException(AuthException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", "Error de validación");
        error.put("message", ex.getMessage());
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST); // Devuelve 400
    }

    // 3. CREDENCIALES INCORRECTAS (Spring Security)
    @ExceptionHandler(org.springframework.security.authentication.BadCredentialsException.class)
    public ResponseEntity<Map<String, String>> handleBadCredentials(
            org.springframework.security.authentication.BadCredentialsException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", "Acceso denegado");
        error.put("message", "Correo o contraseña incorrectos");
        return new ResponseEntity<>(error, HttpStatus.UNAUTHORIZED); // Devuelve 401
    }

    // 4. VALIDACIÓN DE DTOs (Atrapa los @NotBlank, @Size, @NotNull de tus Requests)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));
        return new ResponseEntity<>(errors, HttpStatus.BAD_REQUEST); // Devuelve 400 con los campos fallidos
    }

    // 5. ERROR GENÉRICO (Cualquier otro fallo inesperado)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", "Internal Server Error");
        error.put("message", "Ocurrió un error inesperado en el servidor");
        // Para desarrollo, podrías imprimir el stacktrace: ex.printStackTrace();
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}