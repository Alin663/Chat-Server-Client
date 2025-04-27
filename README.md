Sistema di Chat Sicura con Crittografia RSA

Questo progetto implementa un sistema di chat client-server che utilizza la crittografia RSA a 2048 bit per garantire comunicazioni sicure. 

Include:

Server: Gestisce le connessioni, autenticazione e messaggi.

Client: Interfaccia grafica per registrazione, login e chat.

Crittografia end-to-end: Tutti i messaggi sono cifrati prima dell'invio.

Funzionalità
Registrazione utenti con password crittografate
Login tramite scambio di chiavi RSA
Chat in tempo reale con messaggi cifrati
Log del server per tracciare attività e messaggi in tempo reale e salvati in seguito

Credenziali Predefinite
Username: admin

Password: admin123

Sicurezza
Crittografia RSA a 2048 bit per chiavi pubbliche/private
Nessun dato in chiaro (password e messaggi sempre cifrati)
Autenticazione obbligatoria prima della chat

Requisiti
Java 8 o superiore

Connessione TCP/IP (localhost o rete LAN)

Note
Ci sono problemi non risolti sulla registrazione che non permette di fare il login in seguito, ma bisogna riavviare il file.


Sviluppato come progetto dimostrativo per lo studio della crittografia RSA e dei Server Socket.
