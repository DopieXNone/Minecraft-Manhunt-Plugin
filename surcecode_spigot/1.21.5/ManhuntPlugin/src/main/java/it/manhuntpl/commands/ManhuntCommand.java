package it.manhuntpl.commands;

import it.manhuntpl.managers.ManhuntManager;
import it.manhuntpl.managers.ManhuntManager.Settings;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;
import java.util.stream.Collectors;

public class ManhuntCommand implements CommandExecutor, TabCompleter {
    private final ManhuntManager manager;

    public ManhuntCommand(ManhuntManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players may use this command.");
            return true;
        }
        Player p = (Player) sender;

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            showHelp(p);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "create"   -> handleCreate(p, args);
            case "start"    -> handleStart(p, args);
            case "reject"   -> handleReject(p, args);
            case "join"     -> handleJoin(p, args);
            case "allow"    -> handleAllow(p, args);
            case "leave"    -> handleLeave(p);
            case "compass"  -> handleCompass(p, args);
            case "settings" -> handleSettings(p, args);
            case "track"    -> handleTrack(p);
            default         -> p.sendMessage(ChatColor.RED + "Unknown subcommand. Use /manhunt help.");
        }
        return true;
    }

    private void showHelp(Player p) {
        p.sendMessage("");
        p.sendMessage(ChatColor.DARK_GRAY + "╔═════════════════════════════╗");
        p.sendMessage(ChatColor.DARK_GRAY + "║ " + ChatColor.AQUA + "     MANHUNT HELP MENU       " + ChatColor.DARK_GRAY + "║");
        p.sendMessage(ChatColor.DARK_GRAY + "╠═════════════════════════════╣");
        p.sendMessage(ChatColor.GOLD   + "/manhunt create <Name> <Survivor> <Hunter>");
        p.sendMessage(ChatColor.GRAY   + "    » Create a new manhunt");
        p.sendMessage("");
        p.sendMessage(ChatColor.GOLD   + "/manhunt start <Name>");
        p.sendMessage(ChatColor.GRAY   + "    » Start your pending manhunt");
        p.sendMessage("");
        p.sendMessage(ChatColor.GOLD   + "/manhunt reject <Name>");
        p.sendMessage(ChatColor.GRAY   + "    » Cancel your pending manhunt");
        p.sendMessage("");
        p.sendMessage(ChatColor.GOLD   + "/manhunt join <Name> <survivor|hunter>");
        p.sendMessage(ChatColor.GRAY   + "    » Request to join a manhunt");
        p.sendMessage("");
        p.sendMessage(ChatColor.GOLD   + "/manhunt allow <PlayerName>");
        p.sendMessage(ChatColor.GRAY   + "    » Approve a join request");
        p.sendMessage("");
        p.sendMessage(ChatColor.GOLD   + "/manhunt leave");
        p.sendMessage(ChatColor.GRAY   + "    » Leave your current manhunt");
        p.sendMessage("");
        p.sendMessage(ChatColor.GOLD   + "/manhunt compass follow <SurvivorName>");
        p.sendMessage(ChatColor.GRAY   + "    » Track a survivor with compass");
        p.sendMessage("");
        p.sendMessage(ChatColor.GOLD   + "/manhunt settings <Name>");
        p.sendMessage(ChatColor.GRAY   + "    » Open settings GUI for pending manhunt (host only)");
        p.sendMessage("");
        p.sendMessage(ChatColor.GOLD   + "/manhunt track");
        p.sendMessage(ChatColor.GRAY   + "    » Open the tracking GUI (hunters only)");
        p.sendMessage(ChatColor.DARK_GRAY + "╚═════════════════════════════╝");
    }

    private void handleCreate(Player p, String[] a) {
        if (a.length != 4) {
            p.sendMessage(ChatColor.RED + "Usage: /manhunt create <Name> <Survivor> <Hunter>");
            return;
        }
        manager.createManhunt(a[1], a[2], a[3], p.getName());
    }

    private void handleStart(Player p, String[] a) {
        if (a.length != 2) {
            p.sendMessage(ChatColor.RED + "Usage: /manhunt start <Name>");
            return;
        }
        manager.startManhunt(p, a[1]);
    }

    private void handleReject(Player p, String[] a) {
        if (a.length != 2) {
            p.sendMessage(ChatColor.RED + "Usage: /manhunt reject <Name>");
            return;
        }
        manager.rejectManhunt(p, a[1]);
    }

    private void handleJoin(Player p, String[] a) {
        if (a.length != 3) {
            p.sendMessage(ChatColor.RED + "Usage: /manhunt join <Name> <survivor|hunter>");
            return;
        }
        String role = a[2].toLowerCase();
        if (!role.equals("survivor") && !role.equals("hunter")) {
            p.sendMessage(ChatColor.RED + "The role must be 'survivor' or 'hunter'.");
            return;
        }
        manager.requestJoin(p, a[1], role);
    }

    private void handleAllow(Player p, String[] a) {
        if (a.length != 2) {
            p.sendMessage(ChatColor.RED + "Usage: /manhunt allow <PlayerName>");
            return;
        }
        manager.allowJoin(p, a[1]);
    }

    private void handleLeave(Player p) {
        manager.leaveManhunt(p);
    }

    private void handleCompass(Player p, String[] a) {
        if (a.length != 3 || !a[1].equalsIgnoreCase("follow")) {
            p.sendMessage(ChatColor.RED + "Usage: /manhunt compass follow <SurvivorName>");
            return;
        }
        manager.followTarget(p, a[2]);
    }

    private void handleSettings(Player p, String[] a) {
        if (a.length != 2) {
            p.sendMessage(ChatColor.RED + "Usage: /manhunt settings <ManhuntName>");
            return;
        }
        String mhName = a[1];
        // Deve esistere in pending
        if (!manager.isPendingManhunt(mhName)) {
            p.sendMessage(ChatColor.RED + "No pending manhunt found with that name.");
            return;
        }
        // Solo host può eseguire
        if (!manager.isPendingHost(mhName, p.getName())) {
            p.sendMessage(ChatColor.RED + "Only the host may open settings for this manhunt.");
            return;
        }

        // Carica da file e memorizza in memoria
        manager.loadSettings(mhName);

        // Apertura GUI per la manhunt specificata
        openSettingsGUI(p, mhName);
    }

    private void openSettingsGUI(Player p, String mhName) {
        Inventory gui = Bukkit.createInventory(null, 27, "Manhunt Settings: " + mhName);
        Settings s = manager.getSettings(mhName);

        // Tracking (slot 10)
        ItemStack compassItem = new ItemStack(Material.COMPASS);
        ItemMeta compassMeta = compassItem.getItemMeta();
        compassMeta.setDisplayName("Open Tracking GUI");
        compassItem.setItemMeta(compassMeta);
        gui.setItem(10, compassItem);

        // Stopwatch (slot 13)
        ItemStack clock = new ItemStack(Material.CLOCK);
        ItemMeta cm = clock.getItemMeta();
        cm.setDisplayName("Stopwatch: " + (s.stopwatch ? "§aON" : "§cOFF"));
        clock.setItemMeta(cm);
        gui.setItem(13, clock);

        // Advanced Settings (slot 16)
        ItemStack adv = new ItemStack(Material.OAK_SIGN);
        ItemMeta am = adv.getItemMeta();
        am.setDisplayName("Advanced Settings");
        adv.setItemMeta(am);
        gui.setItem(16, adv);

        p.openInventory(gui);
    }

    private void handleTrack(Player p) {
        var mh = manager.getPlayerManhunt(p.getName());
        if (mh == null || !mh.isHunter(p.getName())) {
            p.sendMessage(ChatColor.RED + "Only hunters may open this GUI.");
            return;
        }
        List<String> survivors = mh.getSurvivors();
        int size = ((survivors.size() - 1) / 9 + 1) * 9;
        Inventory gui = Bukkit.createInventory(null, size, "Manhunt Tracking");
        for (int i = 0; i < survivors.size(); i++) {
            String name = survivors.get(i);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(name));
            meta.setDisplayName(name);
            head.setItemMeta(meta);
            gui.setItem(i, head);
        }
        p.openInventory(gui);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!(sender instanceof Player)) return List.of();
        Player p = (Player) sender;

        switch (args.length) {
            case 1 -> {
                return List.of("help","create","start","reject","join","allow","leave","compass","settings","track")
                        .stream().filter(s -> s.startsWith(args[0].toLowerCase()))
                        .collect(Collectors.toList());
            }
            case 2 -> {
                if (args[0].equalsIgnoreCase("start") ||
                        args[0].equalsIgnoreCase("reject") ||
                        args[0].equalsIgnoreCase("join") ||
                        args[0].equalsIgnoreCase("settings")) {
                    return manager.getPendingNames().stream()
                            .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList());
                }
                if (args[0].equalsIgnoreCase("allow")) {
                    return manager.getJoinRequestsForHost(p.getName()).stream()
                            .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList());
                }
                if (args[0].equalsIgnoreCase("compass")) {
                    return List.of("follow").stream()
                            .filter(s2 -> s2.startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList());
                }
                return List.of();
            }
            case 3 -> {
                if (args[0].equalsIgnoreCase("join")) {
                    return List.of("survivor","hunter").stream()
                            .filter(r -> r.startsWith(args[2].toLowerCase()))
                            .collect(Collectors.toList());
                }
                if (args[0].equalsIgnoreCase("compass") && args[1].equalsIgnoreCase("follow")) {
                    var mh = manager.getPlayerManhunt(p.getName());
                    if (mh != null) {
                        return mh.getSurvivors().stream()
                                .filter(s2 -> s2.toLowerCase().startsWith(args[2].toLowerCase()))
                                .collect(Collectors.toList());
                    }
                }
                return List.of();
            }
            default -> List.of();
        }
        return List.of();
    }
}
