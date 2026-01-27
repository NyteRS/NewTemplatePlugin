package com.example.exampleplugin.spawner;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.command.system.CommandManager;
import com.hypixel.hytale.server.core.console.ConsoleSender;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;

/**
 * Small SpawnStrategy that executes a server command template to spawn mobs.
 *
 * Template placeholders:
 *  - %x, %y, %z  -> integer block coordinates
 *  - %player      -> triggering player's username (if available)
 *
 * This class intentionally keeps fallbacks simple: prefer console execution, fall back to the triggering Player
 * if console execution fails.
 */
public final class CommandSpawnStrategy implements ProximitySpawnSystem.SpawnStrategy {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private final String commandTemplate;
    private final boolean runAsConsole;

    /**
     * @param commandTemplate command template, e.g. "spawnmob zombie %x %y %z"
     * @param runAsConsole if true, attempt to run command as console (ConsoleSender.INSTANCE)
     */
    public CommandSpawnStrategy(String commandTemplate, boolean runAsConsole) {
        this.commandTemplate = commandTemplate;
        this.runAsConsole = runAsConsole;
    }

    @Override
    public void spawn(com.hypixel.hytale.server.core.universe.world.World world, double x, double y, double z, PlayerRef trigger, Player triggerPlayer) {
        try {
            String playerName = "";
            try {
                if (triggerPlayer != null) {
                    playerName = triggerPlayer.getDisplayName();
                } else if (trigger != null) {
                    playerName = trigger.getUsername();
                }
            } catch (Throwable ignored) {
                playerName = (trigger != null) ? trigger.getUsername() : "";
            }

            String cmd = commandTemplate
                    .replace("%x", String.valueOf((int) Math.floor(x)))
                    .replace("%y", String.valueOf((int) Math.floor(y)))
                    .replace("%z", String.valueOf((int) Math.floor(z)))
                    .replace("%player", playerName);

            LOG.atInfo().log("[CommandSpawn] executing command: %s (asConsole=%s)", cmd, runAsConsole);

            if (runAsConsole) {
                try {
                    CommandManager.get().handleCommand(ConsoleSender.INSTANCE, cmd);
                    return;
                } catch (Throwable t) {
                    LOG.atWarning().withCause(t).log("[CommandSpawn] execute as console failed, falling back to player");
                }
            }

            // Try as player if available; otherwise finally try console as last resort
            if (triggerPlayer != null) {
                try {
                    CommandManager.get().handleCommand(triggerPlayer, cmd);
                    return;
                } catch (Throwable t) {
                    LOG.atWarning().withCause(t).log("[CommandSpawn] execute as player failed, falling back to console");
                }
            }

            // Last resort: console
            try {
                CommandManager.get().handleCommand(ConsoleSender.INSTANCE, cmd);
            } catch (Throwable t) {
                LOG.atWarning().withCause(t).log("[CommandSpawn] execute fallback as console failed");
            }
        } catch (Throwable t) {
            LOG.atWarning().withCause(t).log("[CommandSpawn] unexpected error while attempting to run spawn command");
        }
    }
}