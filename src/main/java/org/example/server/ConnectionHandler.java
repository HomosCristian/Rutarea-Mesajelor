package org.example.server;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.example.service.MailService;
import org.example.protocol.Protocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;

public class ConnectionHandler implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(ConnectionHandler.class);
    private static final Gson gson = new Gson();

    private final Socket socket;
    private final MailService mailService;

    public ConnectionHandler(Socket socket, MailService mailService) {
        this.socket = socket;
        this.mailService = mailService;
    }

    @Override
    public void run() {
        String remote = socket.getRemoteSocketAddress().toString();
        log.info("Connection from {}", remote);

        try (
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()))
        ) {
            // 1) Citim exact o linie terminată cu newline
            String received = in.readLine();
            log.info(">>> Primit de la {}: {}", remote, received);

            if (received == null) {
                log.warn("Clientul {} a închis conexiunea fără să trimită date.", remote);
                return;
            }

            // 2) Parse JSON sau răspund cu BadRequest
            JsonObject request;
            try {
                request = gson.fromJson(received, JsonObject.class);
            } catch (Exception ex) {
                JsonObject resp = new JsonObject();
                resp.addProperty("status", Protocol.STATUS_ERROR);
                resp.addProperty("error", Protocol.ERROR_BAD_REQUEST);
                out.write(gson.toJson(resp));
                out.newLine();
                out.flush();
                log.warn("JSON invalid de la {}: {}", remote, ex.getMessage());
                return;
            }

            // 3) Verificăm câmpul action
            if (!request.has("action")) {
                JsonObject resp = new JsonObject();
                resp.addProperty("status", Protocol.STATUS_ERROR);
                resp.addProperty("error", Protocol.ERROR_MISSING_FIELD);
                out.write(gson.toJson(resp));
                out.newLine();
                out.flush();
                log.warn("Lipsă câmp 'action' de la {}", remote);
                return;
            }

            // 4) Determinăm acțiunea și apelăm serviciul
            String action = request.get("action").getAsString();
            JsonObject response;
            switch (action) {
                case Protocol.ACTION_CHECK_RECIPIENT:
                    log.info("Processing checkRecipient de la {}", remote);
                    response = mailService.handleCheckRecipient(request);
                    break;
                case Protocol.ACTION_SEND_MESSAGE:
                    log.info("Processing sendMessage de la {}: {}", remote, received);
                    response = mailService.handleSendMessage(request);
                    break;
                default:
                    response = new JsonObject();
                    response.addProperty("status", Protocol.STATUS_ERROR);
                    response.addProperty("error", Protocol.ERROR_UNKNOWN_ACTION);
                    log.warn("Acțiune necunoscută '{}' de la {}", action, remote);
                    break;
            }

            // 5) Trimitem răspunsul
            String raspStr = gson.toJson(response);
            log.info("<<< Trimit către {}: {}", remote, raspStr);
            out.write(raspStr);
            out.newLine();
            out.flush();
            log.info("Răspuns trimis către {}: {}", remote, raspStr);

        } catch (IOException ex) {
            log.error("Eroare I/O pe conexiunea cu {}: {}", remote, ex.getMessage());
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) { }
            log.info("Conexiunea cu {} a fost închisă", remote);
        }
    }
}
