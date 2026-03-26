package com.hank.thebattleground;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public class PluginStartup extends JavaPlugin {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public PluginStartup(JavaPluginInit init) {
        super(init);
        LOGGER.atInfo().log("%s version %s Loaded!", this.getName(), this.getManifest().getVersion().toString());
    }

    @Override
    protected void setup() {


    }
}
