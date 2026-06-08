package sw1.backend.flowroad.config;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import sw1.backend.flowroad.models.organization.Organization;
import sw1.backend.flowroad.models.user.Roles;
import sw1.backend.flowroad.models.user.User;
import sw1.backend.flowroad.models.user.UserProfile;
import sw1.backend.flowroad.repository.organization.OrganizationRepository;
import sw1.backend.flowroad.repository.user.UserRepository;

@Component
@RequiredArgsConstructor
@Order(1)
@ConditionalOnProperty(prefix = "flowroad.seed", name = "enabled", havingValue = "true")
public class InitialDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InitialDataSeeder.class);

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${flowroad.seed.superuser.email}")
    private String superuserEmail;

    @Value("${flowroad.seed.superuser.password}")
    private String superuserPassword;

    @Value("${flowroad.seed.organization.name}")
    private String organizationName;

    @Value("${flowroad.seed.organization.code}")
    private String organizationCode;

    @Value("${flowroad.seed.organization-admin.email}")
    private String organizationAdminEmail;

    @Value("${flowroad.seed.organization-admin.password}")
    private String organizationAdminPassword;

    @Value("${flowroad.seed.clients.enabled:false}")
    private boolean clientsEnabled;

    @Override
    public void run(ApplicationArguments args) {
        log.info("[SEED] Iniciando seed inicial de FlowRoad.");

        seedSuperUser();
        Organization organization = seedOrganization();
        seedOrganizationAdmin(organization);

        if (clientsEnabled) {
            seedClients();
        } else {
            log.info("[SEED] Seed de clientes deshabilitado.");
        }

        log.info("[SEED] Seed inicial de FlowRoad finalizado.");
    }

    private void seedSuperUser() {
        UserProfile profile = UserProfile.builder()
                .nombre("Super")
                .apellido("User")
                .telefono("70000000")
                .direccion("FlowRoad Global")
                .build();

        upsertUser(
                superuserEmail,
                superuserPassword,
                Roles.ADMIN,
                null,
                profile,
                "superuser global");
    }

    private Organization seedOrganization() {
        String normalizedCode = organizationCode.toUpperCase();
        Organization organization = organizationRepository.findByCode(normalizedCode)
                .orElseGet(() -> organizationRepository.findByNameAndIsActiveTrue(organizationName).orElse(null));

        if (organization == null) {
            organization = new Organization();
            organization.setName(organizationName);
            organization.setCode(normalizedCode);
            organization.setIsActive(true);
            organization.setCreatedAt(LocalDateTime.now());
            Organization saved = organizationRepository.save(organization);
            log.info("[SEED] Organizacion creada: {} ({})", saved.getName(), saved.getCode());
            return saved;
        }

        boolean changed = false;
        if (!organizationName.equals(organization.getName())) {
            organization.setName(organizationName);
            changed = true;
        }
        if (!normalizedCode.equals(organization.getCode())) {
            organization.setCode(normalizedCode);
            changed = true;
        }
        if (!Boolean.TRUE.equals(organization.getIsActive())) {
            organization.setIsActive(true);
            changed = true;
        }

        if (changed) {
            organization = organizationRepository.save(organization);
            log.info("[SEED] Organizacion existente actualizada: {} ({})", organization.getName(), organization.getCode());
        } else {
            log.info("[SEED] Organizacion ya existente: {} ({})", organization.getName(), organization.getCode());
        }

        return organization;
    }

    private void seedOrganizationAdmin(Organization organization) {
        UserProfile profile = UserProfile.builder()
                .nombre("Admin")
                .apellido("BCB")
                .telefono("70000001")
                .direccion("Banco BCB")
                .build();

        upsertUser(
                organizationAdminEmail,
                organizationAdminPassword,
                Roles.ADMIN,
                organization.getId(),
                profile,
                "administrador de organizacion");
    }

    private void seedClients() {
        List<SeedClient> clients = List.of(
                new SeedClient("Ana", "Rodriguez", "ana.rodriguez.bcb@gmail.com", "70000002"),
                new SeedClient("Carlos", "Mendez", "carlos.mendez.bcb@gmail.com", "70000003"),
                new SeedClient("Mariana", "Vargas", "mariana.vargas.bcb@gmail.com", "70000004"),
                new SeedClient("Jorge", "Salvatierra", "jorge.salvatierra.bcb@gmail.com", "70000005"),
                new SeedClient("Lucia", "Fernandez", "lucia.fernandez.bcb@gmail.com", "70000006"),
                new SeedClient("Diego", "Molina", "diego.molina.bcb@gmail.com", "70000007"),
                new SeedClient("Valeria", "Rojas", "valeria.rojas.bcb@gmail.com", "70000008"));

        for (SeedClient client : clients) {
            UserProfile profile = UserProfile.builder()
                    .nombre(client.nombre())
                    .apellido(client.apellido())
                    .telefono(client.telefono())
                    .direccion("Cliente Banco BCB")
                    .build();

            upsertUser(
                    client.email(),
                    client.email(),
                    Roles.CLIENT,
                    null,
                    profile,
                    "cliente");
        }
    }

    private void upsertUser(
            String email,
            String rawPassword,
            Roles role,
            String orgId,
            UserProfile profile,
            String seedLabel) {
        User user = userRepository.findByEmail(email)
                .orElseGet(() -> User.builder()
                        .email(email)
                        .createdAt(LocalDateTime.now())
                        .build());

        boolean isNew = user.getId() == null;
        user.setEmail(email);
        user.setRole(role);
        user.setOrgId(orgId);
        user.setDepartmentId(null);
        user.setCargoId(null);
        user.setWorkload(0);
        user.setIsActive(true);
        user.setProfile(profile);

        if (user.getPassword() == null || !passwordEncoder.matches(rawPassword, user.getPassword())) {
            user.setPassword(passwordEncoder.encode(rawPassword));
        }

        userRepository.save(user);

        if (isNew) {
            log.info("[SEED] Usuario creado: {} ({})", email, seedLabel);
        } else {
            log.info("[SEED] Usuario ya existente actualizado: {} ({})", email, seedLabel);
        }
    }

    private record SeedClient(String nombre, String apellido, String email, String telefono) {
    }
}
