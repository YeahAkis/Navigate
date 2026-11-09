package org.notionsmp.navigate;

import org.bukkit.Color;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Locale;

record ParticleSettings(boolean enabled, Particle particle, Object data, int amount, double rangeSquared, long interval) {

    static ParticleSettings load(FileConfiguration config) {
        boolean enabled = config.getBoolean("particles.enabled", true);
        int amount = Math.max(1, config.getInt("particles.amount", 3));
        double range = Math.max(1, config.getDouble("particles.range", 64));
        long interval = Math.max(1, config.getLong("particles.interval", 10));
        float size = (float) Math.max(0.1, config.getDouble("particles.size", 1.0));

        Particle particle = Registry.PARTICLE_TYPE.get(NamespacedKey.minecraft(config.getString("particles.type", "dust").toLowerCase(Locale.ROOT)));
        Object data = null;
        if (particle == null || particle != Particle.DUST && particle.getDataType() != Void.class) {
            particle = Particle.DUST;
        }
        if (particle == Particle.DUST) {
            data = new Particle.DustOptions(parseColor(config.getString("particles.color", "#DC143C")), size);
        }
        return new ParticleSettings(enabled, particle, data, amount, range * range, interval);
    }

    private static Color parseColor(String value) {
        String hex = value.startsWith("#") ? value.substring(1) : value;
        try {
            return Color.fromRGB(Integer.parseInt(hex, 16) & 0xFFFFFF);
        } catch (NumberFormatException e) {
            return Color.fromRGB(220, 20, 60);
        }
    }
}
