package com.themesmp.anticheat;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PlayerData {

    public final Map<String, Double> vl = new HashMap<>();
    private final Map<String, Double> buf = new HashMap<>();

    public final long joinTime = System.currentTimeMillis();
    public long lastTeleport;
    public long lastVelocity;
    public long lastDamage;
    public long lastIce;

    public int airTicks;

    // timer check
    public int moves;
    public long windowStart = System.currentTimeMillis();
    public int timerStrikes;

    // killaura multi-target
    public UUID lastTarget;
    public long lastHit;

    public final ArrayDeque<Long> swings = new ArrayDeque<>();
    public final ArrayDeque<Long> places = new ArrayDeque<>();

    /** Adds to a buffer; returns true (and resets it) once the limit is reached. */
    public boolean buffer(String key, double add, double limit) {
        double v = buf.merge(key, add, Double::sum);
        if (v >= limit) {
            buf.put(key, 0.0);
            return true;
        }
        return false;
    }

    public void relax(String key, double amount) {
        buf.computeIfPresent(key, (k, v) -> Math.max(0.0, v - amount));
    }
}
