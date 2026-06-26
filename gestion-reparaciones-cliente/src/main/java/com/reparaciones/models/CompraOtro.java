package com.reparaciones.models;

import java.time.LocalDateTime;

/**
 * Pedido de otro material (no componente) a un proveedor.
 * <p>Similar a {@code CompraComponente}, pero en lugar de referenciar
 * un componente por ID, incluye un campo de texto libre {@code concepto}
 * para describir el material pedido.</p>
 * <p>El precio se almacena en la divisa original del pedido
 * ({@code precioUnidadPedido} / {@code divisa}) y convertido
 * automáticamente a euros ({@code precioEur}) usando el tipo de
 * cambio vigente en la fecha del pedido.</p>
 */
public class CompraOtro {

    /** ID del pedido de otro material. */
    private int idCompraOtro;

    /** Descripción libre del material pedido. */
    private String concepto;

    /** ID del proveedor al que se realiza el pedido. */
    private int idProv;

    /** Nombre del proveedor — obtenido por JOIN con {@code Proveedor}. */
    private String nombreProveedor;

    /** Número de unidades pedidas. */
    private int cantidad;

    /**
     * Número de unidades ya recibidas, o {@code null} si no se ha recibido
     * ninguna todavía.
     */
    private Integer cantidadRecibida;

    /** {@code true} si el pedido se marcó como urgente. */
    private boolean esUrgente;

    /** Fecha y hora en que se registró el pedido. */
    private LocalDateTime fechaPedido;

    /**
     * Fecha y hora de recepción (total o parcial), o {@code null}
     * si el pedido sigue pendiente.
     */
    private LocalDateTime fechaLlegada;

    /** Precio por unidad en la divisa original del pedido. */
    private double precioUnidadPedido;

    /** Código ISO 4217 de la divisa del pedido (p. ej. {@code "EUR"}, {@code "CNY"}). */
    private String divisa;

    /**
     * Precio por unidad convertido a euros usando el tipo de cambio de la
     * fecha del pedido. Igual a {@code precioUnidadPedido} si {@code divisa="EUR"}.
     */
    private double precioEur;

    /** Estado actual del pedido — reutiliza el enum {@code CompraComponente.Estado}. */
    private CompraComponente.Estado estado;

    /** Última vez que se modificó el registro en BD. */
    private LocalDateTime updatedAt;

    /**
     * Constructor completo — llamado desde {@code CompraDAO}.
     *
     * @param idCompraOtro        ID del pedido de otro material
     * @param concepto            descripción libre del material
     * @param idProv              ID del proveedor
     * @param nombreProveedor     nombre del proveedor (JOIN)
     * @param cantidad            unidades pedidas
     * @param cantidadRecibida    unidades recibidas, o {@code null}
     * @param esUrgente           {@code true} si el pedido es urgente
     * @param fechaPedido         fecha de registro del pedido
     * @param fechaLlegada        fecha de recepción, o {@code null}
     * @param precioUnidadPedido  precio por unidad en divisa original
     * @param divisa              código ISO 4217 de la divisa
     * @param precioEur           precio por unidad convertido a EUR
     * @param estado              estado del pedido
     * @param updatedAt           última actualización del registro
     */
    public CompraOtro(int idCompraOtro, String concepto,
                      int idProv, String nombreProveedor,
                      int cantidad, Integer cantidadRecibida,
                      boolean esUrgente, LocalDateTime fechaPedido,
                      LocalDateTime fechaLlegada,
                      double precioUnidadPedido, String divisa, double precioEur,
                      CompraComponente.Estado estado,
                      LocalDateTime updatedAt) {
        this.idCompraOtro         = idCompraOtro;
        this.concepto             = concepto;
        this.idProv               = idProv;
        this.nombreProveedor      = nombreProveedor;
        this.cantidad             = cantidad;
        this.cantidadRecibida     = cantidadRecibida;
        this.esUrgente            = esUrgente;
        this.fechaPedido          = fechaPedido;
        this.fechaLlegada         = fechaLlegada;
        this.precioUnidadPedido   = precioUnidadPedido;
        this.divisa               = divisa;
        this.precioEur            = precioEur;
        this.estado               = estado;
        this.updatedAt            = updatedAt;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    /** @return ID del pedido de otro material */
    public int getIdCompraOtro() { return idCompraOtro; }

    /** @return descripción libre del material */
    public String getConcepto() { return concepto; }

    /** @return ID del proveedor */
    public int getIdProv() { return idProv; }

    /** @return nombre del proveedor */
    public String getNombreProveedor() { return nombreProveedor; }

    /** @return número de unidades pedidas */
    public int getCantidad() { return cantidad; }

    /** @return unidades recibidas, o {@code null} si ninguna todavía */
    public Integer getCantidadRecibida() { return cantidadRecibida; }

    /** @return {@code true} si el pedido se marcó como urgente */
    public boolean isEsUrgente() { return esUrgente; }

    /** @return fecha y hora de registro del pedido */
    public LocalDateTime getFechaPedido() { return fechaPedido; }

    /** @return fecha de recepción, o {@code null} si sigue pendiente */
    public LocalDateTime getFechaLlegada() { return fechaLlegada; }

    /** @return precio por unidad en la divisa original */
    public double getPrecioUnidadPedido() { return precioUnidadPedido; }

    /** @return código ISO 4217 de la divisa del pedido */
    public String getDivisa() { return divisa; }

    /** @return precio por unidad convertido a EUR */
    public double getPrecioEur() { return precioEur; }

    /** @return estado actual del pedido */
    public CompraComponente.Estado getEstado() { return estado; }

    /** @return última actualización del registro en BD */
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    // ── Setters ───────────────────────────────────────────────────────────────

    public void setCantidad(int cantidad)                       { this.cantidad = cantidad; }
    public void setCantidadRecibida(Integer cantidadRecibida)   { this.cantidadRecibida = cantidadRecibida; }
    public void setEsUrgente(boolean esUrgente)                 { this.esUrgente = esUrgente; }
    public void setFechaLlegada(LocalDateTime fechaLlegada)     { this.fechaLlegada = fechaLlegada; }
    public void setPrecioUnidadPedido(double precio)            { this.precioUnidadPedido = precio; }
    public void setDivisa(String divisa)                        { this.divisa = divisa; }
    public void setPrecioEur(double precioEur)                  { this.precioEur = precioEur; }
    public void setEstado(CompraComponente.Estado estado)       { this.estado = estado; }
    public void setIdProv(int idProv)                           { this.idProv = idProv; }
    public void setNombreProveedor(String nombre)               { this.nombreProveedor = nombre; }
    public void setConcepto(String concepto)                    { this.concepto = concepto; }

    // ── Calculados ────────────────────────────────────────────────────────────

    /**
     * Importe total del pedido en la divisa original.
     *
     * @return {@code precioUnidadPedido × cantidad}
     */
    public double getTotalPedido() { return precioUnidadPedido * cantidad; }

    /**
     * Importe total del pedido convertido a euros.
     *
     * @return {@code precioEur × cantidad}
     */
    public double getTotalEur() { return precioEur * cantidad; }
}
