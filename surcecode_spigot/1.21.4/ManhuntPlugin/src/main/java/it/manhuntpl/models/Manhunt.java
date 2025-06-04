package it.manhuntpl.models;

import java.util.*;

public class Manhunt {
    private final String name;
    private final String host;
    private final Set<String> hunters = new HashSet<>();
    private final Set<String> survivors = new HashSet<>();
    private final Set<String> originalHunters;
    private final Set<String> originalSurvivors;

    public Manhunt(String name,
                   List<String> survivors,
                   List<String> hunters,
                   String host) {
        this.name = name;
        this.host = host;
        this.originalSurvivors = new HashSet<>(survivors);
        this.originalHunters   = new HashSet<>(hunters);
        // popola le collezioni attive
        this.survivors.addAll(survivors);
        this.hunters.addAll(hunters);
    }

    public String getName() {
        return name;
    }

    public String getHost() {
        return host;
    }

    public List<String> getHunters() {
        return new ArrayList<>(hunters);
    }

    public List<String> getSurvivors() {
        return new ArrayList<>(survivors);
    }

    public List<String> getOriginalHunters() {
        return new ArrayList<>(originalHunters);
    }

    public List<String> getOriginalSurvivors() {
        return new ArrayList<>(originalSurvivors);
    }

    /** Aggiunge un hunter anche se non era originale */
    public void addHunter(String p) {
        hunters.add(p);
    }

    /** Aggiunge un survivor anche se non era originale */
    public void addSurvivor(String p) {
        survivors.add(p);
    }

    public boolean removePlayer(String p) {
        return hunters.remove(p) || survivors.remove(p);
    }

    public boolean isHunter(String p) {
        return hunters.contains(p);
    }

    public boolean isSurvivor(String p) {
        return survivors.contains(p);
    }

    public boolean isPlayer(String p) {
        return isHunter(p) || isSurvivor(p);
    }

    public boolean wasOriginalHunter(String p) {
        return originalHunters.contains(p);
    }

    public boolean wasOriginalSurvivor(String p) {
        return originalSurvivors.contains(p);
    }

    public boolean wasOriginalPlayer(String p) {
        return wasOriginalHunter(p) || wasOriginalSurvivor(p);
    }
}
