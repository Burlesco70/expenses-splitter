# Expense Splitter (com.superwheel.expensesplitter) – specifica per la ricostruzione

Fonti: `com.superwheel.expensesplitter.apk` (SHA-256 `0fd9c8ee…0015d`), `backup_dati.ab` (non cifrato, v5, compresso),
DB `Splitter` estratto in `work/x/apps/com.superwheel.expensesplitter/db/`.
Metodo: provider JADX di REA non disponibile (`REA_JADX_MCP_JAR` non impostata); REA `open_binary` ha dato solo inventario APK.
Il bytecode è stato disassemblato con androguard (venv locale in `work/venv`). Le classi sono offuscate (ProGuard), quindi i nomi sotto sono dedotti.

## Osservato (dal DB e dal bytecode)

### Schema SQLite (`Splitter`; coincide con le stringhe `create table` nel dex)
```
tbl_trips     (_id pk, trip_name NOT NULL, trip_desc, trip_currency)
tbl_persons   (_id pk, name NOT NULL, email, phone, tripid)
tbl_expenses  (_id pk, name NOT NULL, tripid, payerid, amount float, currency, currencyfactor float, dateadded string)
tbl_splitters (expenseid, splitterid)
```
Dati reali: 61 viaggi, 218 persone, 1522 spese, 3827 righe splitter. Valute: EUR (1497) e HRK (25, fattore ≈ 0,14).
`dateadded` è testo locale italiano, ad es. `"09 mag 2017 19:00"`, quindi da normalizzare in migrazione.
SharedPreferences `AppPrefs`: `tripID` (viaggio attivo), `lastCurrency`, `eulaOK`.

### Cosa NON esiste nell'originale (rispetto alla richiesta)
- Nessuna quota fissa o percentuale: `tbl_splitters` contiene solo i partecipanti, la divisione è sempre in parti uguali.
- Nessuna categoria e nessun allegato/foto.
- Nessun CSV, nessun backup/ripristino. Il report è testo inviato via `mailto:` o SMS (`SmsManager`).
- Il settle-up è una spesa speciale ("Transfer"): `TransferMoneyActivity`, con campi pagatore/ricevente/importo/valuta.
  Il codice riconosce anche la stringa `_moneytransfer_` come nome. Nel DB c'è 1 solo trasferimento (`"Transfer "`, id 292, payer 50, splitter 49 e 50).
  Lo splitter 50 sembra un'anomalia, quindi non copiare questo caso alla cieca.
- Contiene AdMob (`com.google.ads`): da non riportare.

### Algoritmo di calcolo (`CalculateActivity.e()`, `u`, `s`, `t`)
1. Per ogni persona del viaggio: `pagato` e `dovuto` partono da 0.
2. Per ogni spesa: `importo = amount`, moltiplicato per `currencyfactor` se `currency != trip_currency`.
   - `pagato[payer] += importo`
   - per ogni splitter: `dovuto[splitter] += importo / n_splitter`
3. `saldo = pagato − dovuto`. Lista ordinata per saldo decrescente (`s`).
4. Ciclo greedy: si prendono il saldo minimo (debitore) e il massimo (creditore).
   `x = min(|debitore|, creditore)`, si registra "debitore deve pagare x a creditore", si aggiornano i saldi.
   Si ferma quando tutti i saldi sono ≤ 0,01 (costante double `0x3F847AE147AE147C`).
   Il Toast "Balance: 0" compare se non resta nulla da saldare.
5. Output formattato con `%.2f` + codice valuta del viaggio.
   Mail: oggetto = "Report <nome viaggio>", corpo = elenco "A deve a B: x EUR", poi le spese dettagliate. Destinatari = email delle persone separate da `;`.

L'algoritmo è greedy, non una minimizzazione ottimale dei trasferimenti (come promette la tua descrizione). Il massimo osservato è 11 transazioni in un viaggio.
Per soddisfare "5 debiti → 2" conviene partire da questo greedy e migliorarlo.
Il model Float a 32 bit introduce errori di arrotondamento, quindi nel rifacimento usare interi in centesimi.

### Verifica
`work/legacy_settle.py` è la mia reimplementazione del calcolo. Su tutti i 61 viaggi reali la somma dei saldi è 0,0000.
Questo conferma il modello pagato/dovuto/splitter. Non ho invece confrontato i trasferimenti con quelli mostrati dall'app originale (non eseguita).

## Inferito / incerto
- Il tie-break preciso del greedy e l'ordine dei risultati dipendono dall'ordinamento `Collections.sort` e dai float: da replicare con test se serve identità esatta.
- Tabs UI: Spese, Persone, Dividi (calcolo), Viaggi (da `help.txt`). Layout in `work/apk/res/layout`.
- Valute: lista in `res/raw/currency.txt` (160 voci, `CODICE<TAB>nome`), riutilizzabile.
- Il fattore di cambio è inserito a mano per spesa (`expense_currency.xml`), nessuna chiamata di rete per i cambi.

## Proposta per la nuova app (da confermare)
- Android nativo: Kotlin, Jetpack Compose, Room, nessun permesso di rete e nessun SDK pubblicitario.
- Schema Room: `trip`, `person`, `expense` (importo in centesimi, `category`, `receiptUri`), `expense_share` (`expenseId`, `personId`, `shareCents`; split uguale, a quote fisse o percentuali), `settlement` (spesa di tipo trasferimento).
- Esportazione: TXT (formato del report originale), CSV (spese + saldi + trasferimenti) tramite `ACTION_SEND`/`FileProvider`.
- Backup: JSON con `schemaVersion` e `.db` (copia SQLite dopo checkpoint WAL) tramite Storage Access Framework, quindi anche Google Drive. Ripristino con validazione.
- Importatore del DB legacy: legge `Splitter` e converte (float→centesimi, date italiane→ISO, scarta splitter orfani: 16 nel backup).
- Cambia nome dell'applicazione "Expenses splitter" ma cambia package com.pbg.expensessplitter, come credit, developed by "Python Biella Group"

## Step finale
- Genera apk e le istruzioni per generarla in autonomia in caso di modifica "manuale"