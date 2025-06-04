package it.manhuntpl;

import it.manhuntpl.managers.ManhuntManager;
import it.manhuntpl.listeners.GUIListener;
import it.manhuntpl.listeners.PlayerListener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;

public class ManhuntPlugin extends JavaPlugin {
    private ManhuntManager manager;

    @Override
    public void onEnable() {
        // Salva il config.yml di default se non esiste
        saveDefaultConfig();

        // Crea la cartella principale del plugin (dataFolder)
        File dataFolder = getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        manager = new ManhuntManager(this);

        // Registra il comando /manhunt
        getCommand("manhunt").setExecutor(new it.manhuntpl.commands.ManhuntCommand(manager));
        getCommand("manhunt").setTabCompleter(new it.manhuntpl.commands.ManhuntCommand(manager));

        // Registra i listener
        getServer().getPluginManager().registerEvents(new PlayerListener(manager), this);
        getServer().getPluginManager().registerEvents(new GUIListener(manager), this);

        // Task per aggiornare le bussole ogni 5 tick
        new BukkitRunnable() {
            @Override
            public void run() {
                manager.updateAllCompasses();
            }
        }.runTaskTimer(this, 5L, 5L);

        getLogger().info("ManhuntPlugin enabled");
    }

    @Override
    public void onDisable() {
        getLogger().info("ManhuntPlugin disabled");
    }
}
