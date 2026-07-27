package com.reparaciones.models;

import java.util.List;

/** Resultado del envío masivo de teléfonos. */
public class ResultadoEnvioLote {
    private Integer idEnvio;
    private List<ItemEnvio> items;

    public ResultadoEnvioLote() {}

    public Integer getIdEnvio() { return idEnvio; }
    public void setIdEnvio(Integer idEnvio) { this.idEnvio = idEnvio; }

    public List<ItemEnvio> getItems() { return items; }
    public void setItems(List<ItemEnvio> items) { this.items = items; }
}
