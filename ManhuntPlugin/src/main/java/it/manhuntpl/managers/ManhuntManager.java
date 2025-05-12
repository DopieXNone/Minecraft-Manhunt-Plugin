package it.manhuntpl.managers;

import it.manhuntpl.ManhuntPlugin;
import it.manhuntpl.models.Manhunt;
import it.manhuntpl.models.ManhuntCode;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ManhuntManager {
    private final ManhuntPlugin plugin;
    private final Map<String, Manhunt> manhunts = new ConcurrentHashMap<>();
    private final Map<String, ManhuntCode> pending = new ConcurrentHashMap<>();
    private final Map<String, List<Request>> joinRequests = new ConcurrentHashMap<>();
    private final Map<String, String> tracked = new ConcurrentHashMap<>();

    public ManhuntManager(ManhuntPlugin plugin) {
        this.plugin = plugin;
    }

    private static class Request {
        final String player;
        final String role;
        Request(String player, String role) {
            this.player = player;
            this.role = role;
        }
    }

    /** Crea (in pending) una nuova manhunt con 1 survivor e 1 hunter */
    public void createManhunt(String name, String survivor, String hunter, String host) {
        if (pending.containsKey(name) || manhunts.containsKey(name)) {
            send(host, ChatColor.RED + "Nome manhunt già in uso!");
            return;
        }
        pending.put(name, new ManhuntCode(name, survivor, hunter, host));
        send(host, ChatColor.GREEN +
                "Manhunt '" + name +
                "' creata. Host: conferma con /manhunt accept " + name);
    }

    /** L’host conferma e parte la manhunt */
    public void acceptManhunt(Player p, String name) {
        ManhuntCode code = pending.get(name);
        if (code == null || !code.getHost().equals(p.getName())) {
            send(p, ChatColor.RED +
                    "Nessuna manhunt da accettare col tuo nome!");
            return;
        }
        pending.remove(name);

        // Crea l’oggetto Manhunt con host
        Manhunt mh = new Manhunt(name, code.getSurvivors(), code.getHunters(), code.getHost());

        // Popola le collezioni attive e dai la bussola agli hunter
        for (String surv : code.getSurvivors()) mh.addSurvivor(surv);
        for (String hunt : code.getHunters()) {
            mh.addHunter(hunt);
            giveCompass(hunt);
        }

        manhunts.put(name, mh);
        broadcast(ChatColor.GOLD + "Manhunt '" + name + "' avviata!");
    }

    /** L’host annulla la creazione */
    public void rejectManhunt(Player p, String name) {
        ManhuntCode code = pending.get(name);
        if (code != null && code.getHost().equals(p.getName())) {
            pending.remove(name);
            send(p, ChatColor.YELLOW +
                    "Manhunt '" + name + "' annullata.");
        } else {
            send(p, ChatColor.RED +
                    "Nessuna manhunt da rifiutare col tuo nome!");
        }
    }

    /**
     * Un giocatore richiede di unirsi:
     * - se la manhunt è in pending, arruola direttamente in code
     * - se è attiva, genera una richiesta che l’host dovrà approvare
     */
    public void requestJoin(Player p, String name, String role) {
        if (pending.containsKey(name)) {
            ManhuntCode code = pending.get(name);
            if (role.equals("survivor")) code.addSurvivor(p.getName());
            else code.addHunter(p.getName());
            send(p, ChatColor.GREEN +
                    "Richiesta join pre-accept ricevuta. Host: /manhunt accept " + name);
            return;
        }
        if (!manhunts.containsKey(name)) {
            send(p, ChatColor.RED +
                    "Nessuna manhunt attiva o in creazione con quel nome!");
            return;
        }
        joinRequests
                .computeIfAbsent(name, k -> new ArrayList<>())
                .add(new Request(p.getName(), role));
        send(p, ChatColor.GREEN +
                "Richiesta di unirti come " + role +
                " inviata all'host di '" + name + "'.");
    }

    /** Host approva una richiesta di join attiva */
    public void allowJoin(Player host, String targetName) {
        // Trova la manhunt di cui è host (prima active, poi pending as fallback)
        String mhName = manhunts.entrySet().stream()
                .filter(e -> e.getValue().getHost().equals(host.getName()))
                .map(Map.Entry::getKey)
                .findFirst().orElse(null);
        if (mhName == null) mhName = pending.entrySet().stream()
                .filter(e -> e.getValue().getHost().equals(host.getName()))
                .map(Map.Entry::getKey)
                .findFirst().orElse(null);
        if (mhName == null) {
            send(host, ChatColor.RED + "Non sei host di nessuna manhunt!");
            return;
        }

        List<Request> reqs = joinRequests.getOrDefault(mhName, Collections.emptyList());
        Optional<Request> reqOpt = reqs.stream()
                .filter(r -> r.player.equalsIgnoreCase(targetName))
                .findFirst();
        if (reqOpt.isEmpty()) {
            send(host, ChatColor.RED + "Nessuna richiesta di join da " + targetName);
            return;
        }
        Request req = reqOpt.get();
        // Se la manhunt è ancora pending -> aggiungi al code, altrimenti al model
        if (pending.containsKey(mhName)) {
            ManhuntCode code = pending.get(mhName);
            if (req.role.equals("survivor")) code.addSurvivor(req.player);
            else code.addHunter(req.player);
        } else {
            Manhunt mh = manhunts.get(mhName);
            if (req.role.equals("survivor")) mh.addSurvivor(req.player);
            else {
                mh.addHunter(req.player);
                giveCompass(req.player);
            }
        }
        send(host, ChatColor.GOLD + "Richiesta di " + req.player + " approvata.");
        Player target = Bukkit.getPlayer(req.player);
        if (target != null && target.isOnline()) {
            send(target, ChatColor.GREEN + "Sei stato aggiunto a '" + mhName +
                    "' come " + req.role + "!");
        }
        reqs.remove(req);
    }

    /** Fornisce i nomi in attesa per tab-complete su /manhunt allow */
    public List<String> getJoinRequestsForHost(String host) {
        String mhName = manhunts.entrySet().stream()
                .filter(e -> e.getValue().getHost().equals(host))
                .map(Map.Entry::getKey)
                .findFirst().orElse(null);
        if (mhName == null) mhName = pending.entrySet().stream()
                .filter(e -> e.getValue().getHost().equals(host))
                .map(Map.Entry::getKey)
                .findFirst().orElse(null);
        if (mhName == null) return List.of();
        return joinRequests.getOrDefault(mhName, List.of()).stream()
                .map(r -> r.player)
                .toList();
    }

    /** Permette a un player di abbandonare la manhunt in cui è */
    public void leaveManhunt(Player p) {
        String name = p.getName();
        Manhunt mh = getPlayerManhunt(name);
        if (mh == null) {
            send(p, ChatColor.RED + "Non sei in nessuna manhunt!");
            return;
        }
        boolean wasHunter = mh.getHunters().remove(name);
        mh.getSurvivors().remove(name);
        if (wasHunter) {
            p.getInventory().remove(Material.COMPASS);
            tracked.remove(name);
        }
        send(p, ChatColor.YELLOW + "Hai abbandonato la manhunt '" + mh.getName() + "'.");

        // Se non rimane né survivor né hunter, termina
        if (mh.getSurvivors().isEmpty() || mh.getHunters().isEmpty()) {
            manhunts.remove(mh.getName());
            broadcast(ChatColor.RED +
                    "Manhunt '" + mh.getName() + "' terminata (giocatori insufficienti).");
            // Pulisci compass residue
            for (String h : mh.getHunters()) {
                Player ph = Bukkit.getPlayer(h);
                if (ph != null && ph.isOnline()) ph.getInventory().remove(Material.COMPASS);
                tracked.remove(h);
            }
        }
    }

    /** Imposta il target da tracciare */
    public void followTarget(Player p, String survivor) {
        Manhunt mh = getPlayerManhunt(p.getName());
        if (mh == null || !mh.getHunters().contains(p.getName())) {
            send(p, ChatColor.RED + "Non sei hunter in alcuna manhunt!");
            return;
        }
        if (!mh.getSurvivors().contains(survivor)) {
            send(p, ChatColor.RED + survivor + " non è survivor in questa manhunt!");
            return;
        }
        tracked.put(p.getName(), survivor);
        send(p, ChatColor.GREEN + "Compass ora traccia " + survivor);
    }

    /** Task chiamato ogni 5 tick per aggiornare tutte le bussole */
    public void updateAllCompasses() {
        for (var e : tracked.entrySet()) {
            Player hunter = Bukkit.getPlayer(e.getKey());
            Player target = Bukkit.getPlayer(e.getValue());
            if (hunter == null || target == null) continue;
            if (!hunter.isOnline() || !target.isOnline()) continue;
            if (!hunter.getWorld().equals(target.getWorld())) continue;
            for (ItemStack item : hunter.getInventory().getContents()) {
                if (item != null && item.getType() == Material.COMPASS) {
                    CompassMeta meta = (CompassMeta) item.getItemMeta();
                    if (meta != null) {
                        meta.setLodestone(target.getLocation());
                        meta.setLodestoneTracked(false);
                        item.setItemMeta(meta);
                    }
                }
            }
        }
    }

    /** Quando un survivor muore, termina la manhunt e rimuove bussola/tracking */
    public void onSurvivorDeath(PlayerDeathEvent e) {
        String died = e.getEntity().getName();
        Optional<Map.Entry<String, Manhunt>> ended = manhunts.entrySet().stream()
                .filter(ent -> ent.getValue().getSurvivors().contains(died))
                .findFirst();
        if (ended.isPresent()) {
            String nm = ended.get().getKey();
            Manhunt mh = manhunts.remove(nm);
            broadcast(ChatColor.RED +
                    "Survivor " + died + " è morto: MANHUNT '" + nm + "' TERMINATA!");
            for (String h : mh.getHunters()) {
                Player ph = Bukkit.getPlayer(h);
                if (ph != null && ph.isOnline()) ph.getInventory().remove(Material.COMPASS);
                tracked.remove(h);
            }
        }
    }

    /** Quando un hunter muore, prepara ricompensa di bussola al respawn */
    public void onHunterDeath(PlayerDeathEvent e) {
        String died = e.getEntity().getName();
        Manhunt mh = getPlayerManhunt(died);
        if (mh != null && mh.isHunter(died)) {
            // Rimuoviamo subito la bussola
            e.getEntity().getInventory().remove(Material.COMPASS);
            // Segnaliamo che al respawn deve riceverla
            e.getEntity().setMetadata("giveCompass", new FixedMetadataValue(plugin, true));
        }
    }

    /** Al respawn ridà la bussola nello slot 9 */
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();

        // Schedule un tick dopo il respawn
        new BukkitRunnable() {
            @Override
            public void run() {
                // Rimuoviamo il marker
                if (p.hasMetadata("giveCompass")) {
                    p.removeMetadata("giveCompass", plugin);
                }
                // Se è ancora hunter, ridiamo la bussola
                Manhunt mh = getPlayerManhunt(p.getName());
                if (mh != null && mh.isHunter(p.getName())) {
                    ManhuntManager.this.giveCompass(p.getName());
                }
            }
        }.runTask(plugin);
    }

    /** Dai la tracking compass (nono slot) a un player online */
    private void giveCompass(String playerName) {
        Player p = Bukkit.getPlayer(playerName);
        if (p == null) return;
        ItemStack comp = new ItemStack(Material.COMPASS);
        CompassMeta meta = (CompassMeta) comp.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RED + "Tracking Compass");
            comp.setItemMeta(meta);
        }
        p.getInventory().setItem(8, comp);
    }

    // --- utilities ---

    private void send(String playerName, String msg) {
        Player p = Bukkit.getPlayer(playerName);
        if (p != null) p.sendMessage(msg);
    }

    private void send(Player p, String msg) {
        p.sendMessage(msg);
    }

    private void broadcast(String msg) {
        Bukkit.broadcastMessage(msg);
    }

    /** Trova la manhunt in cui il player è survivor o hunter */
    public Manhunt getPlayerManhunt(String player) {
        return manhunts.values().stream()
                .filter(m -> m.getSurvivors().contains(player)
                        || m.getHunters().contains(player))
                .findFirst().orElse(null);
    }

    /** Tutti i nomi di manhunt in pending o attive (per tab-complete) */
    public Set<String> getAllPendingOrActiveNames() {
        Set<String> names = new HashSet<>(pending.keySet());
        names.addAll(manhunts.keySet());
        return names;
    }
}
