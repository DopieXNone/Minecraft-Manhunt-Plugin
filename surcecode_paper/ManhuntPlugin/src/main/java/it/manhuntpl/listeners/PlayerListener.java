package it.manhuntpl.listeners;

import it.manhuntpl.managers.ManhuntManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public class PlayerListener implements Listener {
    private final ManhuntManager manager;

    public PlayerListener(ManhuntManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        manager.onSurvivorDeath(e);
        manager.onHunterDeath(e);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        manager.onRespawn(e);
    }
}
