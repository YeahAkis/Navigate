package org.notionsmp.navigate;

import org.bukkit.Material;

import java.util.EnumSet;
import java.util.Set;

record NavigateOptions(boolean safeMode, boolean follow, Set<Material> materials) {

    static final NavigateOptions DEFAULT = new NavigateOptions(true, false, null);

    static NavigateOptions parse(String input) {
        boolean safeMode = true;
        boolean follow = false;
        Set<Material> materials = null;

        for (String token : input.trim().split("\\s+")) {
            if (token.isEmpty()) {
                continue;
            }
            switch (token.toLowerCase()) {
                case "follow" -> follow = true;
                case "safe", "true" -> safeMode = true;
                case "unsafe", "false" -> safeMode = false;
                default -> {
                    if (materials == null) {
                        materials = EnumSet.noneOf(Material.class);
                    }
                    for (String name : token.split(",")) {
                        if (name.isEmpty()) {
                            continue;
                        }
                        Material material = Material.matchMaterial(name);
                        if (material == null || !material.isBlock()) {
                            throw new IllegalArgumentException(name);
                        }
                        materials.add(material);
                    }
                }
            }
        }

        return new NavigateOptions(safeMode, follow, materials == null || materials.isEmpty() ? null : materials);
    }
}
