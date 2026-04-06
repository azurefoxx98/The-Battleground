package com.zbeve.battlegrounds;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

import javax.annotation.Nonnull;

public class BattlegroundsPlugin extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();

    public BattlegroundsPlugin(@Nonnull JavaPluginInit init) { super(init); }

    @Override
    protected void setup() {
        getEntityStoreRegistry().registerSystem(new GhostDeathPossessionSystem());
        getCommandRegistry().registerCommand(new GhostTestCommand());
        ((HytaleLogger.Api) LOGGER.atInfo()).log("Ghost possession system active");
    }

    @Override protected void shutdown() {}
}
