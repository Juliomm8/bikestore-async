package com.bikestore.asyncapp.config;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

public final class ProcessProfileValidator {
    private static final Set<String> PROCESS_PROFILES = Set.of("integrated", "api", "payment", "email");

    private ProcessProfileValidator() {
    }

    public static void validate(String... activeProfiles) {
        List<String> selectedProfiles = Arrays.stream(activeProfiles)
                .filter(PROCESS_PROFILES::contains)
                .distinct()
                .toList();

        if (selectedProfiles.size() > 1) {
            throw new IllegalArgumentException(
                    "Activa un solo modo de BikeStore: integrated, api, payment o email; recibidos: "
                            + String.join(", ", selectedProfiles));
        }

        if (Arrays.asList(activeProfiles).contains("evaluation")
                && !Arrays.asList(activeProfiles).contains("payment")) {
            throw new IllegalArgumentException("El perfil evaluation solo se puede combinar con payment");
        }
    }
}
