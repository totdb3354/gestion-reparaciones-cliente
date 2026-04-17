package com.reparaciones.models;

/**
 * Proveedor de componentes.
 * <p>Los proveedores inactivos ({@code activo = false}) no aparecen
 * en los desplegables de nuevos pedidos, pero sus pedidos históricos
 * se conservan y siguen siendo visibles.</p>
 */
public class Proveedor {

    /** Clave primaria (ID_PROV en BD). */
    private int idProv;

    /** Nombre comercial del proveedor. */
    private String nombre;

    /** {@code true} si el proveedor está disponible para nuevos pedidos. */
    private boolean activo;

    /**
     * @param idProv  clave primaria del proveedor
     * @param nombre  nombre comercial
     * @param activo  {@code true} si está disponible para nuevos pedidos
     */
    public Proveedor(int idProv, String nombre, boolean activo) {
        this.idProv  = idProv;
        this.nombre  = nombre;
        this.activo  = activo;
    }

    /** @return clave primaria del proveedor */
    public int getIdProv() { return idProv; }

    /** @return nombre comercial del proveedor */
    public String getNombre() { return nombre; }

    /** @return {@code true} si está disponible para nuevos pedidos */
    public boolean isActivo() { return activo; }

    public void setNombre(String nombre) { this.nombre = nombre; }
    public void setActivo(boolean activo) { this.activo = activo; }

    /** Devuelve el nombre para uso en ComboBox. */
    @Override
    public String toString() { return nombre; }
}
