package org.example.repository;
import com.google.gson.Gson;
import org.example.model.MailMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;

public class MailboxRepository {
    private static final Logger log = LoggerFactory.getLogger(MailboxRepository.class);
    private static final Gson gson = new Gson();
    private static final Path BASE_DIR = Paths.get("mailboxes");

    /**
     * Salvează mesajul în filesystem sub folder-ul mailboxes/<destinatar>/msg-<timestamp>.json
     * @throws IOException dacă scrierea eşuează
     */
    public static void saveMessage(MailMessage msg) throws IOException {
        String dest = msg.getTo();
        Path mailboxRoot = BASE_DIR.resolve(dest);
        if (!Files.exists(mailboxRoot)) {
            Files.createDirectories(mailboxRoot);
            log.info("Creat director pentru destinatarul '{}': {}", dest, mailboxRoot.toAbsolutePath());
        }
        String ts = String.valueOf(Instant.now().toEpochMilli());
        Path file = mailboxRoot.resolve("msg-" + ts + ".json");
        String jsonContent = gson.toJson(msg);
        Files.write(file, jsonContent.getBytes(), StandardOpenOption.CREATE_NEW);
        log.info("Mesaj salvat local pentru '{}': {}", dest, file.toAbsolutePath());
    }
}

