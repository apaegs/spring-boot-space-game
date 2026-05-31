# CLAUDE.md

Den här filen riktar sig till två läsare: **kompisen som onboardas** och **Claude som agent**. Båda ska kunna läsa den och direkt börja jobba i projektet utan att fråga.

## Vad projektet är

Ett 2D, tick-baserat multiplayer-spel i webbläsaren — inspirerat av Utopia, Star Kingdoms och Clash of Titans. Varje spelare har ett "kingdom" som producerar resurser och armé över tid. Världen tickar i intervaller (t.ex. var X:e minut) och spelare lägger order asynkront. Inte realtids-strategi, inte heller hot-seat — närmast en webbaserad ekonomi/krigssim där "att vara aktiv" betyder att logga in och fatta beslut, inte att klicka snabbt.

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
├── kingdom/        # Entity, Repository, Service, Controller för Kingdom
├── resource/       # ... för Resource
├── tick/           # Tick-scheduler och world state
└── auth/           # Användare, login, registrering
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

Använd dessa termer konsekvent i kod, commits, issues och diskussioner.

- **Kingdom** — En spelares spelade enhet. En spelare har exakt ett kingdom åt gången.
- **Tick** — Ett återkommande tidsintervall då världen processas: resurser produceras, byggen blir klara, händelser triggas. Schemaläggs centralt, inte per kingdom.
- **Resource** — Producerbara/förbrukbara råvaror (t.ex. guld, mat, järn). Hör till ett Kingdom.
- **Order** — Ett beslut en spelare lägger som verkställs vid nästa (eller framtida) tick. Asynkron input — spelaren behöver inte vara online när ordern processas.
- **World** — Den globala staten som alla kingdoms delar (kartan, nuvarande tick-nummer, etc.).
- **Player / User** — Personen bakom kontot. Skiljt från Kingdom — en User kan teoretiskt ha flera kingdoms över tid men bara ett aktivt.

Termer som **inte** används (för att undvika förvirring): "turn" (vi har ticks, inte turer), "round" (en spelvärld har inte rundor), "match" (det är inte session-baserat).

## När du som Claude jobbar i repot

- Kolla [README.md](README.md) först om något i den här filen är otydligt.
- Föreslå inte kod som bryter mot konventionerna ovan utan att flagga det.
- Innan du föreslår en ny dependency: kolla om Spring Boot-parentet redan hanterar den.
- Om en uppgift känns för stor för en PR: föreslå att dela upp den i flera issues istället för att bara köra på.
