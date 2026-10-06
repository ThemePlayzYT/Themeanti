package com.themesmp.anticheat;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Shulker;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

public final class CheckListener implements Listener {

    private static final double[] OFF = {-0.31, 0.0, 0.31};

    private final ThemeAnticheat plugin;

    public CheckListener(ThemeAnticheat plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ bookkeeping

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        plugin.data(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.remove(e.getPlayer());
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent e) {
        plugin.data(e.getPlayer()).lastTeleport = System.currentTimeMillis();
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        plugin.data(e.getPlayer()).lastTeleport = System.currentTimeMillis();
    }

    @EventHandler
    public void onVelocity(PlayerVelocityEvent e) {
        plugin.data(e.getPlayer()).lastVelocity = System.currentTimeMillis();
    }

    @EventHandler
    public void onDamaged(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p) {
            plugin.data(p).lastDamage = System.currentTimeMillis();
        }
    }

    // ------------------------------------------------------------------ exemptions

    private boolean basicExempt(Player p) {
        GameMode gm = p.getGameMode();
        return p.hasPermission("theme.bypass")
                || gm == GameMode.CREATIVE
                || gm == GameMode.SPECTATOR
                || p.isDead();
    }

    private boolean moveExempt(Player p, PlayerData d) {
        if (basicExempt(p)) return true;
        if (p.getAllowFlight() || p.isFlying() || p.isGliding() || p.isRiptiding()
                || p.isInsideVehicle()) return true;

        long now = System.currentTimeMillis();
        if (now - d.joinTime < 3000
                || now - d.lastTeleport < 2000
                || now - d.lastVelocity < 2000
                || now - d.lastDamage < 1500) return true;

        if (p.getPing() > plugin.cfg("settings.max-ping", 400)) return true;
        double[] tps = Bukkit.getTPS();
        return tps.length > 0 && tps[0] < plugin.cfg("settings.min-tps", 18.0);
    }

    // ------------------------------------------------------------------ world helpers

    /** True if there is a solid (collidable) block within `depth` below the player's feet. */
    private boolean solidBelow(Location l, double depth) {
        World w = l.getWorld();
        if (w == null) return false;
        for (double ox : OFF) {
            for (double oz : OFF) {
                Block b = w.getBlockAt(
                        (int) Math.floor(l.getX() + ox),
                        (int) Math.floor(l.getY() - depth),
                        (int) Math.floor(l.getZ() + oz));
                if (!b.isPassable()) return true;
            }
        }
        return false;
    }

    /** Water, lava, climbables, webs, bouncy/sticky blocks etc. where normal checks don't apply. */
    private boolean special(Player p, Location l) {
        if (p.isInWater() || p.isInLava() || p.isSwimming()) return true;
        World w = l.getWorld();
        if (w == null) return true;
        for (int dy = -1; dy <= 2; dy++) {
            for (double ox : OFF) {
                for (double oz : OFF) {
                    Block b = w.getBlockAt(
                            (int) Math.floor(l.getX() + ox),
                            (int) Math.floor(l.getY() + dy),
                            (int) Math.floor(l.getZ() + oz));
                    Material m = b.getType();
                    if (b.isLiquid() || Tag.CLIMBABLE.isTagged(m)
                            || m == Material.COBWEB || m == Material.HONEY_BLOCK
                            || m == Material.SLIME_BLOCK || m == Material.BUBBLE_COLUMN
                            || m == Material.POWDER_SNOW || m == Material.SCAFFOLDING
                            || m == Material.SWEET_BERRY_BUSH) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean onIce(Location l) {
        Material a = l.clone().subtract(0, 0.5, 0).getBlock().getType();
        Material b = l.clone().subtract(0, 1.0, 0).getBlock().getType();
        return Tag.ICE.isTagged(a) || Tag.ICE.isTagged(b);
    }

    private boolean hasFlyEffect(Player p) {
        return p.hasPotionEffect(PotionEffectType.LEVITATION)
                || p.hasPotionEffect(PotionEffectType.SLOW_FALLING)
                || p.hasPotionEffect(PotionEffectType.JUMP_BOOST);
    }

    // ------------------------------------------------------------------ movement checks

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        Location from = e.getFrom();
        Location to = e.getTo();
        if (to == null || from.getWorld() != to.getWorld()) return;

        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();
        if (dx == 0 && dy == 0 && dz == 0) return;

        PlayerData d = plugin.data(p);

        if (moveExempt(p, d)) {
            d.airTicks = 0;
            return;
        }

        boolean cancel = false;

        // ---- Timer: too many movement packets per second
        d.moves++;
        long now = System.currentTimeMillis();
        if (now - d.windowStart >= 1000) {
            if (d.moves > plugin.cfg("checks.timer.max-moves-per-second", 25)) {
                d.timerStrikes++;
            } else {
                d.timerStrikes = Math.max(0, d.timerStrikes - 1);
            }
            if (d.timerStrikes >= 3) {
                d.timerStrikes = 0;
                cancel |= plugin.flag(p, "timer", "moves/s=" + d.moves, 1.0);
            }
            d.moves = 0;
            d.windowStart = now;
        }

        boolean realGround = solidBelow(to, 0.2);
        boolean special = special(p, to);
        boolean effect = hasFlyEffect(p);

        // ---- Fly
        if (realGround || special || effect) {
            d.airTicks = 0;
        } else {
            d.airTicks++;
            if (dy > 0.65) {
                cancel |= plugin.flag(p, "fly", String.format("rise dy=%.2f", dy), 2.0);
            } else if (d.airTicks > 12 && dy > -0.03) {
                if (d.buffer("fly", 1.0, 4.0)) {
                    cancel |= plugin.flag(p, "fly", String.format("hover dy=%.3f air=%d", dy, d.airTicks), 1.0);
                }
            } else {
                d.relax("fly", 0.25);
            }
        }

        // ---- GroundSpoof: client says "on ground" while server sees open air
        if (p.isOnGround() && !special && d.airTicks > 3 && !solidBelow(to, 0.75)) {
            boolean support = p.getNearbyEntities(1.0, 1.5, 1.0).stream()
                    .anyMatch(en -> en instanceof Boat || en instanceof Shulker);
            if (!support && d.buffer("groundspoof", 1.0, 4.0)) {
                cancel |= plugin.flag(p, "groundspoof", "air=" + d.airTicks, 1.0);
            }
        } else {
            d.relax("groundspoof", 0.25);
        }

        // ---- Speed
        if (!special) {
            if (onIce(to)) d.lastIce = now;

            double horiz = Math.hypot(dx, dz);
            boolean ground = realGround && solidBelow(from, 0.2);
            double allowed = ground
                    ? plugin.cfg("checks.speed.ground-max", 0.34)
                    : plugin.cfg("checks.speed.air-max", 0.42);

            PotionEffect speed = p.getPotionEffect(PotionEffectType.SPEED);
            if (speed != null) allowed *= 1.0 + 0.2 * (speed.getAmplifier() + 1);
            allowed *= p.getWalkSpeed() / 0.2;
            if (now - d.lastIce < 1500) allowed *= 2.2;

            if (horiz > allowed) {
                if (d.buffer("speed", 1.0, 5.0)) {
                    cancel |= plugin.flag(p, "speed",
                            String.format("%.3f > %.3f", horiz, allowed), 1.0);
                }
            } else {
                d.relax("speed", 0.25);
            }
        }

        if (cancel) {
            e.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ combat checks

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        if (e.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
        if (basicExempt(p)) return;

        PlayerData d = plugin.data(p);
        Entity target = e.getEntity();
        Location eye = p.getEyeLocation();
        BoundingBox bb = target.getBoundingBox();

        // ---- Reach: distance from eyes to nearest point of the target's hitbox
        double cx = clamp(eye.getX(), bb.getMinX(), bb.getMaxX());
        double cy = clamp(eye.getY(), bb.getMinY(), bb.getMaxY());
        double cz = clamp(eye.getZ(), bb.getMinZ(), bb.getMaxZ());
        double dist = Math.sqrt(sq(eye.getX() - cx) + sq(eye.getY() - cy) + sq(eye.getZ() - cz));

        double range = 3.0;
        AttributeInstance attr = p.getAttribute(Attribute.ENTITY_INTERACTION_RANGE);
        if (attr != null) range = attr.getValue();
        double allowed = range + plugin.cfg("checks.reach.tolerance", 0.5);

        if (dist > allowed) {
            e.setCancelled(true);
            if (d.buffer("reach", 1.0, 3.0)) {
                plugin.flag(p, "reach", String.format("%.2f > %.2f", dist, allowed), 1.0);
            }
            return;
        } else {
            d.relax("reach", 0.2);
        }

        // ---- KillAura (angle): hitting something far away from where the crosshair points
        if (dist > 1.8) {
            Vector toTarget = bb.getCenter().subtract(eye.toVector());
            double angle = Math.toDegrees(eye.getDirection().angle(toTarget));
            if (angle > plugin.cfg("checks.killaura.max-angle", 95)) {
                e.setCancelled(true);
                if (d.buffer("ka-angle", 1.0, 2.0)) {
                    plugin.flag(p, "killaura", String.format("angle=%.0f", angle), 1.0);
                }
                return;
            } else {
                d.relax("ka-angle", 0.25);
            }
        }

        // ---- KillAura (multi-target): two different targets within one tick
        long now = System.currentTimeMillis();
        if (d.lastTarget != null && !d.lastTarget.equals(target.getUniqueId()) && now - d.lastHit < 35) {
            e.setCancelled(true);
            if (d.buffer("ka-multi", 1.0, 2.0)) {
                plugin.flag(p, "killaura", "multi-target", 1.0);
            }
        }
        d.lastTarget = target.getUniqueId();
        d.lastHit = now;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSwing(PlayerAnimationEvent e) {
        if (e.getAnimationType() != PlayerAnimationType.ARM_SWING) return;
        Player p = e.getPlayer();
        if (basicExempt(p)) return;

        PlayerData d = plugin.data(p);
        long now = System.currentTimeMillis();
        d.swings.addLast(now);
        while (!d.swings.isEmpty() && now - d.swings.peekFirst() > 1000) {
            d.swings.pollFirst();
        }
        if (d.swings.size() > plugin.cfg("checks.autoclicker.max-cps", 24)) {
            if (d.buffer("autoclicker", 1.0, 3.0)) {
                plugin.flag(p, "autoclicker", "cps=" + d.swings.size(), 1.0);
            }
        } else {
            d.relax("autoclicker", 0.1);
        }
    }

    // ------------------------------------------------------------------ player checks

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        if (basicExempt(p)) return;

        PlayerData d = plugin.data(p);
        long now = System.currentTimeMillis();
        d.places.addLast(now);
        while (!d.places.isEmpty() && now - d.places.peekFirst() > 1000) {
            d.places.pollFirst();
        }
        if (d.places.size() > plugin.cfg("checks.fastplace.max-blocks-per-second", 14)) {
            e.setCancelled(true);
            if (d.buffer("fastplace", 1.0, 3.0)) {
                plugin.flag(p, "fastplace", "blocks/s=" + d.places.size(), 1.0);
            }
        }
    }

    // ------------------------------------------------------------------ util

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private static double sq(double v) {
        return v * v;
    }
}
