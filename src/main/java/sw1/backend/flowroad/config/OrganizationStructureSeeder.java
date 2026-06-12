package sw1.backend.flowroad.config;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

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
import sw1.backend.flowroad.models.organization.Cargo;
import sw1.backend.flowroad.models.organization.Department;
import sw1.backend.flowroad.models.organization.Organization;
import sw1.backend.flowroad.models.user.Roles;
import sw1.backend.flowroad.models.user.User;
import sw1.backend.flowroad.models.user.UserProfile;
import sw1.backend.flowroad.repository.organization.CargoRepository;
import sw1.backend.flowroad.repository.organization.DepartmentRepository;
import sw1.backend.flowroad.repository.organization.OrganizationRepository;
import sw1.backend.flowroad.repository.user.UserRepository;

@Component
@RequiredArgsConstructor
@Order(2)
@ConditionalOnProperty(prefix = "flowroad.seed.org-structure", name = "enabled", havingValue = "true")
public class OrganizationStructureSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OrganizationStructureSeeder.class);

    private final OrganizationRepository organizationRepository;
    private final DepartmentRepository departmentRepository;
    private final CargoRepository cargoRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${flowroad.seed.organization.code:BCB}")
    private String organizationCode;

    @Value("${flowroad.seed.organization.name:Banco BCB}")
    private String organizationName;

    @Override
    public void run(ApplicationArguments args) {
        seedBankStructure();
    }

    private void seedBankStructure() {
        Optional<Organization> organization = findBankOrganization();

        if (organization.isEmpty()) {
            log.warn(
                    "[SEED][ORG-STRUCTURE] No existe la organizacion {} ({}) para crear la estructura demo.",
                    organizationName,
                    organizationCode);
            return;
        }

        Organization bank = organization.get();
        log.info("[SEED][ORG-STRUCTURE] Iniciando estructura demo para {} ({})", bank.getName(), bank.getCode());

        for (DepartmentSeed departmentSeed : departmentSeeds()) {
            Department department = seedDepartment(bank, departmentSeed);
            List<Cargo> cargos = seedCargos(bank, department, departmentSeed.cargos());
            seedInternalUsers(bank, department, cargos, departmentSeed);
        }

        log.info("[SEED][ORG-STRUCTURE] Estructura demo finalizada para {} ({})", bank.getName(), bank.getCode());
    }

    private Optional<Organization> findBankOrganization() {
        String normalizedCode = organizationCode.toUpperCase(Locale.ROOT);
        return organizationRepository.findByCode(normalizedCode)
                .or(() -> organizationRepository.findByNameAndIsActiveTrue(organizationName));
    }

    private Department seedDepartment(Organization organization, DepartmentSeed seed) {
        Department department = departmentRepository.findByCodeAndOrgId(seed.code(), organization.getId())
                .orElseGet(() -> {
                    Department created = new Department();
                    created.setOrgId(organization.getId());
                    created.setCode(seed.code());
                    created.setCreatedAt(LocalDateTime.now());
                    created.setCargoIds(new ArrayList<>());
                    return created;
                });

        boolean isNew = department.getId() == null;
        department.setOrgId(organization.getId());
        department.setName(seed.name());
        department.setCode(seed.code());
        department.setSlaHours(seed.slaHours());
        department.setIsActive(true);
        if (department.getCargoIds() == null) {
            department.setCargoIds(new ArrayList<>());
        }

        Department saved = departmentRepository.save(department);

        if (isNew) {
            log.info("[SEED][ORG-STRUCTURE] Departamento creado: {} ({})", saved.getName(), saved.getCode());
        } else {
            log.info("[SEED][ORG-STRUCTURE] Departamento existente actualizado: {} ({})", saved.getName(), saved.getCode());
        }

        return saved;
    }

    private List<Cargo> seedCargos(Organization organization, Department department, List<CargoSeed> cargoSeeds) {
        List<Cargo> cargos = new ArrayList<>();

        for (CargoSeed seed : cargoSeeds) {
            Cargo cargo = seedCargo(organization, department, seed);
            cargos.add(cargo);
        }

        return cargos;
    }

    private Cargo seedCargo(Organization organization, Department department, CargoSeed seed) {
        Optional<Cargo> existingDepartmentCargo = cargoRepository.findAllById(department.getCargoIds())
                .stream()
                .filter(cargo -> sameName(cargo.getName(), seed.name()))
                .findFirst();

        Cargo cargo = existingDepartmentCargo
                .or(() -> cargoRepository.findByOrgId(organization.getId())
                        .stream()
                        .filter(candidate -> sameName(candidate.getName(), seed.name()))
                        .findFirst())
                .orElseGet(() -> Cargo.builder()
                        .orgId(organization.getId())
                        .name(seed.name())
                        .build());

        boolean isNew = cargo.getId() == null;
        cargo.setOrgId(organization.getId());
        cargo.setName(seed.name());
        cargo.setLevel(seed.level());
        cargo.setIsActive(true);
        Cargo saved = cargoRepository.save(cargo);

        if (!department.getCargoIds().contains(saved.getId())) {
            department.getCargoIds().add(saved.getId());
            departmentRepository.save(department);
        }

        if (isNew) {
            log.info("[SEED][ORG-STRUCTURE] Cargo creado: {} -> {}", saved.getName(), department.getName());
        } else {
            log.info("[SEED][ORG-STRUCTURE] Cargo existente actualizado: {} -> {}", saved.getName(), department.getName());
        }

        return saved;
    }

    private void seedInternalUsers(
            Organization organization,
            Department department,
            List<Cargo> cargos,
            DepartmentSeed departmentSeed) {
        List<UserSeed> users = departmentSeed.users();

        for (int index = 0; index < users.size(); index++) {
            Cargo cargo = cargos.get(Math.min(index, cargos.size() - 1));
            seedInternalUser(organization, department, cargo, users.get(index), departmentSeed.role());
        }
    }

    private void seedInternalUser(
            Organization organization,
            Department department,
            Cargo cargo,
            UserSeed seed,
            Roles role) {
        User user = userRepository.findByEmail(seed.email())
                .orElseGet(() -> User.builder()
                        .email(seed.email())
                        .createdAt(LocalDateTime.now())
                        .build());

        boolean isNew = user.getId() == null;
        user.setEmail(seed.email());
        user.setRole(role);
        user.setOrgId(organization.getId());
        user.setDepartmentId(department.getId());
        user.setCargoId(cargo.getId());
        user.setWorkload(0);
        user.setIsActive(true);
        user.setProfile(UserProfile.builder()
                .nombre(seed.nombre())
                .apellido(seed.apellido())
                .telefono(seed.telefono())
                .direccion(department.getName() + " - Banco BCB")
                .build());

        if (user.getPassword() == null || !passwordEncoder.matches(seed.email(), user.getPassword())) {
            user.setPassword(passwordEncoder.encode(seed.email()));
        }

        userRepository.save(user);

        if (isNew) {
            log.info("[SEED][ORG-STRUCTURE] Usuario interno creado: {} ({})", seed.email(), role);
        } else {
            log.info("[SEED][ORG-STRUCTURE] Usuario interno actualizado: {} ({})", seed.email(), role);
        }
    }

    private List<DepartmentSeed> departmentSeeds() {
        return List.of(
                new DepartmentSeed(
                        "Atencion al Cliente",
                        "ATC",
                        24,
                        Roles.RECEP,
                        List.of(
                                new CargoSeed("Ejecutivo de Atencion", 1),
                                new CargoSeed("Auxiliar de Atencion", 2)),
                        List.of(
                                new UserSeed("Atencion", "Cliente 1", "atencioncliente1@gmail.com", "70100001"),
                                new UserSeed("Atencion", "Cliente 2", "atencioncliente2@gmail.com", "70100002"))),
                new DepartmentSeed(
                        "Creditos",
                        "CRE",
                        48,
                        Roles.WORKER,
                        List.of(
                                new CargoSeed("Analista de Credito", 1),
                                new CargoSeed("Oficial de Credito", 2)),
                        List.of(
                                new UserSeed("Creditos", "Uno", "creditos1@gmail.com", "70100003"),
                                new UserSeed("Creditos", "Dos", "creditos2@gmail.com", "70100004"))),
                new DepartmentSeed(
                        "Riesgos",
                        "RIE",
                        48,
                        Roles.WORKER,
                        List.of(
                                new CargoSeed("Analista de Riesgo", 1),
                                new CargoSeed("Supervisor de Riesgo", 2)),
                        List.of(
                                new UserSeed("Riesgos", "Uno", "riesgos1@gmail.com", "70100005"),
                                new UserSeed("Riesgos", "Dos", "riesgos2@gmail.com", "70100006"))),
                new DepartmentSeed(
                        "Legal",
                        "LEG",
                        48,
                        Roles.WORKER,
                        List.of(
                                new CargoSeed("Asesor Legal", 1),
                                new CargoSeed("Revisor Legal", 2)),
                        List.of(
                                new UserSeed("Legal", "Uno", "legal1@gmail.com", "70100007"),
                                new UserSeed("Legal", "Dos", "legal2@gmail.com", "70100008"))),
                new DepartmentSeed(
                        "Operaciones",
                        "OPE",
                        36,
                        Roles.WORKER,
                        List.of(
                                new CargoSeed("Analista Operativo", 1),
                                new CargoSeed("Coordinador Operativo", 2)),
                        List.of(
                                new UserSeed("Operaciones", "Uno", "operaciones1@gmail.com", "70100009"),
                                new UserSeed("Operaciones", "Dos", "operaciones2@gmail.com", "70100010"))),
                new DepartmentSeed(
                        "Procesos",
                        "PRO",
                        72,
                        Roles.DESIGNER,
                        List.of(
                                new CargoSeed("Diseniador de Procesos", 1),
                                new CargoSeed("Analista de Procesos", 2)),
                        List.of(
                                new UserSeed("Procesos", "Uno", "procesos1@gmail.com", "70100011"),
                                new UserSeed("Procesos", "Dos", "procesos2@gmail.com", "70100012"))));
    }

    private boolean sameName(String current, String expected) {
        return current != null && current.equalsIgnoreCase(expected);
    }

    private record DepartmentSeed(
            String name,
            String code,
            Integer slaHours,
            Roles role,
            List<CargoSeed> cargos,
            List<UserSeed> users) {
    }

    private record CargoSeed(String name, Integer level) {
    }

    private record UserSeed(String nombre, String apellido, String email, String telefono) {
    }
}
