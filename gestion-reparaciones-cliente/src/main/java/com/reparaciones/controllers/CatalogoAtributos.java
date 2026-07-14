package com.reparaciones.controllers;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Matriz modelo→{colores oficiales de Apple (inglés), capacidades GB} (spec atributos SKU §2.3).
 * Fuente de verdad de los selectores filtrados y de la validación del importador.
 * Se amplía junto con MODELOS_ORDENADOS cuando salen series nuevas.
 */
public final class CatalogoAtributos {

    private CatalogoAtributos() {}

    private static final Map<String, List<String>> COLORES = new LinkedHashMap<>();
    private static final Map<String, List<Integer>> CAPACIDADES = new LinkedHashMap<>();

    private static void def(String modelo, List<Integer> caps, List<String> colores) {
        COLORES.put(modelo, colores);
        CAPACIDADES.put(modelo, caps);
    }

    static {
        List<String> c6s   = List.of("Silver", "Gold", "Space Gray", "Rose Gold");
        def("6s",       List.of(16, 32, 64, 128), c6s);
        def("6splus",   List.of(16, 32, 64, 128), c6s);
        List<String> c7    = List.of("Jet Black", "Black", "Silver", "Gold", "Rose Gold", "(PRODUCT)RED");
        def("7",        List.of(32, 128, 256), c7);
        def("7plus",    List.of(32, 128, 256), c7);
        List<String> c8    = List.of("Silver", "Gold", "Space Gray", "(PRODUCT)RED");
        def("8",        List.of(64, 128, 256), c8);
        def("8plus",    List.of(64, 128, 256), c8);
        def("se2020",   List.of(64, 128, 256), List.of("Black", "White", "(PRODUCT)RED"));
        def("x",        List.of(64, 256), List.of("Silver", "Space Gray"));
        def("xr",       List.of(64, 128, 256), List.of("Black", "White", "Blue", "Yellow", "Coral", "(PRODUCT)RED"));
        List<String> cxs   = List.of("Silver", "Gold", "Space Gray");
        def("xs",       List.of(64, 256, 512), cxs);
        def("xsmax",    List.of(64, 256, 512), cxs);
        def("11",       List.of(64, 128, 256), List.of("Black", "White", "Green", "Yellow", "Purple", "(PRODUCT)RED"));
        List<String> c11p  = List.of("Silver", "Gold", "Space Gray", "Midnight Green");
        def("11pro",    List.of(64, 256, 512), c11p);
        def("11promax", List.of(64, 256, 512), c11p);
        List<String> c12   = List.of("Black", "White", "Blue", "Green", "Purple", "(PRODUCT)RED");
        def("12",       List.of(64, 128, 256), c12);
        def("12mini",   List.of(64, 128, 256), c12);
        List<String> c12p  = List.of("Silver", "Gold", "Graphite", "Pacific Blue");
        def("12pro",    List.of(128, 256, 512), c12p);
        def("12promax", List.of(128, 256, 512), c12p);
        List<String> c13   = List.of("Starlight", "Midnight", "Blue", "Pink", "Green", "(PRODUCT)RED");
        def("13",       List.of(128, 256, 512), c13);
        def("13mini",   List.of(128, 256, 512), c13);
        List<String> c13p  = List.of("Silver", "Gold", "Graphite", "Sierra Blue", "Alpine Green");
        def("13pro",    List.of(128, 256, 512, 1024), c13p);
        def("13promax", List.of(128, 256, 512, 1024), c13p);
        List<String> c14   = List.of("Midnight", "Starlight", "Blue", "Purple", "Yellow", "(PRODUCT)RED");
        def("14",       List.of(128, 256, 512), c14);
        def("14plus",   List.of(128, 256, 512), c14);
        List<String> c14p  = List.of("Silver", "Gold", "Space Black", "Deep Purple");
        def("14pro",    List.of(128, 256, 512, 1024), c14p);
        def("14promax", List.of(128, 256, 512, 1024), c14p);
        List<String> c15   = List.of("Black", "Blue", "Green", "Yellow", "Pink");
        def("15",       List.of(128, 256, 512), c15);
        def("15plus",   List.of(128, 256, 512), c15);
        List<String> c15p  = List.of("Natural Titanium", "Blue Titanium", "White Titanium", "Black Titanium");
        def("15pro",    List.of(128, 256, 512, 1024), c15p);
        def("15promax", List.of(256, 512, 1024), c15p);
        List<String> c16   = List.of("Black", "White", "Pink", "Teal", "Ultramarine");
        def("16",       List.of(128, 256, 512), c16);
        def("16plus",   List.of(128, 256, 512), c16);
        def("16e",      List.of(128, 256, 512), List.of("Black", "White"));
        List<String> c16p  = List.of("Black Titanium", "White Titanium", "Natural Titanium", "Desert Titanium");
        def("16pro",    List.of(128, 256, 512, 1024), c16p);
        def("16promax", List.of(256, 512, 1024), c16p);
        def("17",       List.of(256, 512), List.of("Black", "White", "Sage", "Mist Blue", "Lavender"));
        def("air",      List.of(256, 512, 1024), List.of("Space Black", "Cloud White", "Light Gold", "Sky Blue"));
        List<String> c17p  = List.of("Silver", "Cosmic Orange", "Deep Blue");
        def("17pro",    List.of(256, 512, 1024), c17p);
        def("17promax", List.of(256, 512, 1024, 2048), c17p);
    }

    public static final List<String> COLORES_TODOS = COLORES.values().stream()
            .flatMap(List::stream).distinct().sorted().toList();

    public static final List<Integer> CAPACIDADES_TODAS = CAPACIDADES.values().stream()
            .flatMap(List::stream).distinct().sorted().toList();

    public static List<String> coloresDe(String modeloInterno) {
        return modeloInterno == null ? List.of() : COLORES.getOrDefault(modeloInterno, List.of());
    }

    public static List<Integer> capacidadesDe(String modeloInterno) {
        return modeloInterno == null ? List.of() : CAPACIDADES.getOrDefault(modeloInterno, List.of());
    }

    public static boolean esColorOficial(String color) {
        return color != null && COLORES_TODOS.contains(color);
    }
}
