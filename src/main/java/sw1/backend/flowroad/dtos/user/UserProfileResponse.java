package sw1.backend.flowroad.dtos.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserProfileResponse {
    private String nombre;
    private String apellido;
    private String direccion;
    private String telefono;
    private String avatarUrl;
}