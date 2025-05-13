package it.manhuntpl.commands;

import it.manhuntpl.managers.ManhuntManager;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

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
            sender.sendMessage(ChatColor.RED + "Only players use this command.");
            return true;
        }
        Player p = (Player) sender;

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            showHelp(p);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "create" -> handleCreate(p, args);
            case "accept" -> handleAccept(p, args);
            case "reject" -> handleReject(p, args);
            case "join"   -> handleJoin(p, args);
            case "allow"  -> handleAllow(p, args);
            case "leave"  -> handleLeave(p);
            case "compass"-> handleCompass(p, args);
            default        -> p.sendMessage(ChatColor.RED + "unknown command. Usa /manhunt help.");
        }
        return true;
    }

    private void showHelp(Player p) {
        p.sendMessage("");
        p.sendMessage(ChatColor.DARK_GRAY + "╔═════════════════════════════╗");
        p.sendMessage(ChatColor.DARK_GRAY + "║ " + ChatColor.AQUA + "     M A N H U N T   H E L P     " + ChatColor.DARK_GRAY + "║");
        p.sendMessage(ChatColor.DARK_GRAY + "╠═════════════════════════════╣");
        p.sendMessage(ChatColor.GOLD   + " /manhunt create <Name> <Survivor> <Hunter>");
        p.sendMessage(ChatColor.GRAY   + "    » Create a manhunt");
        p.sendMessage("");
        p.sendMessage(ChatColor.GOLD   + " /manhunt accept <Name>");
        p.sendMessage(ChatColor.GRAY   + "    » Starts the manhunt as host");
        p.sendMessage("");
        p.sendMessage(ChatColor.GOLD   + " /manhunt reject <Name>");
        p.sendMessage(ChatColor.GRAY   + "    » Cancels the creation");
        p.sendMessage("");
        p.sendMessage(ChatColor.GOLD   + " /manhunt join <Name> <survivor|hunter>");
        p.sendMessage(ChatColor.GRAY   + "    » Asks to join (host must perform: /manhunt allow)");
        p.sendMessage("");
        p.sendMessage(ChatColor.GOLD   + " /manhunt allow <PlayerName>");
        p.sendMessage(ChatColor.GRAY   + "    » Host approves a join request");
        p.sendMessage("");
        p.sendMessage(ChatColor.GOLD   + " /manhunt leave");
        p.sendMessage(ChatColor.GRAY   + "    » Leave the manhunt");
        p.sendMessage("");
        p.sendMessage(ChatColor.GOLD   + " /manhunt compass follow <SurvivorName>");
        p.sendMessage(ChatColor.GRAY   + "    » Sets the tracking compass");
        p.sendMessage(ChatColor.DARK_GRAY + "╚═════════════════════════════╝");
    }

    private void handleCreate(Player p, String[] a) {
        if (a.length != 4) {
            p.sendMessage(ChatColor.RED + "Usage: /manhunt create <Name> <Survivor> <Hunter>");
            return;
        }
        manager.createManhunt(a[1], a[2], a[3], p.getName());
    }

    private void handleAccept(Player p, String[] a) {
        if (a.length != 2) {
            p.sendMessage(ChatColor.RED + "Usage: /manhunt accept <Name>");
            return;
        }
        manager.acceptManhunt(p, a[1]);
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

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!(sender instanceof Player)) return List.of();
        Player p = (Player) sender;

        switch (args.length) {
            case 1 -> {
                return List.of("help","create","accept","reject","join","allow","leave","compass").stream()
                        .filter(s -> s.startsWith(args[0].toLowerCase()))
                        .collect(Collectors.toList());
            }
            case 2 -> {
                if (args[0].equalsIgnoreCase("accept") ||
                        args[0].equalsIgnoreCase("reject") ||
                        args[0].equalsIgnoreCase("join")) {
                    return manager.getAllPendingOrActiveNames().stream()
                            .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList());
                }
                if (args[0].equalsIgnoreCase("allow")) {
                    // suggerisci giocatori in attesa di approvazione per le manhunt di cui p è host
                    return manager.getJoinRequestsForHost(p.getName()).stream()
                            .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList());
                }
                if (args[0].equalsIgnoreCase("compass")) {
                    return List.of("follow").stream()
                            .filter(s -> s.startsWith(args[1].toLowerCase()))
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
                                .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                                .collect(Collectors.toList());
                    }
                }
                return List.of();
            }
            default -> { return List.of(); }
        }
    }
}
