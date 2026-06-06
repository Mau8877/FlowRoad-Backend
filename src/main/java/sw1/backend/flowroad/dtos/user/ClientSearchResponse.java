package sw1.backend.flowroad.dtos.user;

public record ClientSearchResponse(
        String id,
        String fullName,
        String email,
        String avatarUrl) {
}
