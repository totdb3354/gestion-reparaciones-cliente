package com.reparaciones.models;

/** Punto del gráfico de estadísticas por puntos de dificultad (spec 2026-09-01). */
public class PuntoEstadisticaPuntos {
    private String nombreTecnico;
    private String periodo;
    private double puntos;
    private double puntosNormales;
    private double puntosGlass;
    private double puntosPulidos;
    private int    nNormales;
    private int    nGlass;
    private int    nPulidos;
    private int    nSinPiezas;
    private Integer nImeis;        // aditivo 2026-09-04; null si el servidor aún no lo envía
    private Double  puntosJornada; // aditivo 2026-09-08: cerrados en horario; null con servidor antiguo
    private Integer nImeisJornada; // aditivo 2026-09-08: IMEIs con algún cierre en horario; null con servidor antiguo

    public PuntoEstadisticaPuntos() {}

    public PuntoEstadisticaPuntos(String nombreTecnico, String periodo, double puntos,
                                  double puntosNormales, double puntosGlass, double puntosPulidos,
                                  int nNormales, int nGlass, int nPulidos, int nSinPiezas) {
        this(nombreTecnico, periodo, puntos, puntosNormales, puntosGlass, puntosPulidos,
             nNormales, nGlass, nPulidos, nSinPiezas, null, null, null);
    }

    /** Constructor completo (tests): los tres últimos pueden ser null como con un servidor antiguo. */
    public PuntoEstadisticaPuntos(String nombreTecnico, String periodo, double puntos,
                                  double puntosNormales, double puntosGlass, double puntosPulidos,
                                  int nNormales, int nGlass, int nPulidos, int nSinPiezas,
                                  Integer nImeis, Double puntosJornada, Integer nImeisJornada) {
        this.nombreTecnico = nombreTecnico;
        this.periodo = periodo;
        this.puntos = puntos;
        this.puntosNormales = puntosNormales;
        this.puntosGlass = puntosGlass;
        this.puntosPulidos = puntosPulidos;
        this.nNormales = nNormales;
        this.nGlass = nGlass;
        this.nPulidos = nPulidos;
        this.nSinPiezas = nSinPiezas;
        this.nImeis = nImeis;
        this.puntosJornada = puntosJornada;
        this.nImeisJornada = nImeisJornada;
    }

    public String getNombreTecnico()  { return nombreTecnico; }
    public String getPeriodo()        { return periodo; }
    public double getPuntos()         { return puntos; }
    public double getPuntosNormales() { return puntosNormales; }
    public double getPuntosGlass()    { return puntosGlass; }
    public double getPuntosPulidos()  { return puntosPulidos; }
    public int    getnNormales()      { return nNormales; }
    public int    getnGlass()         { return nGlass; }
    public int    getnPulidos()       { return nPulidos; }
    public int    getnSinPiezas()     { return nSinPiezas; }

    /** IMEIs distintos del técnico en el periodo; {@code null} contra un servidor sin el campo. */
    public Integer getnImeis()        { return nImeis; }

    /** Puntos cerrados en horario (spec 2026-09-08); {@code null} contra un servidor sin el campo. */
    public Double  getPuntosJornada() { return puntosJornada; }

    /** IMEIs con algún cierre en horario (spec 2026-09-08); {@code null} contra un servidor sin el campo. */
    public Integer getnImeisJornada() { return nImeisJornada; }
}
