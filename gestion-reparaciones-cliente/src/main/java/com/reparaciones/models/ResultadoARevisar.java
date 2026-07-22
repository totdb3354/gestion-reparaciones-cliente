package com.reparaciones.models;

/** Resultado del escaneo masivo de un IMEI para pasarlo a revisión. */
public class ResultadoARevisar {
    private String imei;
    private String resultado;

    public ResultadoARevisar() {}

    public String getImei() { return imei; }
    public void setImei(String imei) { this.imei = imei; }

    public String getResultado() { return resultado; }
    public void setResultado(String resultado) { this.resultado = resultado; }
}
