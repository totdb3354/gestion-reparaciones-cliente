package com.reparaciones.models;

/** Respuesta de POST /api/lotes/verificar para un IMEI que ya existe. */
public class VerificacionImei {

    private String imei;
    private boolean existe;
    private String estado;          // null = histórico
    private int trabajosAbiertos;
    private String modelo;

    public VerificacionImei(String imei, boolean existe, String estado, int trabajosAbiertos, String modelo) {
        this.imei = imei; this.existe = existe; this.estado = estado;
        this.trabajosAbiertos = trabajosAbiertos; this.modelo = modelo;
    }

    public String getImei()          { return imei; }
    public boolean isExiste()        { return existe; }
    public String getEstado()        { return estado; }
    public int getTrabajosAbiertos() { return trabajosAbiertos; }
    public String getModelo()        { return modelo; }

    /** Activo = en el ciclo (estado no final) o con trabajo abierto ⇒ conflicto al importar. */
    public boolean esActivo() {
        if (trabajosAbiertos > 0) return true;
        return estado != null && !estado.equals("OK") && !estado.equals("ENVIADO") && !estado.equals("DESGUACE");
    }
}
