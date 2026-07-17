package com.reparaciones.models;

public class Proveedor {

    private int idProv;
    private String nombre;
    private boolean activo;
    private String divisa;
    private String comentario;
    private String tipo;

    public Proveedor(int idProv, String nombre, boolean activo, String divisa, String comentario) {
        this.idProv     = idProv;
        this.nombre     = nombre;
        this.activo     = activo;
        this.divisa     = divisa != null ? divisa : "EUR";
        this.comentario = comentario;
    }

    public int     getIdProv()     { return idProv; }
    public String  getNombre()     { return nombre; }
    public boolean isActivo()      { return activo; }
    public String  getDivisa()     { return divisa; }
    public String  getComentario() { return comentario; }
    public String  getTipo()       { return tipo; }

    public void setNombre(String nombre)         { this.nombre     = nombre; }
    public void setActivo(boolean activo)        { this.activo     = activo; }
    public void setDivisa(String divisa)         { this.divisa     = divisa; }
    public void setComentario(String comentario) { this.comentario = comentario; }
    public void setTipo(String tipo)             { this.tipo       = tipo; }

    @Override
    public String toString() { return nombre; }
}
