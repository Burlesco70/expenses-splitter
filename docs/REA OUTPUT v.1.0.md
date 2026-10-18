OUTPUT v.1.0

 L'app è pronta e ho generato l'APK, ma non l'ho mai fatta girare su un dispositivo o su un emulatore. Ho verificato solo la logica con 8 test unitari, tutti passati,
 e le proprietà dell'APK.

 Cosa ho verificato sull'APK
 - Package com.pbg.expensessplitter, nome "Expenses Splitter".
 - Nessun permesso INTERNET e nessun SDK pubblicitario.
 - La firma risulta con "Python Biella Group".

 File in dist/
 - ExpensesSplitter-1.0.0-release.apk: firmato con una chiave generata da me, 11 MB.
 - ExpensesSplitter-1.0.0-debug.apk: stessa app, firmata con la chiave di debug.
 - legacy-Splitter.db: il database dell'app originale già estratto dal backup.

 Cosa fa l'app
 - Gestisce gruppi e partecipanti, e registra le spese con importo, valuta con cambio manuale, categoria, data e foto dello scontrino da galleria o fotocamera.
 - La divisione può essere in pari quote, a quote fisse o in percentuale. Gli importi sono in centesimi interi.
 - Il calcolo dei trasferimenti è esatto, quindi usa il numero minimo possibile, fino a 20 persone. Oltre le 20 usa un algoritmo greedy come l'originale.
 - Il pulsante "Registra pagamento" aggiunge un pareggio parziale o totale.
 - I report TXT e CSV si condividono da chat o e-mail. Il CSV usa il separatore ; con BOM, così si apre direttamente in Excel italiano.
 - Il backup si fa in JSON o .db tramite il selettore di sistema, quindi anche su Google Drive.
     - L'import JSON aggiunge viaggi.
     - Il ripristino .db sostituisce tutto, dopo un controllo di integrità e di schema.
 - L'interfaccia è in italiano e inglese. Nelle informazioni e nei report compare il credito a Python Biella Group, con un cenno all'app originale come ispirazione.

 Cosa resta da provare a mano
 - L'import dal database dell'app originale, che non ho eseguito perché dipende da SQLite di Android. Copia legacy-Splitter.db sul telefono e usa il menu ⋮ → "Importa
   da Expense Splitter originale". Con i tuoi 61 viaggi controlla soprattutto date e saldi.
 - La fotocamera, la condivisione dei file e il ripristino del .db.
 - Le foto degli scontrini non sono incluse nei backup. Le valute sono trattate con 2 decimali.

 Cose da sapere
 - Conserva ExpensesSplitter/pbg-release.jks e keystore.properties: servono per aggiornare l'app in futuro con la stessa firma. Non vanno in git.
 - Per installare la release dopo la versione debug, disinstalla prima la debug.
 - Per rigenerare l'APK, i comandi sono in ExpensesSplitter/README.md; basta ./gradlew :app:assembleRelease con il JDK incluso.
 - Il README spiega anche come ricompilare su un'altra macchina o con Android Studio, e dove modificare logica, schema, interfaccia e testi.
 - Ho scaricato JDK 17, Android SDK 34 e Gradle 8.9 in toolchain/ (871 MB). Non ho toccato il sistema; puoi cancellare la cartella quando non ti serve più.

 Il progetto è in ExpensesSplitter/. La specifica ricavata dal reverse engineering è ancora in SPEC_ricostruzione.md.