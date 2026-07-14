package com.reparaciones.models;

import java.time.LocalDateTime;

/** Lote de importación de teléfonos (espejo del DTO del servidor). */
public class Lote {
    private int idLote;
    private String batchNumber;
    private int idProv;
    private String proveedor;
    private LocalDateTime fechaImport;
    private String nota;
    private int numTelefonos;
    private LocalDateTime updatedAt;

    public Lote() {}

    public int getIdLote()               { return idLote; }
    public String getBatchNumber()       { return batchNumber; }
    public int getIdProv()               { return idProv; }
    public String getProveedor()         { return proveedor; }
    public LocalDateTime getFechaImport(){ return fechaImport; }
    public String getNota()              { return nota; }
    public int getNumTelefonos()         { return numTelefonos; }
    public LocalDateTime getUpdatedAt()  { return updatedAt; }

    @Override public String toString() { return batchNumber + " (" + proveedor + ")"; }
}
