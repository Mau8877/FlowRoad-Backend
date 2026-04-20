package sw1.backend.flowroad.services.diagram;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import sw1.backend.flowroad.models.diagram.Diagram;
import sw1.backend.flowroad.repository.diagram.DiagramRepository;

@Service
@RequiredArgsConstructor
public class DiagramService {

    private final DiagramRepository diagramRepository;

    /**
     * 1. CREAR: Se llama cuando el usuario hace clic en "Nuevo Diagrama"
     * Crea un lienzo en blanco listo para que alguien abra una DesignSession.
     */
    @Transactional
    public Diagram createDiagram(String orgId, String name, String description, String userId) {
        // Regla de Negocio: No permitir nombres duplicados en la misma empresa
        if (diagramRepository.existsByOrgIdAndName(orgId, name)) {
            throw new RuntimeException("Ya existe un diagrama con el nombre '" + name + "' en tu organización.");
        }

        Diagram newDiagram = Diagram.builder()
                .orgId(orgId)
                .name(name)
                .description(description)
                .version(1) // Empezamos en la versión 1
                .isActive(true)
                .cells(new ArrayList<>()) // Lienzo inicial vacío
                .createdAt(LocalDateTime.now())
                .createdBy(userId)
                .updatedAt(LocalDateTime.now())
                .build();

        return diagramRepository.save(newDiagram);
    }

    /**
     * 2. LECTURA GENERAL: Para la tabla del administrador
     */
    public List<Diagram> getAllDiagrams(String orgId) {
        return diagramRepository.findAllByOrgId(orgId);
    }

    /**
     * 3. LECTURA FILTRADA: Para los trabajadores (solo ven los activos)
     */
    public List<Diagram> getActiveDiagrams(String orgId) {
        return diagramRepository.findAllByOrgIdAndIsActiveTrue(orgId);
    }

    /**
     * 4. LECTURA INDIVIDUAL: Obtiene un diagrama asegurando la seguridad de la
     * Organización
     */
    public Diagram getDiagramById(String id, String orgId) {
        return diagramRepository.findByIdAndOrgId(id, orgId)
                .orElseThrow(() -> new RuntimeException("Diagrama no encontrado o no pertenece a tu organización."));
    }

    /**
     * 5. ACTUALIZAR METADATOS: Para cambiar el nombre o descripción desde las
     * opciones.
     * Nota: Aquí NO actualizamos el contenido (cells), eso lo hace el
     * DesignSessionService.
     */
    @Transactional
    public Diagram updateMetadata(String id, String orgId, String newName, String newDescription) {
        Diagram diagram = getDiagramById(id, orgId);

        // Si intenta cambiar el nombre, validamos que el nuevo no esté tomado
        if (!diagram.getName().equals(newName) && diagramRepository.existsByOrgIdAndName(orgId, newName)) {
            throw new RuntimeException("El nombre '" + newName + "' ya está en uso por otro diagrama.");
        }

        diagram.setName(newName);
        diagram.setDescription(newDescription);
        diagram.setUpdatedAt(LocalDateTime.now());

        // Opcional: diagram.setVersion(diagram.getVersion() + 1);

        return diagramRepository.save(diagram);
    }

    /**
     * 6. BORRADO LÓGICO (Soft Delete): En sistemas Enterprise casi nunca se hace un
     * DELETE real.
     */
    @Transactional
    public Diagram toggleActiveStatus(String id, String orgId) {
        Diagram diagram = getDiagramById(id, orgId);

        // Invertimos el estado (Si era true, pasa a false. Si era false, a true)
        diagram.setIsActive(!diagram.getIsActive());
        diagram.setUpdatedAt(LocalDateTime.now());

        return diagramRepository.save(diagram);
    }
}