package it.manhuntpl.models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ManhuntCode {
    private final String manhuntName;
    private final String host;
    private final List<String> survivors = new ArrayList<>();
    private final List<String> hunters = new ArrayList<>();

    public ManhuntCode(String manhuntName, String initialSurvivor, String initialHunter, String host) {
        this.manhuntName = manhuntName;
        this.host = host;
        this.survivors.add(initialSurvivor);
        this.hunters.add(initialHunter);
    }

    public String getManhuntName() {
        return manhuntName;
    }

    public String getHost() {
        return host;
    }

    public List<String> getSurvivors() {
        return Collections.unmodifiableList(survivors);
    }

    public List<String> getHunters() {
        return Collections.unmodifiableList(hunters);
    }

    public void addSurvivor(String player) {
        if (!survivors.contains(player)) {
            survivors.add(player);
        }
    }

    public void addHunter(String player) {
        if (!hunters.contains(player)) {
            hunters.add(player);
        }
    }
}
