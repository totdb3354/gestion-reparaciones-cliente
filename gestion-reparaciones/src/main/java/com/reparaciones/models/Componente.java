package com.reparaciones.models;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Componente {

    private int idCom;
    private String tipo;
    private LocalDateTime fechaRegistro;
    private int stock;
    private int stockMinimo;
    private BigDecimal precioUnidad;

    public Componente(int idCom, String tipo, LocalDateTime fechaRegistro,
                      int stock, int stockMinimo, BigDecimal precioUnidad) {
        this.idCom = idCom;
        this.tipo = tipo;
        this.fechaRegistro = fechaRegistro;
        this.stock = stock;
        this.stockMinimo = stockMinimo;
        this.precioUnidad = precioUnidad;
    }

    public int getIdCom() { return idCom; }
    public void setIdCom(int idCom) { this.idCom = idCom; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public LocalDateTime getFechaRegistro() { return fechaRegistro; }
    public void setFechaRegistro(LocalDateTime fechaRegistro) { this.fechaRegistro = fechaRegistro; }

    public int getStock() { return stock; }
    public void setStock(int stock) { this.stock = stock; }

    public int getStockMinimo() { return stockMinimo; }
    public void setStockMinimo(int stockMinimo) { this.stockMinimo = stockMinimo; }

    public BigDecimal getPrecioUnidad() { return precioUnidad; }
    public void setPrecioUnidad(BigDecimal precioUnidad) { this.precioUnidad = precioUnidad; }

    @Override
    public String toString() { return tipo; }
}