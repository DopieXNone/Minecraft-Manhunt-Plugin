package it.manhuntpl.listeners;

import it.manhuntpl.managers.ManhuntManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
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

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        // Ripristina progressione nella manhunt dopo disconnessione
        manager.handleReconnect(e.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        if (manager.getPlayerManhunt(p.getName()) == null) {
            manager.clearEffects(p);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (manager.getPlayerManhunt(p.getName()) == null) {
            manager.clearEffects(p);
        }
    }
}
