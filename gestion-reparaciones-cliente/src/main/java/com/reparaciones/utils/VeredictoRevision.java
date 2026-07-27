package com.reparaciones.utils;

import com.reparaciones.models.RevisionTelefono;

import java.util.ArrayList;
import java.util.List;

/**
 * Evaluación del veredicto de la revisión funcional (spec F2b §5). Única fuente de la
 * regla (el servidor solo aplica el veto duro del OK y el bloqueo automático).
 * Reglas: bloqueo manda; batería &lt; 85 → reparación obligatoria (añade NORMAL);
 * PANT P/G → PULIDO/GLASS; cualquier check funcional → NORMAL; limpio = nada de lo anterior
 * con batería medida ≥ 85. Devuelve null si la parte funcional no está guardada.
 */
public final class VeredictoRevision {

    private VeredictoRevision() {}

    public record Veredicto(boolean bloqueado, boolean bateriaObligatoria, List<String> trabajos, boolean limpio) {}

    public static Veredicto evaluar(RevisionTelefono r) {
        if (r == null || r.getFunFecha() == null) return null;
        boolean bloqueado = r.isFunBloqueoOp();
        boolean bateriaObligatoria = r.getFunBateriaPct() != null && r.getFunBateriaPct() < 85;
        boolean defectoFuncional = r.isFunPantTactil() || r.isFunPantQuemada() || r.isFunPantMal()
                || r.isFunCamMancha() || r.isFunCamLente() || r.isFunAltSup() || r.isFunAltInf()
                || r.isFunMic() || r.isFunFaceId() || r.isFunMs();
        List<String> trabajos = new ArrayList<>();
        if ("P".equals(r.getEstPant())) trabajos.add("PULIDO");
        if ("G".equals(r.getEstPant())) trabajos.add("GLASS");
        if (defectoFuncional || bateriaObligatoria) trabajos.add("NORMAL");
        boolean limpio = !bloqueado && trabajos.isEmpty()
                && r.getFunBateriaPct() != null && r.getFunBateriaPct() >= 85;
        return new Veredicto(bloqueado, bateriaObligatoria, List.copyOf(trabajos), limpio);
    }

    /** Tipo con el que se precarga el modal de asignar: PANT manda; si no, NORMAL. Null si limpio. */
    public static TipoTrabajo tipoPrincipal(Veredicto v) {
        if (v == null || v.trabajos().isEmpty()) return null;
        return switch (v.trabajos().get(0)) {
            case "PULIDO" -> TipoTrabajo.PULIDO;
            case "GLASS"  -> TipoTrabajo.GLASS;
            default       -> TipoTrabajo.REPARACION;
        };
    }
}
