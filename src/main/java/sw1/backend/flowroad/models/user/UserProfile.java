package sw1.backend.flowroad.models.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfile {
    private String nombre;
    private String apellido;
    private String direccion;
    private String telefono;
    private String avatarUrl;
}