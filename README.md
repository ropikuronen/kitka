# ScrollSlayer

Oma Android-sovellus somen skrollauksen vähentämiseen. Vain itselle, ei Play Kauppaa.

Sovellus tekee kaksi asiaa:

1. **Portti.** Kun avaat kohdesovelluksen (Instagram, TikTok, YouTube jne.), päälle tulee koko näytön kerros. Sulje toimii heti. Jatka aukeaa 10 sekunnin päästä. Jos jatkat, portti tulee uudestaan 10 minuutin putken jälkeen.
2. **Luvut.** Minuutit per sovellus (Androidin omista käyttötilastoista), avausyritykset, sulkemiset, jatkamiset ja arvio pyyhkäisyistä. Tänään ja 7 päivää.

Kohdesovelluksen lisäys toimii heti. Poisto astuu voimaan 24 tunnin päästä.

## Miksi juuri näin

Jokainen ominaisuus perustuu johonkin tutkittuun. Muuta ei ole lisätty.

| Ominaisuus | Peruste |
|---|---|
| Sulje-nappi heti näkyvissä | Grüning ym. 2023, PNAS: sulkemisvaihtoehto oli one secin tehokkain osa. |
| 10 s viive ennen Jatka-nappia | Sama koe: viive vähensi avauksia myös, mutta vähemmän. |
| Ei motivaatio- tai pohdintaviestejä | Sama koe: pohdintaviestillä ei ollut vaikutusta. |
| Poisto 24 h viiveellä | Allcott, Gentzkow & Song 2022, AER: rajojen asettaminen etukäteen vähensi käyttöä selvästi. Haliburton ym. 2024, CHI: käyttäjät pitivät taukoja kitkasta ja palasivat nopeasti ylikäyttöön. |
| Luvut näkyvissä | Pelkkä seuranta ei tutkimuksissa vähentänyt käyttöä (Zimmermann & Sobolev). Luvut ovat siksi vain sitä varten, että näet toimiiko tämä sinulla. |

Rehellisesti: näyttö on lyhyen aikavälin vaikutuksista (viikkoja). Mikään tutkimus ei lupaa pysyvää lopettamista.

## Asennus puhelimeen

1. Avaa puhelimen selaimessa (kirjautuneena GitHubiin):
   https://github.com/ropikuronen/kitka/releases/latest/download/app.apk
2. Salli asennus selaimesta, kun Android kysyy. Asenna.
3. Avaa sovellus ja seuraa käyttöönottoa:
   - Esteettömyys > Asennetut sovellukset > ScrollSlayer > päälle.
   - Jos tulee "Rajoitettu asetus": Sovelluksen tiedot > kolme pistettä oikealla ylhäällä > Salli rajoitetut asetukset. Sitten uudestaan esteettömyyteen.
   - Käyttötilastojen käyttöoikeus > ScrollSlayer > salli.

Päivitys: lataa sama linkki uudestaan ja asenna päälle. Data säilyy, koska jokainen build allekirjoitetaan samalla avaimella (`app/debug.keystore`).

## Build

Jokainen push `main`-haaraan buildaa APK:n GitHub Actionsissa ja julkaisee sen `latest`-releaseen. Paikallisesti:

```
gradle :app:assembleDebug
```

## Mitä tämä ei tee

- Ei estä sinua poistamasta sovellusta tai sammuttamasta palvelua.
- Ei lue näytön sisältöä. Palvelu näkee vain, mikä sovellus on auki, ja scroll-tapahtumat.
- Ei lähetä mitään minnekään. Kaikki on puhelimen omassa tietokannassa.

## Lähteet

- Grüning, Riedel & Lorenz-Spreen (2023). Directing smartphone use through the self-nudge app one sec. PNAS 120(8). https://doi.org/10.1073/pnas.2213114120
- Haliburton ym. (2024). A Longitudinal In-the-Wild Investigation of Design Frictions to Prevent Smartphone Overuse. CHI '24. https://doi.org/10.1145/3613904.3642370
- Allcott, Gentzkow & Song (2022). Digital Addiction. American Economic Review 112(7). https://doi.org/10.1257/aer.20210867
- Zimmermann & Sobolev (2023). Digital Strategies for Screen Time Reduction: A Randomized Field Experiment. Cyberpsychology, Behavior, and Social Networking 26(1). https://doi.org/10.1089/cyber.2022.0027
