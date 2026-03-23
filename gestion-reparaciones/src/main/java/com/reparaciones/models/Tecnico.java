package com.reparaciones.models;

public class Tecnico {

    private int idTec;
    private String nombre;

    public Tecnico(int idTec, String nombre) {
        this.idTec = idTec;
        this.nombre = nombre;
    }

    public int getIdTec() { return idTec; }
    public void setIdTec(int idTec) { this.idTec = idTec; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    @Override
    public String toString() { return nombre; }
}
