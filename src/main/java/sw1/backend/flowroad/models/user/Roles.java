package sw1.backend.flowroad.models.user;

public enum Roles {
    RECEP, // Recepcionista de la organización
    ADMIN, // Administrador de una Organización (Nissan, Banco)
    WORKER, // Trabajador operativo
    CLIENT, // El que crea los tickets desde afuera
    DESIGNER, // El que diseña los procesos
}