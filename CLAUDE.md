# CLAUDE.md

Den här filen riktar sig till två läsare: **kompisen som onboardas** och **Claude som agent**. Båda ska kunna läsa den och direkt börja jobba i projektet utan att fråga.

## Vad projektet är

Ett 2D, tick-baserat rymdspel i webbläsaren — närmast en webbaserad släkting till Elite. Varje spelare styr ett moderskepp på en fix 100×100-grid med planeter och stjärnor. Spelaren lägger destination, skeppet rör sig 1 ruta per tick, och vid ankomst till en planet kan spelaren landa och interagera. Världen tickar i bakgrunden (≤ 1 min) oberoende av om någon är inloggad — det är inte realtids-strategi, snarare "logga in då och då och fatta beslut".

Detaljerad domän-modell: se [DOMAIN.md](DOMAIN.md).

## Kör projektet

```sh
./mvnw spring-boot:run          # Startar app + Postgres (via docker-compose-auto)
./mvnw verify                   # Bygger + tester
./mvnw test                     # Bara tester
docker compose up -d            # Bara Postgres (om du inte vill köra appen)
```

Appen på http://localhost:8080.

## Frontend

- **React + Vite** i `frontend/`. Backend exponerar REST under `/api/**`.
- **PixiJS** för 2D-kartan — en `<canvas>` monterad i en React-komponent. Pixi-koden lever isolerad från React-trädet (Pixi äger sin egen render-loop); React syncar bara state in via props/refs och läser ut event via callbacks.
- **Inte** Phaser — overkill för tick-baserat.
- Animerade siffror via en lättviktig lib (t.ex. `framer-motion` eller `react-spring`); ingen anledning att skriva egna easing-funktioner.
- State: börja med React state + `@tanstack/react-query` för server-state. Lägg inte till Redux/Zustand innan det faktiskt behövs.

## Kodkonventioner

### Paketstruktur
Paketera **per domän**, inte per teknisk lagrtyp:
```
org.example.springbootspacegame
├── ship/           # Entity, Repository, Service, Controller för Ship
├── planet/         # ... för Planet
├── world/          # WorldState + grid-relaterad logik
├── tick/           # Tick-scheduler
└── auth/           # User, login, registrering
```
Inte `controllers/`, `services/`, `repositories/` på toppnivå.

### Lager
- **Controller**: tunna. Tar emot DTO, anropar Service, returnerar DTO. Ingen affärslogik.
- **Service**: all affärslogik. Transaktionsgränser här (`@Transactional`).
- **Repository**: Spring Data interfaces. Inga custom @Query om derived names räcker.
- **Entity vs DTO**: aldrig exponera JPA-entities via REST. Mappa explicit till DTO i Service-lagret.

### Lombok
Använd `@RequiredArgsConstructor` för constructor-injection och `@Getter`/`@Setter` sparsamt. **Inte** `@Data` på entities (equals/hashCode på JPA-entities är en fälla).

### Testnamngivning
`<KlassUnderTest>Test` för unit, `<KlassUnderTest>IT` för integration. Integration tests använder Testcontainers — **mocka aldrig databasen**.

### Migrations
Schema-ändringar görs **alltid** via Flyway. Filer i `src/main/resources/db/migration/` med format `V<n>__<beskrivning>.sql`. Aldrig `spring.jpa.hibernate.ddl-auto=update` — den står på `validate` av en anledning.

## Arbetsflöde

### Branchar
- `feature/<kort-beskrivning>` för nya features
- `fix/<kort-beskrivning>` för buggfixar
- `chore/<kort-beskrivning>` för städning, deps, CI
- Aldrig direkt-push till `main`

### Commits
Fri text på engelska, men hålla varje commit fokuserad på en sak. Imperativ form ("Add tick scheduler", inte "Added" eller "Adds"). Kroppen får förklara *varför* om det inte är uppenbart.

### PRs
- Länka issue: `Closes #N`
- Beskrivning ska säga *varför*, inte bara *vad* (diffen visar vad)
- Håll dem små (< ~400 rader diff). Stora PRs är svåra att granska seriöst — dela upp.
- En review krävs. CI måste vara grön.
- Squash and merge.

## Secrets och miljövariabler

- **Allt känsligt går via `.env`** (gitignored). Mall: `.env.example` (committad, inga riktiga värden).
- Spring läser `.env` automatiskt via `spring.config.import=optional:file:.env[.properties]`. Format: properties-stil (`key=value`, ingen `export`-prefix).
- `compose.yaml` läser `POSTGRES_*` med fallback till defaults — lokala devs kan strunta i `.env` helt.
- Lägg till nya secrets så här:
  1. Lägg raden i `.env.example` med tomt eller placeholder-värde.
  2. Lägg det riktiga värdet i din lokala `.env`.
  3. Dela värdet med kompisen out-of-band (Signal / lösenordshanterare / muntligt).
- CI/prod-secrets ska in i GitHub Actions Secrets eller hosting-providerns equivalent — **inte** i `.env` committad nånstans.
- Om en secret hamnar i git: rotera den omedelbart. Att ta bort den i en ny commit räcker inte — den finns kvar i historiken.

## Vad man INTE ska göra

- **Pusha direkt till `main`** — gå alltid via PR.
- **Mocka databasen i integrationstester** — använd Testcontainers. Mock-tester ljuger om Postgres-specifikt beteende.
- **Lägga affärslogik i Controllers** — controllern är ett transport-lager.
- **Exponera JPA-entities via REST** — alltid mappa till DTO.
- **Ändra DB-schemat utan Flyway-migration**.
- **Använda `@Data` på entities** (equals/hashCode-trubbel).
- **Committa `.env`, API-nycklar, lösenord eller andra secrets** — se sektionen ovan.

## Domän-vokabulär

Använd dessa termer konsekvent i kod, commits, issues och diskussioner. Detaljerad modell i [DOMAIN.md](DOMAIN.md).

- **User** — Personen bakom kontot. Autentiseringsidentitet, ingen gameplay-state.
- **Ship** — Spelarens moderskepp. I v1 har varje User exakt ett Ship; schemat tillåter fler för framtida fleet.
- **Planet** — Pre-seedad punkt på kartan som ett Ship kan landa på.
- **Tile** — En ruta på 100×100-griden, identifierad av `(x, y)`. Lagras inte som tabell — bara intressanta saker (Ship, Planet) har koordinater.
- **Tick** — Ett återkommande tidsintervall (≤ 1 min) då världen processas: skepp i rörelse flyttas, framtida feature-effekter triggas. Schemaläggs centralt.
- **World** — Den globala staten alla spelare delar (grid-storlek, current tick). En `WorldState`-singleton-rad.

Termer som **inte** används (för att undvika förvirring):
- "turn" — vi har ticks, inte turer.
- "round" / "match" — det är ingen session-baserad spelvärld.
- "kingdom" / "empire" — det här är inte ett kingdom-builder-spel.

## När du som Claude jobbar i repot

- Kolla [README.md](README.md) först om något i den här filen är otydligt.
- Föreslå inte kod som bryter mot konventionerna ovan utan att flagga det.
- Innan du föreslår en ny dependency: kolla om Spring Boot-parentet redan hanterar den.
- Om en uppgift känns för stor för en PR: föreslå att dela upp den i flera issues istället för att bara köra på.

### Håll dokumentationen levande

Efter varje uppgift som ändrar projektet — ny feature, ny dependency, nytt arkitekturval, ny domän-term, ny konvention — **gå tillbaka och kontrollera** att följande filer fortfarande är korrekta:

- `CLAUDE.md` — domän-vokabulär, paketstruktur, konventioner, anti-patterns
- `DOMAIN.md` — entitets-schema, deferred features, forward-compat-notes
- `README.md` — stack, kom-igång-kommandon, projektstruktur

Om något står fel eller är ofullständigt:
1. Flagga det för användaren med en kort beskrivning av vad som inte stämmer längre.
2. Föreslå konkret ändring (helst som diff eller exakt ny text).
3. Inkludera dokumentationsändringen i **samma PR** som kod-ändringen — inte som en följd-PR. Stale docs som lever en vecka räcker för att förvirra någon.

Det här gäller även när användaren *inte* ber om en doc-uppdatering. Tysta dokumentations-drift är en av de största orsakerna till att CLAUDE.md tappar värde över tid.
