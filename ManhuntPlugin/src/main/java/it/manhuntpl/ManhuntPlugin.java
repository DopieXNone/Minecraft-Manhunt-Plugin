package it.manhuntpl;

import it.manhuntpl.commands.ManhuntCommand;
import it.manhuntpl.listeners.PlayerListener;
import it.manhuntpl.managers.ManhuntManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class ManhuntPlugin extends JavaPlugin {
    private ManhuntManager manager;

    @Override
    public void onEnable() {
        manager = new ManhuntManager(this);

        // Comando /manhunt
        getCommand("manhunt").setExecutor(new ManhuntCommand(manager));
        getCommand("manhunt").setTabCompleter(new ManhuntCommand(manager));

        // Listener per morte/respawn
        getServer().getPluginManager().registerEvents(
                new PlayerListener(manager), this);

        // Task che ogni 5 tick aggiorna le bussole dei cacciatori
        new BukkitRunnable() {
            @Override
            public void run() {
                manager.updateAllCompasses();
            }
        }.runTaskTimer(this, 5L, 5L);

        getLogger().info("ManhuntPlugin abilitato");
    }

    @Override
    public void onDisable() {
        getLogger().info("ManhuntPlugin disabilitato");
    }
}
