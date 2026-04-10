package sw1.backend.flowroad.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Document(collection = "usuarios")
public class User implements UserDetails {

    @Id
    private String id;
    private String email;
    private String password;
    private String role;

    // El perfil le pertenece única y exclusivamente a este usuario
    private UserProfile perfil;

    // Conexión con la empresa
    private String empresaId;
    private String departamento;
    private String cargo;

    public User() {
    }

    public User(String email, String password, String role, UserProfile perfil, String empresaId, String departamento,
            String cargo) {
        this.email = email;
        this.password = password;
        this.role = role;
        this.perfil = perfil;
        this.empresaId = empresaId;
        this.departamento = departamento;
        this.cargo = cargo;
    }

    // --- Getters y Setters User ---
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    // --- Getters y Setters de Sub-documentos y Laborales ---
    public UserProfile getPerfil() {
        return perfil;
    }

    public void setPerfil(UserProfile perfil) {
        this.perfil = perfil;
    }

    public String getEmpresaId() {
        return empresaId;
    }

    public void setEmpresaId(String empresaId) {
        this.empresaId = empresaId;
    }

    public String getDepartamento() {
        return departamento;
    }

    public void setDepartamento(String departamento) {
        this.departamento = departamento;
    }

    public String getCargo() {
        return cargo;
    }

    public void setCargo(String cargo) {
        this.cargo = cargo;
    }

    // --- Métodos obligatorios de UserDetails (Spring Security) ---
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role));
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}