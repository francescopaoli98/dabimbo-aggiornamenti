# dabimbo-aggiornamenti

Aggiornamenti dell'app **Da bimbo a bimbo** (uso privato, fuori dal Play Store).

- `versione.json`: numero dell'ultima versione, indirizzo dell'app e una nota.
- `dabimbo.apk`: l'ultima versione dell'app.

All'apertura l'app legge `versione.json`: se la versione è più nuova di quella installata,
propone "Aggiorna", scarica `dabimbo.apk` e apre l'installazione di Android.
