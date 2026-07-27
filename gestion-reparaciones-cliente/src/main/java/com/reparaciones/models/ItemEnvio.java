package com.reparaciones.models;

/** Resultado del envío de un teléfono individual. */
public class ItemEnvio {
    private String imei;
    private String resultado;
    private String estado;

    public ItemEnvio() {}

    public String getImei() { return imei; }
    public void setImei(String imei) { this.imei = imei; }

    public String getResultado() { return resultado; }
    public void setResultado(String resultado) { this.resultado = resultado; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
}
