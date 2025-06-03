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

---

## 1. Descriere generală

- **MailServer** – ascultă pe un port TCP specificat, primește cereri JSON cu acțiuni (`checkRecipient` și `sendMessage`), gestionează o listă de destinatari locali și o listă de peer-uri (alți serveri).  
  - Dacă destinatarul există local, salvează mesajul în `mailboxes/<destinatar>/msg-<timestamp>.json`.  
  - Dacă destinatarul nu există local, consultă lista de peer-uri (fiecare format “host:port”) și trimite mai întâi o cerere `checkRecipient`. Dacă peer-ul returnează `"FOUND"`, forwards `sendMessage` spre acel peer.  
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

```
ProiectRetele/
├─ .gitignore
├─ pom.xml
├─ README.md
├─ src/
│  ├─ main/
│  │  ├─ java/
│  │  │  └─ org/example/
│  │  │     ├─ client/
│  │  │     │   └─ MailClient.java
│  │  │     ├─ model/
│  │  │     │   ├─ Config.java
│  │  │     │   └─ MailMessage.java
│  │  │     ├─ protocol/
│  │  │     │   └─ Protocol.java
│  │  │     ├─ repository/
│  │  │     │   └─ MailboxRepository.java
│  │  │     ├─ server/
│  │  │     │   ├─ ConnectionHandler.java
│  │  │     │   └─ MailServer.java
│  │  │     └─ service/
│  │  │         └─ MailService.java
│  │  └─ resources/
│  │     ├─ config-12345.json
│  │     └─ config-23456.json
│  └─ test/  ← (opțional, dacă adaugi teste unitare)
└─ mailboxes/ ← se va crea automat la runtime
```

- **`pom.xml`** – definirea dependențelor Maven (Gson, SLF4J, Logback).  
- **`src/main/java/...`** – codul sursă Java.  
- **`src/main/resources/...`** – fișiere de configurare JSON.  
- **`mailboxes/`** – director creat la runtime pentru stocarea mesajelor per destinatar.

---

## 3. Fișiere de configurare

În `src/main/resources/` există două fișiere JSON:

### 3.1. `config-12345.json`

```json
{
  "port": 12345,
  "localRecipients": ["cristi", "tibi"],
  "peers": ["127.0.0.1:23456"],
  "maxHops": 5
}
```
- `port` – instrumente ulterioare ascultă pe 12345.  
- `localRecipients` – lista numelor unice de utilizatori deserviți local (ex.: “cristi”, “tibi”).  
- `peers` – lista de servere alternative (ex.: “127.0.0.1:23456”) pentru rutare.  
- `maxHops` – pragul maxim de redirecționări pentru a evita bucle infinite.

### 3.2. `config-23456.json`

```json
{
  "port": 23456,
  "localRecipients": ["ana", "alex"],
  "peers": ["127.0.0.1:12345"],
  "maxHops": 5
}
```
- `port`: 23456.  
- `localRecipients`: “ana”, “alex”.  
- `peers`: “127.0.0.1:12345” (serverul precedent).  
- `maxHops`: 5.

> **Notă**: La pornirea serverului poți specifica numele fișierului de configurare. De exemplu:  
> ```
> java -cp target/ProiectRetele-1.0-SNAPSHOT.jar org.example.server.MailServer config-12345.json
> ```

---

## 4. Prerechizite

- **Java 17 + JDK** instalat și configurat în `PATH`.  
- **Maven** (dacă vrei să rulezi `mvn` din linia de comandă), sau poți folosi Maven integrat în IntelliJ.  
- **Internet** doar pentru descărcarea dependențelor inițiale (Gson, SLF4J, Logback).  
- **Git** – pentru versionarea codului și upload-ul pe GitHub (opțional).

---

## 5. Cum se compilează

În IntelliJ Idea:

1. Deschide proiectul (`File → Open… → folderul ProiectRetele`).  
2. IntelliJ va detecta automat `pom.xml` și va importa proiectul Maven.  
3. În tab-ul **Maven** (dreapta-sus), sub **Lifecycle**, dă dublu-click pe `package`.  
   - Maven va descărca dependențe și va compila proiectul.  
   - La final vei vedea “BUILD SUCCESS” și în `target/` va exista `ProiectRetele-1.0-SNAPSHOT.jar`.

Din linia de comandă (dacă ai Maven în `PATH`):

```bash
cd C:\Users\Homos Cristian\IdeaProjects\Retele\ProiectRetele
mvn clean package
```

---

## 6. Cum se rulează serverele și clientul

### 6.1. Server – fișiere configurare

Presupunem că ai generat JAR-ul în `target/ProiectRetele-1.0-SNAPSHOT.jar`.

1. Deschide un terminal / PowerShell în folderul proiectului (unde se află `pom.xml` și `target/`).  
2. Rulează serverul pe portul 12345 (cu destinatarii “cristi” și “tibi”):

   ```bash
   java -cp target/ProiectRetele-1.0-SNAPSHOT.jar org.example.server.MailServer config-12345.json
   ```
   - Vei vedea în consolă:
     ```
     INFO  MailServer  – Pornim ThreadPool cu X thread-uri
     INFO  MailServer  – MailServer ascultă pe portul 12345
     ```

3. Deschide un alt terminal (sau o altă fereastră IntelliJ → Run Configuration) și pornește a doua instanță de server:

   ```bash
   java -cp target/ProiectRetele-1.0-SNAPSHOT.jar org.example.server.MailServer config-23456.json
   ```
   - Consolă:
     ```
     INFO  MailServer  – Pornim ThreadPool cu X thread-uri
     INFO  MailServer  – MailServer ascultă pe portul 23456
     ```

Acum ai două instanțe active, fiecare ascultând pe port diferit și servind destinatari diferiți.

---

### 6.2. Client – argumente sau prompt CLI

Într-un alt terminal (sau alt tab din IntelliJ Terminal):

```bash
java -cp target/ProiectRetele-1.0-SNAPSHOT.jar org.example.client.MailClient
```

- La prima rulare, ți se va cere:
  ```
  Host server (ex: 127.0.0.1): 127.0.0.1
  Port server (ex: 12345): 12345
  ```
- Apoi CLI-ul afișează:
  ```
  === MailClient ===
  1) sendMessage
  2) changeServer
  3) quit
  Opțiune:
  ```
- Alege:
  - **1 – sendMessage**  
    - Introdu `From (expeditor)`, `To (destinatar)`, `Subject`, `Body`.  
    - Apasă Enter după ce ai completat fiecare linie.  
    - Vei vedea la final:
      ```
      <<< Trimit către server: {"action":"sendMessage","to":"tibi",...,"visited":[]}
          (trimite newline)
          (flush efectuat, aștept răspuns)
      Răspuns server: {"status":"DELIVERED","where":"local"}
      ```
  - **2 – changeServer**  
    - Poți schimba host și port la runtime (de exemplu, pentru a trimite un mesaj către celălalt server).  
  - **3 – quit**  
    - Închide clientul.

---

## 7. Exemple de test – mesaje locale și rutate

1. **Mesaj local** (destinatar în `localRecipients`):  
   - Server #1 (port 12345) are `localRecipients`: [`“cristi”`, “tibi”].  
   - În CLI client conectat la 127.0.0.1:12345, rulează:
     ```
     From: cristi
     To: tibi
     Subject: Salut
     Body: Ce mai faci?
     ```
   - Rezultat în consola client:  
     ```
     Răspuns server: {"status":"DELIVERED","where":"local"}
     ```
   - În consola serverului #1:
     ```
     >>> Primit de la /127.0.0.1:XXXXX: {"action":"sendMessage",...,"visited":[]}
     Processing sendMessage ... hops=0 visited=[]
     <<< Trimit către /127.0.0.1:XXXXX: {"status":"DELIVERED","where":"local"}
     Răspuns trimis către /127.0.0.1:XXXXX: {"status":"DELIVERED","where":"local"}
     ```
   - Fișierul este salvat în `mailboxes/tibi/msg-<timestamp>.json`.

2. **Mesaj rutat** (destinatar pe alt server):  
   - Server #1 (12345) nu are „ana” în `localRecipients`.  
   - Server #2 (23456) are „ana” local.  
   - În client, conectat la 127.0.0.1:12345, rulează:
     ```
     From: cristi
     To: ana
     Subject: Test rutare
     Body: Salut Ana!
     ```
   - În consola client:
     ```
     Răspuns server: {"status":"ROUTED","where":"127.0.0.1:23456"}
     ```
   - În consola serverului #1:
     ```
     >>> Primit de la /127.0.0.1:YYYYY: {"action":"sendMessage","to":"ana","from":"cristi","subject":"Test”,"body":"Salut Ana!","hops":0,"visited":[]}
     Processing sendMessage ... hops=0 visited=[]
     INFO  MailService – Mesaj rutat către 127.0.0.1:23456 (dest 'ana').
     <<< Trimit către /127.0.0.1:YYYYY: {"status":"ROUTED","where":"127.0.0.1:23456"}
     Răspuns trimis către /127.0.0.1:YYYYY: {"status":"ROUTED","where":"127.0.0.1:23456"}
     ```
   - În consola serverului #2:
     ```
     >>> Primit de la /127.0.0.1:ZZZZZ: {"action":"checkRecipient","recipient":"ana","hops":1,"visited":["127.0.0.1:12345"]}
     INFO  MailService – checkRecipient: 'ana' găsit local.
     >>> Primit de la /127.0.0.1:AAAAA: {"action":"sendMessage","to":"ana","from":"cristi","subject":"Test”,"body":"Salut Ana!","hops":1,"visited":["127.0.0.1:12345"]}
     Processing sendMessage ... hops=1 visited=[127.0.0.1:12345]
     INFO  MailboxRepository – Mesaj salvat local pentru 'ana': .../mailboxes/ana/msg-<timestamp>.json
     <<< Trimit către /127.0.0.1:AAAAA: {"status":"DELIVERED","where":"local"}
     Răspuns trimis către /127.0.0.

