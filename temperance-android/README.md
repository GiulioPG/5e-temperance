# 5e - Temperance per Android

Questo progetto impacchetta la web app **5e - Temperance** (`app/src/main/assets/index.html`)
in un'app Android nativa: icona, schermo intero, tasto Indietro, scelta dei file, salvataggio
dei file, finestre di dialogo e importazione da 5e.tools senza i limiti del browser.

L'APK **non è incluso**: va compilato una volta (sotto trovi tre modi, il più semplice è A).
Serve una connessione a internet solo per la compilazione.

## A. Con Android Studio (consigliato)
1. Installa Android Studio (gratuito) e apri questa cartella con **File > Open**.
2. Attendi la sincronizzazione di Gradle (scarica da solo ciò che serve).
3. Menu **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
4. Il file è in `app/build/outputs/apk/debug/app-debug.apk`.
5. Copialo sul telefono e aprilo (Android chiede di consentire l'installazione da questa origine).

## B. Senza installare nulla: GitHub
1. Crea un repository su GitHub e carica il contenuto di questa cartella.
2. Vai nella scheda **Actions**, scegli **Build APK** e premi **Run workflow**
   (parte anche da solo a ogni caricamento).
3. Al termine scarica l'artefatto **temperance-apk**: dentro c'è `app-debug.apk`.

## C. Da riga di comando
Servono JDK 17, l'Android SDK (piattaforma 34) e Gradle 8.5:

    gradle assembleDebug

## Aggiornare l'app
Sostituisci `app/src/main/assets/index.html` con la nuova versione di `schede-dnd.html`
(rinominandola `index.html`) e compila di nuovo. I dati dei personaggi restano nel telefono.
Prima di disinstallare l'app esporta i dati da **Opzioni > Esporta tutto (JSON)**.

## Note
- L'APK generato è firmato con la chiave di debug: va bene per uso personale. Per una versione
  da pubblicare sul Play Store serve una chiave tua (`keytool -genkey ...`) e un blocco
  `signingConfigs` in `app/build.gradle`; l'icona da 512 px è in `play-store-icon-512.png`.
- Dettagli per i telefoni pieghevoli: l'attività non si riavvia quando apri o chiudi il telefono
  e accetta qualsiasi dimensione di finestra.
- La stampa della scheda non è disponibile nell'app (la WebView non la supporta).
