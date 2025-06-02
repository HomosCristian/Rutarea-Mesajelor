package org.example.server;

import com.google.gson.Gson;
import org.example.model.Config;
import org.example.service.MailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MailServer {
    private static final Logger log = LoggerFactory.getLogger(MailServer.class);
    private static final Gson gson = new Gson();

    public static void main(String[] args) {
        // Citim numele fișierului de configurare din args (dacă există)
        String resourcePath = "/config.json"; // default
        if (args.length >= 1) {
            resourcePath = "/" + args[0];     // ex. "/config-12345.json"
        }

        Config config = loadConfig(resourcePath);
        if (config == null) {
            log.error("Nu am putut încărca {}. Oprire server.", resourcePath);
            return;
        }

        int port = config.getPort();
        Set<String> localRecipients = new HashSet<>(config.getLocalRecipients());
        List<String> peers = config.getPeers();
        int maxHops = config.getMaxHops();

        // Obținem host:port local
        String hostAddress = "127.0.0.1";
        try {
            hostAddress = InetAddress.getLocalHost().getHostAddress();
        } catch (Exception ignored) { }
        String localHostPort = hostAddress + ":" + port;

        // Cream serviciul de mail
        MailService mailService = new MailService(localRecipients, peers, maxHops, localHostPort);

        // Pornim un pool de thread-uri
        int poolSize = Runtime.getRuntime().availableProcessors() * 2;
        ExecutorService pool = Executors.newFixedThreadPool(poolSize);
        log.info("Pornim ThreadPool cu {} thread-uri", poolSize);

        // Deschidem ServerSocket și asumăm bucla de accept
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            log.info("MailServer ascultă pe portul {}", port);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                pool.submit(new ConnectionHandler(clientSocket, mailService));
            }
        } catch (Exception ex) {
            log.error("Eroare la pornirea serverului: {}", ex.getMessage());
        } finally {
            pool.shutdown();
            log.info("MailServer pe portul {} se oprește. Pool de thread-uri închis.", port);
        }
    }

    /**
     * Încarcă fișierul JSON de configurare din resources (classpath).
     * @param resourcePath ex. "/config-12345.json"
     * @return instanță Config sau null dacă nu a putut citi/parse
     */
    private static Config loadConfig(String resourcePath) {
        try (InputStreamReader reader = new InputStreamReader(
                MailServer.class.getResourceAsStream(resourcePath))) {
            return gson.fromJson(reader, Config.class);
        } catch (Exception ex) {
            log.error("Nu am putut citi {}: {}", resourcePath, ex.getMessage());
            return null;
        }
    }
}
