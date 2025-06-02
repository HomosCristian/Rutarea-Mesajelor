package org.example.model;
import java.util.List;

public class Config {
    private int port;
    private List<String> localRecipients;
    private List<String> peers;
    private int maxHops;

    public Config() { }

    public int getPort() {
        return port;
    }
    public void setPort(int port) {
        this.port = port;
    }

    public List<String> getLocalRecipients() {
        return localRecipients;
    }
    public void setLocalRecipients(List<String> localRecipients) {
        this.localRecipients = localRecipients;
    }

    public List<String> getPeers() {
        return peers;
    }
    public void setPeers(List<String> peers) {
        this.peers = peers;
    }

    public int getMaxHops() {
        return maxHops;
    }
    public void setMaxHops(int maxHops) {
        this.maxHops = maxHops;
    }
}

