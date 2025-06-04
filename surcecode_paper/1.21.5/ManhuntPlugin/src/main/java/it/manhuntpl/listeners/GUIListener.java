package it.manhuntpl.listeners;

import it.manhuntpl.managers.ManhuntManager;
import it.manhuntpl.managers.ManhuntManager.Settings;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.Inventory;

public class GUIListener implements Listener {
    private final ManhuntManager manager;

    public GUIListener(ManhuntManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        String title = e.getView().getTitle();
        Player p = (Player) e.getWhoClicked();

        // GUI principale “Manhunt Settings: <Name>”
        if (title.startsWith("Manhunt Settings: ")) {
            e.setCancelled(true);
            String mhName = title.split(": ", 2)[1];
            // solo host di pending
            if (!manager.isPendingHost(mhName, p.getName())) {
                p.sendMessage(ChatColor.RED + "Only the host may modify these settings.");
                return;
            }

            Settings s = manager.getSettings(mhName);
            int slot = e.getRawSlot();

            switch (slot) {
                case 10 -> {
                    // Apri GUI di tracking
                    p.performCommand("manhunt track");
                    p.closeInventory();
                }
                case 13 -> {
                    // Toggle Stopwatch
                    s.stopwatch = !s.stopwatch;
                    ItemStack clock = e.getInventory().getItem(13);
                    ItemMeta cm = clock.getItemMeta();
                    cm.setDisplayName("Stopwatch: " + (s.stopwatch ? "§aON" : "§cOFF"));
                    clock.setItemMeta(cm);
                    e.getInventory().setItem(13, clock);
                    p.sendMessage(ChatColor.GREEN + "Stopwatch " + (s.stopwatch ? "enabled" : "disabled") + ".");
                    manager.saveSettings(mhName);
                }
                case 16 -> {
                    // Apri GUI “Manhunt Advanced Settings”
                    openAdvancedSettings(p, mhName);
                }
            }
        }

        // GUI “Manhunt Tracking”
        if (title.equals("Manhunt Tracking")) {
            e.setCancelled(true);
            ItemStack clicked = e.getCurrentItem();
            if (clicked == null) return;
            String target = clicked.getItemMeta().getDisplayName();
            p.performCommand("manhunt compass follow " + target);
            p.closeInventory();
        }

        // GUI “Manhunt Advanced Settings: <Name>”
        if (title.startsWith("Manhunt Advanced Settings: ")) {
            e.setCancelled(true);
            String mhName = title.split(": ", 2)[1];
            Settings s = manager.getSettings(mhName);
            int slot = e.getRawSlot();

            switch (slot) {
                case 11 -> {
                    // toggle Initial Countdown
                    s.countdown = !s.countdown;
                    ItemStack countdown = e.getInventory().getItem(11);
                    ItemMeta counM = countdown.getItemMeta();
                    counM.setDisplayName("Initial Countdown: " + (s.countdown ? "§aON" : "§cOFF"));
                    countdown.setItemMeta(counM);
                    e.getInventory().setItem(11, countdown);
                    p.sendMessage(ChatColor.GREEN + "Initial Countdown " + (s.countdown ? "enabled" : "disabled") + ".");
                    manager.saveSettings(mhName);
                }
                case 13 -> {
                    // toggle Glow Survivors
                    s.glowSurvivors = !s.glowSurvivors;
                    ItemStack glow = e.getInventory().getItem(13);
                    ItemMeta glowM = glow.getItemMeta();
                    glowM.setDisplayName("Glow Survivors: " + (s.glowSurvivors ? "§aON" : "§cOFF"));
                    glow.setItemMeta(glowM);
                    e.getInventory().setItem(13, glow);
                    p.sendMessage(ChatColor.GREEN + "Glow Survivors " + (s.glowSurvivors ? "enabled" : "disabled") + ".");
                    manager.saveSettings(mhName);
                }
                case 15 -> {
                    // toggle Initial Invincibility
                    s.initialInvincibility = !s.initialInvincibility;
                    ItemStack inv = e.getInventory().getItem(15);
                    ItemMeta invM = inv.getItemMeta();
                    invM.setDisplayName("Initial Invincibility: " + (s.initialInvincibility ? "§aON" : "§cOFF"));
                    inv.setItemMeta(invM);
                    e.getInventory().setItem(15, inv);
                    p.sendMessage(ChatColor.GREEN + "Initial Invincibility " + (s.initialInvincibility ? "enabled" : "disabled") + ".");
                    manager.saveSettings(mhName);
                }
            }
        }
    }

    /** Apre la GUI con le opzioni avanzate per una data manhunt */
    private void openAdvancedSettings(Player p, String mhName) {
        Inventory gui = Bukkit.createInventory(null, 27, "Manhunt Advanced Settings: " + mhName);
        Settings s = manager.getSettings(mhName);

        // Initial Countdown (slot 11)
        ItemStack countdown = new ItemStack(Material.NETHER_STAR);
        ItemMeta counM = countdown.getItemMeta();
        counM.setDisplayName("Initial Countdown: " + (s.countdown ? "§aON" : "§cOFF"));
        countdown.setItemMeta(counM);
        gui.setItem(11, countdown);

        // Glow Survivors (slot 13)
        ItemStack glow = new ItemStack(Material.GLOWSTONE_DUST);
        ItemMeta glowM = glow.getItemMeta();
        glowM.setDisplayName("Glow Survivors: " + (s.glowSurvivors ? "§aON" : "§cOFF"));
        glow.setItemMeta(glowM);
        gui.setItem(13, glow);

        // Initial Invincibility (slot 15)
        ItemStack inv = new ItemStack(Material.SHIELD);
        ItemMeta invM = inv.getItemMeta();
        invM.setDisplayName("Initial Invincibility: " + (s.initialInvincibility ? "§aON" : "§cOFF"));
        inv.setItemMeta(invM);
        gui.setItem(15, inv);

        p.openInventory(gui);
    }
}
