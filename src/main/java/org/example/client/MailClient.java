package org.example.client;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.example.model.MailMessage;
import org.example.protocol.Protocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Scanner;

public class MailClient {
    private static final Logger log = LoggerFactory.getLogger(MailClient.class);
    private static final Gson gson = new Gson();

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        // 1) Citire host și port de la linia de comandă sau prompt
        String host;
        int port;
        if (args.length >= 2) {
            host = args[0];
            port = Integer.parseInt(args[1]);
        } else {
            System.out.print("Host server (ex: 127.0.0.1): ");
            host = scanner.nextLine().trim();
            System.out.print("Port server (ex: 12345): ");
            port = Integer.parseInt(scanner.nextLine().trim());
        }

        while (true) {
            System.out.println("\n=== MailClient ===");
            System.out.println("1) sendMessage");
            System.out.println("2) changeServer");
            System.out.println("3) quit");
            System.out.print("Opțiune: ");
            String choice = scanner.nextLine().trim();

            switch (choice) {
                case "1":
                    sendMessage(scanner, host, port);
                    break;

                case "2":
                    System.out.print("Noul Host server: ");
                    host = scanner.nextLine().trim();
                    System.out.print("Noul Port server: ");
                    port = Integer.parseInt(scanner.nextLine().trim());
                    System.out.println("Server schimbat la " + host + ":" + port);
                    break;

                case "3":
                    System.out.println("La revedere!");
                    scanner.close();
                    return;

                default:
                    System.out.println("Opțiune invalidă. Reîncearcă.");
            }
        }
    }

    private static void sendMessage(Scanner scanner, String host, int port) {
        System.out.print("From (expeditor): ");
        String from = scanner.nextLine().trim();
        System.out.print("To (destinatar): ");
        String to = scanner.nextLine().trim();
        if (to.isEmpty() || from.isEmpty()) {
            System.out.println("From și To nu pot fi goale.");
            return;
        }
        System.out.print("Subject: ");
        String subject = scanner.nextLine().trim();
        System.out.print("Body (textul mesajului), apoi Enter: ");
        String body = scanner.nextLine().trim();

        MailMessage mail = new MailMessage(to, from, subject, body);

        JsonObject requestObj = new JsonObject();
        requestObj.addProperty("action", Protocol.ACTION_SEND_MESSAGE);
        requestObj.addProperty("to", mail.getTo());
        requestObj.addProperty("from", mail.getFrom());
        requestObj.addProperty("subject", mail.getSubject());
        requestObj.addProperty("body", mail.getBody());
        // Pornim cu hops = 0 și visited = [] (array gol)
        requestObj.addProperty("hops", 0);
        requestObj.add("visited", new JsonArray());

        String jsonSend = gson.toJson(requestObj);
        System.out.println("<<< Trimit către server: " + jsonSend);

        // Deschidem socket și comunicăm
        try (Socket socket = new Socket()) {
            // Timeout de 5s pentru conectare
            socket.connect(new InetSocketAddress(host, port), 5000);
            socket.setSoTimeout(5000); // Timeout 5s pentru read

            try (
                    BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                    BufferedReader in  = new BufferedReader(new InputStreamReader(socket.getInputStream()))
            ) {
                out.write(jsonSend);
                out.newLine();
                System.out.println("    (trimite newline)");
                out.flush();
                System.out.println("    (flush efectuat, aștept răspuns)");

                String rasp = in.readLine(); // poate arunca SocketTimeoutException
                if (rasp != null) {
                    System.out.println("Răspuns server: " + rasp);
                } else {
                    System.out.println("Server a închis conexiunea fără răspuns.");
                }
            }
        } catch (SocketTimeoutException ex) {
            System.err.println("Timeout: serverul nu a răspuns în 5 secunde.");
            log.warn("Timeout la comunicarea cu {}:{}", host, port);
        } catch (IOException ex) {
            System.err.println("Eroare la conectare sau trimitere: " + ex.getMessage());
            log.error("I/O error: {}", ex.getMessage());
        }
    }
}
