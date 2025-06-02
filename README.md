# Proiect Rețele – Rutare Mesaje (Mail Routing)

Acest proiect implementează o aplicație client-server de tip “mail routing” folosind socket-uri TCP în Java. Scopul este ca un server să gestioneze trimiterea și stocarea mesajelor către destinatari (“rutare”) și, dacă destinatarul nu există local, să redirecționeze mesajul către peer-uri specificate.

## Cuprins

1. [Descriere generală](#descriere-generală)  
2. [Structura proiectului](#structura-proiectului)  
3. [Fișiere de configurare](#fișiere-de-configurare)  
4. [Prerechizite](#prerechizite)  
5. [Cum se compilează](#cum-se-compileaz%C4%83)  
6. [Cum se rulează serverele și clientul](#cum-se-ruleaz%C4%83-serverele-%C8%99i-clientul)  
   - 6.1. Server – fișiere configurare  
   - 6.2. Client – argumente sau prompt CLI  
7. [Exemple de test – mesaje locale și rutate](#exemple-de-test--mesaje-locale-%C8%99i-rutate)  
8. [Structura directorului `mailboxes/`](#structura-directorului-mailboxes)  
9. [Ștergerea mesajelor din `mailboxes/`](#%C8%99tergerea-mesajelor-din-mailboxes)  
10. [Cum se pune proiectul pe GitHub](#cum-se-pune-proiectul-pe-github)  
11. [Licență](#licen%C8%9B%C4%83)

---

## 1. Descriere generală

- **MailServer** – ascultă pe un port TCP specificat, primește cereri JSON cu acțiuni (`checkRecipient` și `sendMessage`), gestionează o listă de destinatari locali și o listă de peer-uri (alți serveri).  
  - Dacă destinatarul există local, salvează mesajul în `mailboxes/<destinatar>/msg-<timestamp>.json`.  
  - Dacă destinatarul nu există local, consultă lista de peer-uri (fiecare format „host:port”) și trimite mai întâi o cerere `checkRecipient`. Dacă peer-ul returnează `"FOUND"`, forwards `sendMessage` spre acel peer.  
  - Respectă o limită maximă de hop-uri (`maxHops`) și un istoric scurt (`visited`) pentru a evita ciclurile de rutare.

- **MailClient** – interfață CLI care:  
  1. Solicită host și port de server.  
  2. Permite trimiterea unui mesaj (`sendMessage`) cu câmpurile: `from`, `to`, `subject`, `body`.  
  3. Poate schimba manual host/port la runtime.  
  4. Primește și afișează răspunsul JSON de la server (ex.: `{"status":"DELIVERED","where":"local"}` sau `{"status":"ROUTED","where":"127.0.0.1:23456"}`).

- **MailService** – pe server, logica de procesare a cererilor de tip `checkRecipient` și `sendMessage`, apelată de `ConnectionHandler`.

- **ConnectionHandler** – la fiecare conexiune TCP, citește o linie JSON, alege acțiunea potrivită și trimite înapoi un JSON de răspuns, apoi închide conexiunea.

---

## 2. Structura proiectului

