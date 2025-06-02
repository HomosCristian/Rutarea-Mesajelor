package org.example.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.example.model.MailMessage;
import org.example.protocol.Protocol;
import org.example.repository.MailboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;
import java.util.*;

public class MailService {
    private static final Logger log = LoggerFactory.getLogger(MailService.class);
    private static final Gson gson = new Gson();

    private final Set<String> localRecipients;
    private final List<String> peers;
    private final int maxHops;
    private final String localHostPort; // ex. "127.0.0.1:12345"

    public MailService(Set<String> localRecipients, List<String> peers, int maxHops, String localHostPort) {
        this.localRecipients = Collections.unmodifiableSet(new HashSet<>(localRecipients));
        this.peers = Collections.unmodifiableList(new ArrayList<>(peers));
        this.maxHops = maxHops;
        this.localHostPort = localHostPort;
    }

    /**
     * Verifică dacă destinatarul există local.
     */
    public JsonObject handleCheckRecipient(JsonObject request) {
        String dest = request.get("recipient").getAsString();
        JsonObject resp = new JsonObject();
        if (localRecipients.contains(dest)) {
            resp.addProperty("status", Protocol.STATUS_FOUND);
            log.info("checkRecipient: '{}' găsit local.", dest);
        } else {
            resp.addProperty("status", Protocol.STATUS_NOT_FOUND);
            log.info("checkRecipient: '{}' nu există local.", dest);
        }
        return resp;
    }

    /**
     * Procesează un sendMessage:
     * 1) Dacă destinatar local → SALVARE pe disc.
     * 2) Altfel, parcurge peers ca să găseşti unde e rădăcina.
     *
     * @param request JsonObject complet cu câmpurile necesare.
     *                Poate conţine şi “hops” și “visited”.
     * @return JsonObject cu status-ul (DELIVERED, ROUTED sau ERROR).
     */
    public JsonObject handleSendMessage(JsonObject request) {
        // Validare câmpuri obligatorii
        if (!request.has("to") || !request.has("from")
                || !request.has("subject") || !request.has("body")) {
            JsonObject err = new JsonObject();
            err.addProperty("status", Protocol.STATUS_ERROR);
            err.addProperty("error", Protocol.ERROR_MISSING_FIELD);
            log.warn("sendMessage invalid: lipsește câmp obligatoriu.");
            return err;
        }

        // Extragem hops și visited
        int hops = request.has("hops") ? request.get("hops").getAsInt() : 0;
        List<String> visited;
        if (request.has("visited")) {
            visited = new ArrayList<>();
            JsonArray arr = request.getAsJsonArray("visited");
            for (JsonElement elem : arr) {
                visited.add(elem.getAsString());
            }
        } else {
            visited = new ArrayList<>();
        }

        if (hops > maxHops) {
            JsonObject err = new JsonObject();
            err.addProperty("status", Protocol.STATUS_ERROR);
            err.addProperty("error", Protocol.ERROR_NO_ROUTE);
            log.warn("sendMessage abandonat: maxHops = {}.", maxHops);
            return err;
        }

        // Extragem obiectul MailMessage din JSON
        MailMessage msg;
        try {
            msg = gson.fromJson(request, MailMessage.class);
        } catch (Exception ex) {
            JsonObject err = new JsonObject();
            err.addProperty("status", Protocol.STATUS_ERROR);
            err.addProperty("error", Protocol.ERROR_BAD_REQUEST);
            log.warn("sendMessage invalid JSON: {}", ex.getMessage());
            return err;
        }

        String dest = msg.getTo();
        log.info("handleSendMessage: from='{}'  to='{}'  hops={}  visited={}",
                msg.getFrom(), dest, hops, visited);

        // 1) Dacă destinatar local: salvăm pe disc
        if (localRecipients.contains(dest)) {
            try {
                MailboxRepository.saveMessage(msg);
                JsonObject resp = new JsonObject();
                resp.addProperty("status", Protocol.STATUS_DELIVERED);
                resp.addProperty("where", "local");
                return resp;
            } catch (IOException ex) {
                JsonObject err = new JsonObject();
                err.addProperty("status", Protocol.STATUS_ERROR);
                err.addProperty("error", Protocol.ERROR_SAVE_FAILED);
                log.error("saveMessage eșuat pentru '{}': {}", dest, ex.getMessage());
                return err;
            }
        }

        // 2) Altfel, încercăm fiecare peer care nu a fost deja vizitat
        visited.add(localHostPort);
        for (String peer : peers) {
            if (visited.contains(peer)) {
                continue; // evităm ciclurile
            }

            String[] parts = peer.split(":");
            String host = parts[0];
            int peerPort = Integer.parseInt(parts[1]);

            // Construim JSON-ul de checkRecipient, incrementăm hops și includem visited
            JsonObject checkReq = new JsonObject();
            checkReq.addProperty("action", Protocol.ACTION_CHECK_RECIPIENT);
            checkReq.addProperty("recipient", dest);
            checkReq.addProperty("hops", hops + 1);
            JsonArray arrVisited = new JsonArray();
            for (String v : visited) {
                arrVisited.add(v);
            }
            checkReq.add("visited", arrVisited);

            try (Socket s = new Socket(host, peerPort);
                 BufferedWriter bout = new BufferedWriter(new OutputStreamWriter(s.getOutputStream()));
                 BufferedReader bin = new BufferedReader(new InputStreamReader(s.getInputStream()))) {

                bout.write(gson.toJson(checkReq));
                bout.newLine();
                bout.flush();

                String peerRespStr = bin.readLine();
                JsonObject peerResp = gson.fromJson(peerRespStr, JsonObject.class);
                String statusPeer = peerResp.get("status").getAsString();

                if (Protocol.STATUS_FOUND.equals(statusPeer)) {
                    // Dacă peer găsește, îi forwardăm exact JSON-ul original + hops+1+visited
                    JsonObject forwardReq = request.deepCopy();
                    forwardReq.addProperty("hops", hops + 1);
                    JsonArray newVisited = new JsonArray();
                    for (String v : visited) {
                        newVisited.add(v);
                    }
                    forwardReq.add("visited", newVisited);

                    try (Socket s2 = new Socket(host, peerPort);
                         BufferedWriter bout2 = new BufferedWriter(new OutputStreamWriter(s2.getOutputStream()));
                         BufferedReader bin2 = new BufferedReader(new InputStreamReader(s2.getInputStream()))) {

                        bout2.write(gson.toJson(forwardReq));
                        bout2.newLine();
                        bout2.flush();

                        String finalPeerResp = bin2.readLine();
                        JsonObject resp = new JsonObject();
                        resp.addProperty("status", Protocol.STATUS_ROUTED);
                        resp.addProperty("where", peer);
                        log.info("Mesaj rutat către {} (dest '{}').", peer, dest);
                        return resp;
                    }
                }
            } catch (IOException ex) {
                log.warn("Peer '{}' indisponibil: {}", peer, ex.getMessage());
                // continuăm cu următorul peer
            }
        }

        // Dacă am terminat bucla fără să găsim niciun peer cu destinatarul respectiv, trimitem eroare
        JsonObject noRoute = new JsonObject();
        noRoute.addProperty("status", Protocol.STATUS_ERROR);
        noRoute.addProperty("error", Protocol.ERROR_NO_ROUTE);
        log.warn("Nicio rută găsită pentru '{}'.", dest);
        return noRoute;
    }
}
