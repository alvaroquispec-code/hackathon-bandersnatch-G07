package com.tuckersoft.branchengine.service;

import java.text.Normalizer;

/** Reglas deterministas de clasificacion. La primera que se cumple gana. */
public final class BranchRules {
    private BranchRules() {}

    public static String normalize(String raw) {
        return Normalizer.normalize(raw == null ? "" : raw, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase();
    }

    public static String classify(String rawInput) {
        String t = normalize(rawInput);
        if (!t.matches("(?s).*[a-z].*")) return "ENTRADA_CORRUPTA";
        if (containsAny(t, "netflix", "camara", "espectador", "videojuego")) return "RUPTURA_CUARTA_PARED";
        if (containsAny(t, "vigilan", "simbolo", "conspiracion")) return "SOSPECHA";
        if (containsAny(t, "rechaza", "destruye", "desobedece", "renuncia")) return "REBELDIA";
        return "OBEDIENCIA";
    }

    public static String handlerUnit(String branchType) {
        return switch (branchType) {
            case "OBEDIENCIA" -> "Mesa de Guion";
            case "REBELDIA" -> "Control de Continuidad";
            case "SOSPECHA" -> "Oficina de Seguridad";
            case "RUPTURA_CUARTA_PARED" -> "Departamento Netflix";
            default -> "Archivo de Errores";
        };
    }

    public static String outcomeCode(String branchType) {
        return switch (branchType) {
            case "OBEDIENCIA" -> "ADVANCE_MAIN_PATH";
            case "REBELDIA" -> "FORK_TIMELINE";
            case "SOSPECHA" -> "INJECT_WHITE_BEAR_SYMBOL";
            case "RUPTURA_CUARTA_PARED" -> "BREAK_FOURTH_WALL";
            default -> "DISCARD_INPUT";
        };
    }

    /** {deltaLucidez, deltaControl} */
    public static int[] impactDeltas(String impactLevel) {
        return switch (impactLevel) {
            case "LEVE" -> new int[]{-5, 5};
            case "MODERADO" -> new int[]{-15, 10};
            case "GRAVE" -> new int[]{-30, 20};
            case "CRITICO" -> new int[]{-40, 45};
            default -> throw new IllegalArgumentException("impactLevel invalido: " + impactLevel);
        };
    }

    private static boolean containsAny(String t, String... words) {
        for (String w : words) if (t.contains(w)) return true;
        return false;
    }
}
