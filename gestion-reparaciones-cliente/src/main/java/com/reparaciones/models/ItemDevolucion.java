package com.reparaciones.models;

/** Resultado del registro de una devolución individual. */
public class ItemDevolucion {
    private String imei;
    private String resultado;
    private Integer envio;

    public ItemDevolucion() {}

    public String getImei() { return imei; }
    public void setImei(String imei) { this.imei = imei; }

    public String getResultado() { return resultado; }
    public void setResultado(String resultado) { this.resultado = resultado; }

    public Integer getEnvio() { return envio; }
    public void setEnvio(Integer envio) { this.envio = envio; }
}
