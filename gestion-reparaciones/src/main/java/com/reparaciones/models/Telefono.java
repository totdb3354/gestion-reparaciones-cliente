package com.reparaciones.models;

public class Telefono {

    private long imei;

    public Telefono(long imei) {
        this.imei = imei;
    }

    public long getImei() { return imei; }
    public void setImei(long imei) { this.imei = imei; }
}
