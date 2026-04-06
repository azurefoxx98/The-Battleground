package com.zbeve.battlegrounds;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.MathUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.Direction;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.modules.entity.DespawnComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems;
import com.hypixel.hytale.server.core.modules.projectile.ProjectileModule;
import com.hypixel.hytale.server.core.modules.projectile.config.ProjectileConfig;
import com.hypixel.hytale.server.core.modules.projectile.config.StandardPhysicsConfig;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.protocol.RotationMode;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

import javax.annotation.Nonnull;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * When a Ghost Knight dies, there's a chance it possesses the nearest
 * ScrapArmorSitting block and spawns an Animated Armor in its place.
 * A spirit orb arcs from the ghost to the armor before the spawn.
 */
public class GhostDeathPossessionSystem extends DeathSystems.OnDeathSystem {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();
    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "ghost-possession");
        t.setDaemon(true);
        return t;
    });

    private static volatile double possessionChance = 0.15;
    private static final int RADIUS = 20;
    private static final double TRAVEL_TIME = 2.5; // seconds — slow ghostly arc
    private static final double ARC_GRAVITY = 8.0;  // gentle floaty arc
    private static final double WISP_OFFSET = 1.5;  // blocks — how far apart lateral wisps start

    // prevents two ghosts from targeting the same armor pile
    private static final Set<String> claimedArmor = ConcurrentHashMap.newKeySet();

    private int ghostRole = -1;
    private int scrapBlockId = Integer.MIN_VALUE;

    static void setPossessionChance(double c) { possessionChance = c; }
    static double getPossessionChance() { return possessionChance; }

    @Nonnull @Override
    public Query<EntityStore> getQuery() {
        return Query.<EntityStore>and(NPCEntity.getComponentType(), TransformComponent.getComponentType());
    }

    // subclass StandardPhysicsConfig to set protected arc physics
    private static class OrbPhysics extends StandardPhysicsConfig {
        OrbPhysics() {
            this.gravity = ARC_GRAVITY;
            this.terminalVelocityAir = 80;
            this.rotationMode = RotationMode.Velocity;
        }
    }

    // borrow interactions from an existing config so the Interactions component accepts it
    private static class OrbConfig extends ProjectileConfig {
        OrbConfig(ProjectileConfig base, double force) {
            this.model = "Skeleton_Mage_Corruption_Orb";
            this.launchForce = force;
            this.interactions = base.getInteractions();
            this.physicsConfig = new OrbPhysics();
            this.spawnOffset = new com.hypixel.hytale.protocol.Vector3f(0, 0, 0);
            this.spawnRotationOffset = new Direction(0, 0, 0);
        }
    }

    @Override
    public void onComponentAdded(@Nonnull Ref<EntityStore> ref, @Nonnull DeathComponent component,
                                 @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> buf) {
        if (ghostRole < 0) {
            ghostRole = NPCPlugin.get().getIndex("BattlegroundGhostKnightNPC");
            if (ghostRole < 0) return;
        }
        if (scrapBlockId == Integer.MIN_VALUE) {
            scrapBlockId = BlockType.getAssetMap().getIndex("ScrapArmorSitting");
            if (scrapBlockId == Integer.MIN_VALUE) return;
        }

        NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
        if (npc == null || npc.getRoleIndex() != ghostRole) return;

        if (ThreadLocalRandom.current().nextDouble() >= possessionChance) return;

        TransformComponent t = store.getComponent(ref, TransformComponent.getComponentType());
        if (t == null) return;

        Vector3d ghostPos = t.getPosition();
        World world = ((EntityStore) store.getExternalData()).getWorld();
        int ox = MathUtil.floor(ghostPos.x), oy = MathUtil.floor(ghostPos.y), oz = MathUtil.floor(ghostPos.z);

        // find closest ScrapArmorSitting within radius
        int bx = 0, by = 0, bz = 0;
        double closest = Double.MAX_VALUE;
        boolean found = false;

        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dy = -RADIUS; dy <= RADIUS; dy++) {
                for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                    int cy = oy + dy;
                    if (cy < 0 || cy >= 320) continue;
                    if (world.getBlock(ox + dx, cy, oz + dz) == scrapBlockId) {
                        String key = (ox + dx) + "," + cy + "," + (oz + dz);
                        if (claimedArmor.contains(key)) continue;
                        double d = dx * dx + dy * dy + dz * dz;
                        if (d < closest) {
                            closest = d;
                            bx = ox + dx; by = cy; bz = oz + dz;
                            found = true;
                        }
                    }
                }
            }
        }
        if (!found) return;

        // claim this armor pile so other ghosts pick a different one
        String claimKey = bx + "," + by + "," + bz;
        if (!claimedArmor.add(claimKey)) return;

        // launch pos = ghost chest height, target = center of armor block
        double sx = ghostPos.x, sy = ghostPos.y + 1.0, sz = ghostPos.z;
        double tx = bx + 0.5, ty = by + 0.5, tz = bz + 0.5;

        // compute launch velocity for parabolic arc arriving in TRAVEL_TIME seconds
        // horizontal speed = flat distance / T
        double hdx = tx - sx, hdz = tz - sz;
        double hDist = Math.sqrt(hdx * hdx + hdz * hdz);
        double vh = hDist / TRAVEL_TIME;

        // vertical launch speed: vy = (Δy + 0.5*g*T²) / T
        double dy = ty - sy;
        double vv = (dy + 0.5 * ARC_GRAVITY * TRAVEL_TIME * TRAVEL_TIME) / TRAVEL_TIME;

        double totalSpeed = Math.sqrt(vh * vh + vv * vv);
        if (totalSpeed < 0.1) totalSpeed = 0.1;

        // perpendicular direction in XZ plane for lateral spirit wisps
        double perpX = hDist > 0.01 ? -hdz / hDist : 1;
        double perpZ = hDist > 0.01 ? hdx / hDist : 0;

        // load the necromancer orb config for its model + interactions, override physics
        try {
            ProjectileConfig base = (ProjectileConfig) ProjectileConfig.getAssetMap()
                    .getAsset("Endgame_Necromancer_Void_Orb");
            if (base == null) base = (ProjectileConfig) ProjectileConfig.getAssetMap()
                    .getAsset("Projectile_Config_Fireball");
            if (base != null) {
                TimeResource time = buf.getResource(TimeResource.getResourceType());

                // main orb — exact arc from ghost to target
                launchConvergingOrb(buf, base, time, sx, sy, sz, tx, ty, tz);
                // left wisp — starts offset left and higher, converges on target
                launchConvergingOrb(buf, base, time,
                        sx + perpX * WISP_OFFSET, sy + 0.5, sz + perpZ * WISP_OFFSET, tx, ty, tz);
                // right wisp — starts offset right, converges on target
                launchConvergingOrb(buf, base, time,
                        sx - perpX * WISP_OFFSET, sy - 0.3, sz - perpZ * WISP_OFFSET, tx, ty, tz);
            }
        } catch (Throwable e) {
            ((HytaleLogger.Api) LOGGER.atWarning()).log("Orb spawn failed: %s", e.getMessage());
        }

        // fire pillar + NPC spawn after orbs arrive
        long delayMs = (long) (TRAVEL_TIME * 1000);
        final int fx = bx, fy = by, fz = bz;
        SCHEDULER.schedule(() -> {
            try {
                world.execute(() -> {
                    try {
                        // tall fire pillar — the armor erupts in flame
                        Vector3d base2 = new Vector3d(fx + 0.5, fy, fz + 0.5);
                        ParticleUtil.spawnParticleEffect("Explosion_Medium", base2, store);
                        for (double h = 0.0; h <= 3.0; h += 0.7) {
                            ParticleUtil.spawnParticleEffect("Impact_Fire",
                                    new Vector3d(fx + 0.5, fy + h, fz + 0.5), store);
                        }
                        ParticleUtil.spawnParticleEffect("Explosion_Medium",
                                new Vector3d(fx + 0.5, fy + 1.5, fz + 0.5), store);
                        ParticleUtil.spawnParticleEffect("GreenOrbImpact", base2, store);

                        // fire explosion sound
                        int sfx = SoundEvent.getAssetMap().getIndex("SFX_Fireball_Death");
                        SoundUtil.playSoundEvent3d(sfx, SoundCategory.SFX, base2, store);

                        world.breakBlock(fx, fy, fz, 0);
                    } finally {
                        claimedArmor.remove(claimKey);
                    }
                });
                // spawn the animated armor slightly after the fire erupts
                SCHEDULER.schedule(() -> {
                    try {
                        world.execute(() -> {
                            NPCPlugin.get().spawnNPC(store, "BattlegroundAnimatedArmorNPC", null,
                                    new Vector3d(fx + 0.5, fy, fz + 0.5), new Vector3f(0, 0, 0));
                            ((HytaleLogger.Api) LOGGER.atInfo()).log(
                                    "Ghost possessed armor at [%d,%d,%d]", fx, fy, fz);
                        });
                    } catch (Exception ignored) {}
                }, 350, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                claimedArmor.remove(claimKey);
            }
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Computes velocity for a parabolic arc from (sx,sy,sz) to (tx,ty,tz)
     * arriving in TRAVEL_TIME seconds, spawns the orb, and sets its despawn.
     */
    private void launchConvergingOrb(CommandBuffer<EntityStore> buf, ProjectileConfig base,
                                     TimeResource time,
                                     double sx, double sy, double sz,
                                     double tx, double ty, double tz) {
        double dx = tx - sx, dz = tz - sz;
        double hd = Math.sqrt(dx * dx + dz * dz);
        double vH = hd / TRAVEL_TIME;
        double vV = ((ty - sy) + 0.5 * ARC_GRAVITY * TRAVEL_TIME * TRAVEL_TIME) / TRAVEL_TIME;
        double speed = Math.sqrt(vH * vH + vV * vV);
        if (speed < 0.1) speed = 0.1;

        double dirX = hd > 0.01 ? dx / hd * vH : 0;
        double dirZ = hd > 0.01 ? dz / hd * vH : 0;

        OrbConfig cfg = new OrbConfig(base, speed);
        Ref<EntityStore> ref = ProjectileModule.get().spawnProjectile(
                null, null, buf, cfg, new Vector3d(sx, sy, sz), new Vector3d(dirX, vV, dirZ));
        if (ref != null) {
            buf.putComponent(ref, DespawnComponent.getComponentType(),
                    DespawnComponent.despawnInSeconds(time, (float) TRAVEL_TIME));
        }
    }
}
