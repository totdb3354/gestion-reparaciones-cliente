package com.reparaciones.models;

import java.time.LocalDateTime;

/** Revisión completa de un teléfono (estetíca + funcional). */
public class RevisionTelefono {
    private int idRevision;
    private String imei;
    private LocalDateTime fechaCreacion;
    private String estGrado;
    private String estPant;
    private String estUsuario;
    private LocalDateTime estFecha;
    private Integer funBateriaPct;
    private boolean funPantTactil;
    private boolean funPantQuemada;
    private boolean funPantMal;
    private boolean funCamMancha;
    private boolean funCamLente;
    private boolean funAltSup;
    private boolean funAltInf;
    private boolean funMic;
    private boolean funFaceId;
    private boolean funMs;
    private String funMsTexto;
    private boolean funBloqueoOp;
    private String funObservacion;
    private String funUsuario;
    private LocalDateTime funFecha;

    public RevisionTelefono() {}

    public int getIdRevision() { return idRevision; }
    public void setIdRevision(int idRevision) { this.idRevision = idRevision; }

    public String getImei() { return imei; }
    public void setImei(String imei) { this.imei = imei; }

    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }

    public String getEstGrado() { return estGrado; }
    public void setEstGrado(String estGrado) { this.estGrado = estGrado; }

    public String getEstPant() { return estPant; }
    public void setEstPant(String estPant) { this.estPant = estPant; }

    public String getEstUsuario() { return estUsuario; }
    public void setEstUsuario(String estUsuario) { this.estUsuario = estUsuario; }

    public LocalDateTime getEstFecha() { return estFecha; }
    public void setEstFecha(LocalDateTime estFecha) { this.estFecha = estFecha; }

    public Integer getFunBateriaPct() { return funBateriaPct; }
    public void setFunBateriaPct(Integer funBateriaPct) { this.funBateriaPct = funBateriaPct; }

    public boolean isFunPantTactil() { return funPantTactil; }
    public void setFunPantTactil(boolean funPantTactil) { this.funPantTactil = funPantTactil; }

    public boolean isFunPantQuemada() { return funPantQuemada; }
    public void setFunPantQuemada(boolean funPantQuemada) { this.funPantQuemada = funPantQuemada; }

    public boolean isFunPantMal() { return funPantMal; }
    public void setFunPantMal(boolean funPantMal) { this.funPantMal = funPantMal; }

    public boolean isFunCamMancha() { return funCamMancha; }
    public void setFunCamMancha(boolean funCamMancha) { this.funCamMancha = funCamMancha; }

    public boolean isFunCamLente() { return funCamLente; }
    public void setFunCamLente(boolean funCamLente) { this.funCamLente = funCamLente; }

    public boolean isFunAltSup() { return funAltSup; }
    public void setFunAltSup(boolean funAltSup) { this.funAltSup = funAltSup; }

    public boolean isFunAltInf() { return funAltInf; }
    public void setFunAltInf(boolean funAltInf) { this.funAltInf = funAltInf; }

    public boolean isFunMic() { return funMic; }
    public void setFunMic(boolean funMic) { this.funMic = funMic; }

    public boolean isFunFaceId() { return funFaceId; }
    public void setFunFaceId(boolean funFaceId) { this.funFaceId = funFaceId; }

    public boolean isFunMs() { return funMs; }
    public void setFunMs(boolean funMs) { this.funMs = funMs; }

    public String getFunMsTexto() { return funMsTexto; }
    public void setFunMsTexto(String funMsTexto) { this.funMsTexto = funMsTexto; }

    public boolean isFunBloqueoOp() { return funBloqueoOp; }
    public void setFunBloqueoOp(boolean funBloqueoOp) { this.funBloqueoOp = funBloqueoOp; }

    public String getFunObservacion() { return funObservacion; }
    public void setFunObservacion(String funObservacion) { this.funObservacion = funObservacion; }

    public String getFunUsuario() { return funUsuario; }
    public void setFunUsuario(String funUsuario) { this.funUsuario = funUsuario; }

    public LocalDateTime getFunFecha() { return funFecha; }
    public void setFunFecha(LocalDateTime funFecha) { this.funFecha = funFecha; }
}
