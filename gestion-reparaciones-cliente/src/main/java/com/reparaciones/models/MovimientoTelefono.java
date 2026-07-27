package com.reparaciones.models;

import java.time.LocalDateTime;

/** Registro de un movimiento en la línea de vida de un teléfono. */
public class MovimientoTelefono {
    private int idMov;
    private String imei;
    private String ubicacionOrigen;
    private String ubicacionDestino;
    private LocalDateTime fecha;
    private Integer idUsu;
    private String usuario;
    private String motivo;
    private String referencia;

    public MovimientoTelefono() {}

    public int getIdMov() { return idMov; }
    public void setIdMov(int idMov) { this.idMov = idMov; }

    public String getImei() { return imei; }
    public void setImei(String imei) { this.imei = imei; }

    public String getUbicacionOrigen() { return ubicacionOrigen; }
    public void setUbicacionOrigen(String ubicacionOrigen) { this.ubicacionOrigen = ubicacionOrigen; }

    public String getUbicacionDestino() { return ubicacionDestino; }
    public void setUbicacionDestino(String ubicacionDestino) { this.ubicacionDestino = ubicacionDestino; }

    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }

    public Integer getIdUsu() { return idUsu; }
    public void setIdUsu(Integer idUsu) { this.idUsu = idUsu; }

    public String getUsuario() { return usuario; }
    public void setUsuario(String usuario) { this.usuario = usuario; }

    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }

    public String getReferencia() { return referencia; }
    public void setReferencia(String referencia) { this.referencia = referencia; }
}
