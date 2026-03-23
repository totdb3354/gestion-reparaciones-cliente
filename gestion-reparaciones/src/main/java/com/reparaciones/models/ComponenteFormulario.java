package com.reparaciones.models;

import javafx.beans.property.*;

/**
 * Modelo para cada fila de la tabla de componentes en el formulario de nueva reparación.
 * Usa JavaFX properties para que la TableView detecte cambios en tiempo real
 * y pueda habilitar/deshabilitar el botón de guardar automáticamente.
 */
public class ComponenteFormulario {

    private final IntegerProperty idCom;
    private final StringProperty tipo;
    private final StringProperty sku;
    private final IntegerProperty stock;
    private final IntegerProperty unidades;      // 0 o 1 (máximo 1)
    private final BooleanProperty reutilizado;
    private final StringProperty observacion;

    public ComponenteFormulario(int idCom, String tipo, String sku, int stock) {
        this.idCom      = new SimpleIntegerProperty(idCom);
        this.tipo       = new SimpleStringProperty(tipo);
        this.sku        = new SimpleStringProperty(sku);
        this.stock      = new SimpleIntegerProperty(stock);
        this.unidades   = new SimpleIntegerProperty(0);
        this.reutilizado = new SimpleBooleanProperty(false);
        this.observacion = new SimpleStringProperty("");
    }

    public int getIdCom()               { return idCom.get(); }
    public String getTipo()             { return tipo.get(); }
    public StringProperty skuProperty() { return sku; }
    public String getSku()              { return sku.get(); }
    public void setSku(String s)        { sku.set(s); }
    public int getStock()               { return stock.get(); }
    public int getUnidades()            { return unidades.get(); }
    public IntegerProperty unidadesProperty() { return unidades; }
    public void setUnidades(int u)      { unidades.set(u); }
    public boolean isReutilizado()      { return reutilizado.get(); }
    public BooleanProperty reutilizadoProperty() { return reutilizado; }
    public void setReutilizado(boolean r) { reutilizado.set(r); }
    public String getObservacion()      { return observacion.get(); }
    public void setObservacion(String o) { observacion.set(o); }
}