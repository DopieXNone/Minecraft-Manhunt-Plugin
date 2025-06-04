package it.manhuntpl.managers;

import it.manhuntpl.ManhuntPlugin;
import it.manhuntpl.models.Manhunt;
import it.manhuntpl.models.ManhuntCode;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ManhuntManager {
    private final ManhuntPlugin plugin;
    private final Map<String, Manhunt> manhunts          = new ConcurrentHashMap<>();
    private final Map<String, ManhuntCode> pending       = new ConcurrentHashMap<>();
    private final Map<String, List<Request>> joinRequests = new ConcurrentHashMap<>();
    private final Map<String, String> tracked            = new ConcurrentHashMap<>();
    // Impostazioni a livello di manhunt
    private final Map<String, Settings> mhSettings       = new ConcurrentHashMap<>();

    public ManhuntManager(ManhuntPlugin plugin) {
        this.plugin = plugin;
    }

    private static class Request {
        final String player;
        final String role;
        Request(String player, String role) {
            this.player = player;
            this.role   = role;
        }
    }

    /** Container per le impostazioni di una singola manhunt */
    public static class Settings {
        public boolean stopwatch = false;            // mostra tempo nella action bar se true
        public boolean countdown = false;            // iniziale countdown
        public boolean glowSurvivors = false;        // glow permanente sui survivors
        public boolean initialInvincibility = false; // invincibilità iniziale
    }

    /** Restituisce (o crea) le impostazioni associate a una manhunt */
    public Settings getSettings(String mhName) {
        return mhSettings.computeIfAbsent(mhName, k -> new Settings());
    }

    /**
     * Carica le impostazioni da file YAML per la manhunt specificata.
     * Se non esiste il file, crea la cartella e un file con valori di default.
     */
    public void loadSettings(String mhName) {
        // Base directory: plugins/ManhuntPlugin/manhunt/<mhName>
        File baseDir = new File(plugin.getDataFolder(), "manhunt/" + mhName);
        if (!baseDir.exists()) baseDir.mkdirs();

        File settingsFile = new File(baseDir, "settings.yml");
        Settings settings = new Settings();
        if (settingsFile.exists()) {
            YamlConfiguration yaml = new YamlConfiguration();
            try {
                yaml.load(settingsFile);
                settings.stopwatch = yaml.getBoolean("stopwatch", false);
                settings.countdown = yaml.getBoolean("countdown", false);
                settings.glowSurvivors = yaml.getBoolean("glowSurvivors", false);
                settings.initialInvincibility = yaml.getBoolean("initialInvincibility", false);
            } catch (IOException | InvalidConfigurationException e) {
                plugin.getLogger().warning("Could not load settings for manhunt " + mhName + ": " + e.getMessage());
            }
        }
        mhSettings.put(mhName, settings);
    }

    /** Salva le impostazioni correnti della manhunt su file YAML */
    public void saveSettings(String mhName) {
        Settings s = mhSettings.get(mhName);
        if (s == null) return;

        File baseDir = new File(plugin.getDataFolder(), "manhunt/" + mhName);
        if (!baseDir.exists()) baseDir.mkdirs();

        File settingsFile = new File(baseDir, "settings.yml");
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("stopwatch", s.stopwatch);
        yaml.set("countdown", s.countdown);
        yaml.set("glowSurvivors", s.glowSurvivors);
        yaml.set("initialInvincibility", s.initialInvincibility);
        try {
            yaml.save(settingsFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save settings for manhunt " + mhName + ": " + e.getMessage());
        }
    }

    /** Controlla se esiste una manhunt in pending con quel nome */
    public boolean isPendingManhunt(String name) {
        return pending.containsKey(name);
    }

    /** Controlla se il player è host di una manhunt in pending */
    public boolean isPendingHost(String name, String playerName) {
        ManhuntCode code = pending.get(name);
        return code != null && code.getHost().equals(playerName);
    }

    /** Restituisce l'insieme dei nomi delle manhunt in pending */
    public Set<String> getPendingNames() {
        return new HashSet<>(pending.keySet());
    }

    /** Crea (in pending) una nuova manhunt con 1 survivor e 1 hunter */
    public void createManhunt(String name, String survivor, String hunter, String host) {
        if (pending.containsKey(name) || manhunts.containsKey(name)) {
            send(host, ChatColor.RED + "Manhunt name already in use!");
            return;
        }
        pending.put(name, new ManhuntCode(name, survivor, hunter, host));
        // Pre-crea la cartella e il file settings con valori di default
        loadSettings(name);
        saveSettings(name);
        send(host, ChatColor.GREEN +
                "Manhunt '" + name +
                "' created. Host: confirm with /manhunt start " + name);
    }

    /** L’host conferma e parte la manhunt */
    public void startManhunt(Player p, String name) {
        ManhuntCode code = pending.get(name);
        if (code == null || !code.getHost().equals(p.getName())) {
            send(p, ChatColor.RED + "No manhunt to start with your name!");
            return;
        }
        // Rimuovi da pending
        pending.remove(name);

        // Crea l'oggetto Manhunt e assegna i partecipanti
        Manhunt mh = new Manhunt(name, code.getSurvivors(), code.getHunters(), code.getHost());
        for (String surv : code.getSurvivors()) mh.addSurvivor(surv);
        for (String hunt : code.getHunters()) {
            mh.addHunter(hunt);
            giveCompass(hunt);
        }
        manhunts.put(name, mh);

        // Le impostazioni erano già caricate in pending
        Settings s = mhSettings.get(name);

        // Applicazione Initial Invincibility (usa REGENERATION e SATURATION)
        if (s.initialInvincibility) {
            int invSecs = plugin.getConfig().getInt("initial-invincibility-seconds", 0);
            if (invSecs > 0) {
                int durationTicks = invSecs * 20;
                for (String playerName : mh.getHunters()) {
                    Player ph = Bukkit.getPlayer(playerName);
                    if (ph != null && ph.isOnline()) {
                        ph.addPotionEffect(new org.bukkit.potion.PotionEffect(PotionEffectType.REGENERATION, durationTicks, 255, true, false));
                        ph.addPotionEffect(new org.bukkit.potion.PotionEffect(PotionEffectType.SATURATION, durationTicks, 255, true, false));
                    }
                }
                for (String playerName : mh.getSurvivors()) {
                    Player ps = Bukkit.getPlayer(playerName);
                    if (ps != null && ps.isOnline()) {
                        ps.addPotionEffect(new org.bukkit.potion.PotionEffect(PotionEffectType.REGENERATION, durationTicks, 255, true, false));
                        ps.addPotionEffect(new org.bukkit.potion.PotionEffect(PotionEffectType.SATURATION, durationTicks, 255, true, false));
                    }
                }
            }
        }

        // Applicazione Glow sui survivors
        if (s.glowSurvivors) {
            for (String surv : mh.getSurvivors()) {
                Player ps = Bukkit.getPlayer(surv);
                if (ps != null && ps.isOnline()) {
                    ps.setGlowing(true);
                }
            }
        }

        // Initial Countdown
        if (s.countdown) {
            runInitialCountdown(mh);
        }

        broadcast(ChatColor.GOLD + "Manhunt '" + name + "' started!");
    }

    /** Countdown iniziale: blocca movimenti per X secondi (letto da config) */
    private void runInitialCountdown(Manhunt mh) {
        int totalSeconds = plugin.getConfig().getInt("initial-countdown-seconds", 5);

        new BukkitRunnable() {
            int counter = totalSeconds;
            @Override
            public void run() {
                if (counter <= 0) {
                    cancel();
                    // Rimuovi il blocco movimento
                    for (String surv : mh.getSurvivors()) {
                        Player ps = Bukkit.getPlayer(surv);
                        if (ps != null && ps.isOnline()) {
                            ps.setWalkSpeed(0.2f); // default
                        }
                    }
                    for (String hunt : mh.getHunters()) {
                        Player ph = Bukkit.getPlayer(hunt);
                        if (ph != null && ph.isOnline()) {
                            ph.setWalkSpeed(0.2f);
                        }
                    }
                    return;
                }
                // Durante il countdown, imposta velocità a zero
                for (String surv : mh.getSurvivors()) {
                    Player ps = Bukkit.getPlayer(surv);
                    if (ps != null && ps.isOnline()) {
                        ps.setWalkSpeed(0f);
                        ps.sendTitle("§cStarting in " + counter, "", 0, 20, 0);
                    }
                }
                for (String hunt : mh.getHunters()) {
                    Player ph = Bukkit.getPlayer(hunt);
                    if (ph != null && ph.isOnline()) {
                        ph.setWalkSpeed(0f);
                        ph.sendTitle("§cStarting in " + counter, "", 0, 20, 0);
                    }
                }
                counter--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    /** L’host annulla la creazione */
    public void rejectManhunt(Player p, String name) {
        ManhuntCode code = pending.get(name);
        if (code != null && code.getHost().equals(p.getName())) {
            pending.remove(name);
            mhSettings.remove(name);
            // Rimuovi cartella fisica
            File dir = new File(plugin.getDataFolder(), "manhunt/" + name);
            if (dir.exists()) {
                new File(dir, "settings.yml").delete();
                dir.delete();
            }
            send(p, ChatColor.YELLOW + "Manhunt '" + name + "' cancelled.");
        } else {
            send(p, ChatColor.RED + "No manhunt to reject with your name!");
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
                    "Join pre-accept request received. Host: /manhunt start " + name);
            return;
        }
        if (!manhunts.containsKey(name)) {
            send(p, ChatColor.RED + "No active manhunt or creation with that name!");
            return;
        }
        joinRequests
                .computeIfAbsent(name, k -> new ArrayList<>())
                .add(new Request(p.getName(), role));
        send(p, ChatColor.GREEN +
                "Request to join manhunt as " + role +
                " sent to host of '" + name + "'.");
    }

    /** Host approva una richiesta di join attiva */
    public void allowJoin(Player host, String targetName) {
        String mhName = manhunts.entrySet().stream()
                .filter(e -> e.getValue().getHost().equals(host.getName()))
                .map(Map.Entry::getKey)
                .findFirst().orElse(null);
        if (mhName == null) mhName = pending.entrySet().stream()
                .filter(e -> e.getValue().getHost().equals(host.getName()))
                .map(Map.Entry::getKey)
                .findFirst().orElse(null);
        if (mhName == null) {
            send(host, ChatColor.RED + "You aren't the host of any manhunt!");
            return;
        }

        List<Request> reqs = joinRequests.getOrDefault(mhName, Collections.emptyList());
        Optional<Request> reqOpt = reqs.stream()
                .filter(r -> r.player.equalsIgnoreCase(targetName))
                .findFirst();
        if (reqOpt.isEmpty()) {
            send(host, ChatColor.RED + "No join requests from " + targetName);
            return;
        }
        Request req = reqOpt.get();
        if (pending.containsKey(mhName)) {
            ManhuntCode code = pending.get(mhName);
            if (req.role.equals("survivor")) code.addSurvivor(req.player);
            else code.addHunter(req.player);
        } else {
            Manhunt mh = manhunts.get(mhName);
            if (req.role.equals("survivor")) {
                mh.addSurvivor(req.player);
                // se glow attivo, applica glow immediato
                Settings s = getSettings(mhName);
                if (s.glowSurvivors) {
                    Player ps = Bukkit.getPlayer(req.player);
                    if (ps != null && ps.isOnline()) ps.setGlowing(true);
                }
            } else {
                mh.addHunter(req.player);
                giveCompass(req.player);
            }
        }

        send(host, ChatColor.GOLD + "Request " + req.player + " approved.");
        Player target = Bukkit.getPlayer(req.player);
        if (target != null && target.isOnline()) {
            send(target, ChatColor.GREEN +
                    "You were added to manhunt '" + mhName +
                    "' as " + req.role + "!");
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
        if (mhName == null) return Collections.emptyList();
        return joinRequests.getOrDefault(mhName, Collections.emptyList())
                .stream()
                .map(r -> r.player)
                .toList();
    }

    /** Permette a un player di abbandonare la manhunt in cui è */
    public void leaveManhunt(Player p) {
        String playerName = p.getName();
        Manhunt mh = getPlayerManhunt(playerName);
        if (mh == null) {
            send(p, ChatColor.RED + "You aren't in a manhunt!");
            return;
        }

        // Rimuove il giocatore chiamante da survivor o hunter
        boolean wasHunter = mh.getHunters().remove(playerName);
        mh.getSurvivors().remove(playerName);

        // Prepara la lista di eventuali rimanenti nella manhunt
        List<String> remainingPlayers = new ArrayList<>();
        remainingPlayers.addAll(mh.getHunters());
        remainingPlayers.addAll(mh.getSurvivors());

        // Pulisce effetti del giocatore che lascia e lo uccide/teletrasporta
        clearEffects(p);
        p.setHealth(0.0);
        p.teleport(p.getWorld().getSpawnLocation());

        // Informa il giocatore
        send(p, ChatColor.YELLOW + "You left the manhunt '" + mh.getName() + "'.");

        // Verifica se la manhunt è terminata (meno di 2 giocatori rimasti)
        if (remainingPlayers.size() <= 1) {
            // Se c'è un solo giocatore rimanente, anche lui viene fatto uscire
            for (String otherName : remainingPlayers) {
                Player other = Bukkit.getPlayer(otherName);
                if (other != null && other.isOnline()) {
                    // Rimuovi bussola se era hunter
                    if (mh.getHunters().contains(otherName)) {
                        other.getInventory().remove(Material.COMPASS);
                        tracked.remove(otherName);
                    }
                    // Pulisce effetti, uccide e teletrasporta
                    clearEffects(other);
                    other.setHealth(0.0);
                    other.teleport(other.getWorld().getSpawnLocation());
                    send(other, ChatColor.YELLOW + "You have been removed from manhunt '" + mh.getName() + "'.");
                }
            }

            // Rimuovi la manhunt e pulisci impostazioni e bussola residua
            manhunts.remove(mh.getName());
            mhSettings.remove(mh.getName());
            broadcast(ChatColor.RED + "Manhunt '" + mh.getName() + "' ended (insufficient players).");
            // Eventuali hunter sopravvissuti (già gestiti sopra) non hanno più bussola
        }
    }

    /** Imposta il target da tracciare */
    public void followTarget(Player p, String survivor) {
        Manhunt mh = getPlayerManhunt(p.getName());
        if (mh == null || !mh.isHunter(p.getName())) {
            send(p, ChatColor.RED + "You aren't a hunter in any manhunt!");
            return;
        }
        if (!mh.getSurvivors().contains(survivor)) {
            send(p, ChatColor.RED + survivor + " is not a survivor in this manhunt!");
            return;
        }
        tracked.put(p.getName(), survivor);
        send(p, ChatColor.GREEN + "Compass is tracking " + survivor);
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
            mhSettings.remove(nm);
            broadcast(ChatColor.RED +
                    "Survivor " + died + " has died: MANHUNT '" + nm + "' TERMINATED!");
            for (String h : mh.getHunters()) {
                Player ph = Bukkit.getPlayer(h);
                if (ph != null && ph.isOnline()) {
                    // Pulisce effetti, rimuove bussola e tracking
                    clearEffects(ph);
                    ph.getInventory().remove(Material.COMPASS);
                }
                tracked.remove(h);
            }
        }
    }

    /** Quando un hunter muore, prepara ricompensa di bussola al respawn */
    public void onHunterDeath(PlayerDeathEvent e) {
        String died = e.getEntity().getName();
        Manhunt mh = getPlayerManhunt(died);
        if (mh != null && mh.isHunter(died)) {
            e.getEntity().getInventory().remove(Material.COMPASS);
            e.getEntity().setMetadata("giveCompass", new FixedMetadataValue(plugin, true));
        }
    }

    /** Al respawn ridà la bussola nello slot 9 */
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        new BukkitRunnable() {
            @Override
            public void run() {
                if (p.hasMetadata("giveCompass")) {
                    p.removeMetadata("giveCompass", plugin);
                }
                Manhunt mh = getPlayerManhunt(p.getName());
                if (mh != null && mh.isHunter(p.getName())) {
                    giveCompass(p.getName());
                } else {
                    // Se non è più in manhunt, pulisci eventuali effetti residui
                    clearEffects(p);
                }
            }
        }.runTask(plugin);
    }

    /** Ripristina bussola e tracking al login dopo disconnessione */
    public void handleReconnect(Player p) {
        Manhunt mh = getPlayerManhunt(p.getName());
        if (mh == null) {
            clearEffects(p);
            return;
        }
        if (mh.isHunter(p.getName())) {
            giveCompass(p.getName());
            String target = tracked.get(p.getName());
            if (target != null) {
                send(p, ChatColor.GREEN + "Compass is still tracking " + target);
            }
        }
    }

    /** Rimuove tutti gli effetti/applicazioni di manhunt da un player */
    public void clearEffects(Player p) {
        // Rimuove tutti gli effetti attivi
        p.getActivePotionEffects().forEach(effect -> p.removePotionEffect(effect.getType()));

        // Disattiva glow
        p.setGlowing(false);

        // Ripristina velocità di cammino standard
        p.setWalkSpeed(0.2f);
    }

    /** Dai la tracking compass (slot 9) a un player online */
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
                .filter(m -> m.getSurvivors().contains(player) || m.getHunters().contains(player))
                .findFirst().orElse(null);
    }

    /** Tutti i nomi di manhunt in pending o attive (per tab-complete) */
    public Set<String> getAllPendingOrActiveNames() {
        Set<String> names = new HashSet<>(pending.keySet());
        names.addAll(manhunts.keySet());
        return names;
    }
}
