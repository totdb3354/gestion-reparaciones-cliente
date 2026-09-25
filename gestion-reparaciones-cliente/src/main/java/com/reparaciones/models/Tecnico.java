package com.reparaciones.models;

/**
 * Representa a un técnico de reparaciones.
 * <p>Un técnico puede estar activo o inactivo (campo {@code ACTIVO} en BD).
 * Los inactivos no aparecen en el gráfico de estadísticas pero siguen
 * contando en el total acumulado.</p>
 */
public class Tecnico {

    /** Clave primaria (ID_TEC en BD). */
    private int idTec;

    /** Nombre visible del técnico. */
    private String nombre;

    /** {@code true} si el técnico está activo. */
    private boolean activo;

    /** {@code true} si cuenta en la vista de estadísticas (exclusión ronda 2, spec 2026-09-02).
     *  Un JSON sin el campo (servidor anterior a 0.16.2) cuenta como incluido. */
    private Boolean esEstadistica;

    /** {@code true} si entra en la glass automática del modal de asignación (spec 2026-09-05-glass-prediccion).
     *  Un JSON sin el campo (servidor anterior a la 0.16.2) cuenta como NO habilitado. */
    private Boolean esGlass;

    /**
     * @param idTec  clave primaria del técnico
     * @param nombre nombre visible
     * @param activo {@code true} si está activo
     */
    public Tecnico(int idTec, String nombre, boolean activo) {
        this(idTec, nombre, activo, true);
    }

    /**
     * @param idTec         clave primaria del técnico
     * @param nombre        nombre visible
     * @param activo        {@code true} si está activo
     * @param esEstadistica {@code true} si cuenta en la vista de estadísticas
     */
    public Tecnico(int idTec, String nombre, boolean activo, boolean esEstadistica) {
        this(idTec, nombre, activo, esEstadistica, false);
    }

    /**
     * @param esGlass {@code true} si está habilitado para la glass automática del modal de asignación
     */
    public Tecnico(int idTec, String nombre, boolean activo, boolean esEstadistica, boolean esGlass) {
        this.idTec         = idTec;
        this.nombre        = nombre;
        this.activo        = activo;
        this.esEstadistica = esEstadistica;
        this.esGlass       = esGlass;
    }

    /** @return clave primaria del técnico */
    public int getIdTec() { return idTec; }
    public void setIdTec(int idTec) { this.idTec = idTec; }

    /** @return nombre visible del técnico */
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    /** @return {@code true} si el técnico está activo */
    public boolean isActivo() { return activo; }

    /** @return {@code true} si cuenta en la vista de estadísticas; un JSON sin el campo
     *  (servidor anterior a la 0.16.2) cuenta como incluido. */
    public boolean isEsEstadistica() { return esEstadistica == null || esEstadistica; }

    /** @return {@code true} si está habilitado para la glass automática; sin el campo (servidor
     *  anterior a la 0.16.2) cuenta como no habilitado. */
    public boolean isEsGlass() { return esGlass != null && esGlass; }

    /** Devuelve el nombre para uso en ComboBox y MenuButton. */
    @Override
    public String toString() { return nombre; }
}
