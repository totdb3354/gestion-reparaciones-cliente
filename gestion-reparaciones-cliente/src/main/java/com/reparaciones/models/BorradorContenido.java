package com.reparaciones.models;

import java.util.ArrayList;
import java.util.List;

/** Contenido serializable (Gson) del borrador del modal de reparación. */
public class BorradorContenido {

    public String modelo;                 // valor de cbFiltroModelo, o null
    public List<Fila> filas = new ArrayList<>();
    public List<OtraAccion> otros = new ArrayList<>();

    /** Inputs no guardados de una fila de componente. */
    public static class Fila {
        public String prefijo;            // identifica la fila (tipo de componente)
        public int idCom;                 // SKU seleccionado, o -1
        public int cantidad;
        public boolean reutilizado;
        public String observacion;        // null si no hay
        public boolean solicitudNueva;    // solicitud marcada en sesión, sin guardar
        public String descripcionSolicitud;
        public boolean agotadoConfirmado; // agotado confirmado en sesión, sin guardar
        public String descripcionAgotado;
        // Guardado individual:
        public boolean guardada;
        public String  idRepGenerado;
        public String  fechaGuardado;
    }

    public static class OtraAccion {
        public String descripcion;
        public boolean guardada;
        public String  idRepGenerado;
        public String  fechaGuardado;
    }
}
