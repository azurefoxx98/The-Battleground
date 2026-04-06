package com.zbeve.battlegrounds;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.MathUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.permissions.HytalePermissions;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;

import javax.annotation.Nonnull;

// /ghosttest chance|spawn|placearmor
public class GhostTestCommand extends AbstractPlayerCommand {

    private final RequiredArg<String> actionArg;

    public GhostTestCommand() {
        super("ghosttest", "Ghost test utils");
        requirePermission(HytalePermissions.fromCommand("ghosttest"));
        actionArg = withRequiredArg("action", "chance <0-100> | spawn | placearmor", ArgTypes.GREEDY_STRING);
    }

    @Override protected boolean canGeneratePermission() { return true; }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef player, @Nonnull World world) {
        String raw = actionArg.get(ctx);
        if (raw == null) { help(ctx); return; }

        String[] parts = raw.trim().split("\\s+", 2);
        switch (parts[0].toLowerCase()) {
            case "chance" -> {
                if (parts.length < 2) {
                    reply(ctx, "#ffaa00", "Chance: %.0f%%. Usage: /ghosttest chance <0-100>",
                            GhostDeathPossessionSystem.getPossessionChance() * 100);
                    return;
                }
                try {
                    double v = Double.parseDouble(parts[1]);
                    if (v < 0 || v > 100) { reply(ctx, "#ff6666", "0-100 only"); return; }
                    GhostDeathPossessionSystem.setPossessionChance(v / 100.0);
                    reply(ctx, "#66ff66", "Possession chance -> %.0f%%", v);
                } catch (NumberFormatException e) {
                    reply(ctx, "#ff6666", "Not a number: %s", parts[1]);
                }
            }
            case "spawn" -> {
                TransformComponent tr = store.getComponent(ref, TransformComponent.getComponentType());
                HeadRotation hr = store.getComponent(ref, HeadRotation.getComponentType());
                if (tr == null || hr == null) return;

                Vector3d p = tr.getPosition().clone();
                float yaw = hr.getRotation().getYaw();
                double rad = Math.toRadians(yaw);
                p.add(-Math.sin(rad) * 3, 0, Math.cos(rad) * 3);

                var npc = NPCPlugin.get().spawnNPC(store, "BattlegroundGhostKnightNPC", null,
                        p, new Vector3f(0, yaw + 180, 0));
                reply(ctx, npc != null ? "#66ff66" : "#ff6666",
                        npc != null ? "Ghost spawned at [%.0f,%.0f,%.0f]" : "Spawn failed, is the role loaded?",
                        p.x, p.y, p.z);
            }
            case "placearmor" -> {
                TransformComponent tr = store.getComponent(ref, TransformComponent.getComponentType());
                HeadRotation hr = store.getComponent(ref, HeadRotation.getComponentType());
                if (tr == null || hr == null) return;

                Vector3d p = tr.getPosition();
                float yaw = hr.getRotation().getYaw();
                double rad = Math.toRadians(yaw);
                int bx = MathUtil.floor(p.x - Math.sin(rad) * 2);
                int by = MathUtil.floor(p.y);
                int bz = MathUtil.floor(p.z + Math.cos(rad) * 2);

                world.setBlock(bx, by, bz, "ScrapArmorSitting", 0);
                reply(ctx, "#66ff66", "Placed ScrapArmorSitting at [%d,%d,%d]", bx, by, bz);
            }
            default -> help(ctx);
        }
    }

    private void help(CommandContext ctx) {
        ctx.sendMessage(Message.raw(
                "/ghosttest chance <0-100>\n/ghosttest spawn\n/ghosttest placearmor"
        ).color("#ffaa00"));
    }

    private void reply(CommandContext ctx, String col, String fmt, Object... args) {
        ctx.sendMessage(Message.raw(String.format(fmt, args)).color(col));
    }
}
