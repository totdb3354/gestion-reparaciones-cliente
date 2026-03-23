package com.reparaciones.models;

import java.time.LocalDateTime;

public class CompraComponente {

    private int idCompra;
    private int idCom;
    private int cantidad;
    private boolean esUrgente;
    private LocalDateTime fechaPedido;

    public CompraComponente(int idCompra, int idCom, int cantidad,
                             boolean esUrgente, LocalDateTime fechaPedido) {
        this.idCompra = idCompra;
        this.idCom = idCom;
        this.cantidad = cantidad;
        this.esUrgente = esUrgente;
        this.fechaPedido = fechaPedido;
    }

    public int getIdCompra() { return idCompra; }
    public void setIdCompra(int idCompra) { this.idCompra = idCompra; }

    public int getIdCom() { return idCom; }
    public void setIdCom(int idCom) { this.idCom = idCom; }

    public int getCantidad() { return cantidad; }
    public void setCantidad(int cantidad) { this.cantidad = cantidad; }

    public boolean isEsUrgente() { return esUrgente; }
    public void setEsUrgente(boolean esUrgente) { this.esUrgente = esUrgente; }

    public LocalDateTime getFechaPedido() { return fechaPedido; }
    public void setFechaPedido(LocalDateTime fechaPedido) { this.fechaPedido = fechaPedido; }
}