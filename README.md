# Expenses Splitter

App Android local-first per dividere le spese di gruppo (viaggi, casa, eventi). Nessun account, nessun permesso di rete, nessuna pubblicità.
Package `com.pbg.expensessplitter` - sviluppata da **Python Biella Group**. Ispirata alla dismessa *Expense Splitter* di Superwheel Software (specifica ricavata per reverse engineering, vedi `docs/SPEC_ricostruzione.md` e `docs/REA OUTPUT v.1.0.md`; nessun codice o risorsa dell'originale è stato copiato).

## Funzioni
- Viaggi/eventi, partecipanti (anche senza app), spese con importo, causale, data, pagatore, categoria, valuta + cambio manuale, foto scontrino (galleria o fotocamera).
- Divisione: pari quote, quote fisse, percentuali (importi in centesimi interi, resti ripartiti col metodo del resto maggiore).
- Saldi netti e **numero minimo di trasferimenti** (ricerca esatta fino a 20 persone con DP su bitmask, greedy oltre). "Registra pagamento" aggiunge un pareggio parziale o totale.
- Report **TXT** e **CSV** (separatore `;`, UTF-8 con BOM: si apre direttamente in Excel italiano) condivisibili da chat/e-mail.
- **Backup/ripristino**: JSON (`schemaVersion`, importazione = aggiunge viaggi) e `.db` (copia SQLite, il ripristino sostituisce tutto dopo verifica di integrità e schema). Il file si salva dove vuoi, anche su Google Drive, tramite il selettore di sistema.
- Import dal database dell'app originale (`Splitter`).
Limiti: le foto degli scontrini NON sono incluse nei backup; le valute sono trattate con 2 decimali.

## Compilare l'APK in autonomia
Requisiti: JDK 17, Android SDK (platform 34, build-tools 34.0.0), connessione per scaricare Gradle/dipendenze.
Durante lo sviluppo la toolchain era in `../toolchain` (JDK, SDK, Gradle), cartella locale non versionata. Da una shell bash (Git Bash):

```bash
cd ExpensesSplitter
export JAVA_HOME="$(cd ../toolchain/jdk-17*; pwd)"; export PATH="$JAVA_HOME/bin:$PATH"
./gradlew :app:testDebugUnitTest      # test della logica (split, saldi, minimizzazione, report, JSON)
./gradlew :app:assembleDebug          # -> app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleRelease        # -> app/build/outputs/apk/release/app-release.apk (firmato)
```
PowerShell: `$env:JAVA_HOME="..\toolchain\jdk-17.0.20.1+1"; .\gradlew.bat :app:assembleRelease`.
Su un'altra macchina: installa JDK 17 e l'Android SDK (`sdkmanager "platforms;android-34" "build-tools;34.0.0"`), crea `local.properties` con `sdk.dir=C:/percorso/android-sdk` (barre normali) ed esegui gli stessi comandi con `gradlew`.
Oppure apri la cartella `ExpensesSplitter` con Android Studio (Koala o più recente) e usa *Build > Build APK(s)*.

### Firma
`assembleRelease` usa `keystore.properties` + `pbg-release.jks` (nella cartella del progetto, ignorati da git). Senza `keystore.properties` produce un APK non firmato.
**Conserva il file .jks e le password**: servono per gli aggiornamenti futuri (stessa firma). Nuova chiave:
`keytool -genkeypair -keystore pbg-release.jks -alias pbg -keyalg RSA -keysize 2048 -validity 10000` e aggiorna `keystore.properties` (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`).
Installazione: `adb install -r app-release.apk` oppure copia l'APK sul telefono e abilita "installa app sconosciute". Se hai installato prima la versione debug, disinstalla prima (firma diversa).

## Modifiche manuali
- Logica: `app/src/main/java/com/pbg/expensessplitter/core/` (puro Kotlin, testato in `app/src/test`).
- Database Room: `data/` (versione schema 1; se cambi le entità incrementa `version` in `AppDb` e aggiungi una migration).
- UI Compose: `ui/`. Testi: `app/src/main/res/values/strings.xml` (EN) e `values-it/strings.xml` (IT).
- Nome/package/versione: `app/build.gradle.kts` (`applicationId`, `versionCode`, `versionName`) e `namespace` (cambiare il package dei sorgenti è una refactor completa).

## Portare i dati della vecchia app
1. `python tools/extract_legacy_db.py backup_dati.ab Splitter.db` (il backup `.ab` dell'app originale non è nel repository: va recuperato dal proprio backup locale).
2. Copia il file sul telefono e in Expenses Splitter: menu ⋮ > *Importa da Expense Splitter originale*.
Conversioni: float -> centesimi, date italiane ("09 mag 2017 19:00") -> data/ora reali, splitter orfani scartati, "Transfer" -> pagamento.
