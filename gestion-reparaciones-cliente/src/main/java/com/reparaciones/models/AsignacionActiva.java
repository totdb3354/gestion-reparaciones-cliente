package com.reparaciones.models;

/**
 * Asignación activa mínima de un IMEI para el aviso de asignación cruzada.
 * Deserializada por Gson (bind por nombre de campo); la categoría la deriva el
 * cliente del prefijo de {@code idRep} (A=reparación, AG=glass, AP=pulido).
 */
public class AsignacionActiva {
    private String idRep;
    private String nombreTecnico;
    private int idTec;

    public String getIdRep()        { return idRep; }
    public String getNombreTecnico() { return nombreTecnico; }
    public int getIdTec()           { return idTec; }
}
