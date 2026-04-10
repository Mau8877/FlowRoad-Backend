package sw1.backend.flowroad.dtos;

public record LoginRequest(
        String email,
        String password) {
}