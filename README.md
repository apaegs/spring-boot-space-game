# spring-boot-space-game

Ett 2D, tick-baserat multiplayer-spel i webbläsaren, inspirerat av Utopia, Star Kingdoms och Clash of Titans. Spelarna bygger upp sina riken parallellt och världen tickar fram resurser/händelser i bakgrunden.

## Stack

- **Backend**: Spring Boot 4 (Java 25), Spring MVC, Spring Security, Spring Data JPA
- **Databas**: PostgreSQL 16 + Flyway-migrations
- **Frontend**: React + Vite (SPA) med PixiJS för 2D-kartan (scaffoldas i [issue #4](../../issues))
- **Tester**: JUnit 5 + Testcontainers (riktig Postgres, inga mocks)
- **CI**: GitHub Actions

## Kom igång lokalt

Krav: JDK 25, Docker (för Postgres), Git.

```sh
# 1. Klona och gå in
git clone <repo-url>
cd spring-boot-space-game

# 2. Starta backend — Spring upptäcker compose.yaml och startar Postgres automatiskt
./mvnw spring-boot:run

# 3. Kör tester
./mvnw verify
```

Appen lyssnar på http://localhost:8080.

Om du föredrar att starta Postgres separat:
```sh
docker compose up -d
```

## Arbetsflöde

Vi kör tydligt PR-flöde — **ingen direkt-push till `main`**.

1. Hitta eller skapa ett **issue** som beskriver vad som ska göras.
2. Skapa en branch: `feature/<kort-beskrivning>` eller `fix/<kort-beskrivning>`.
3. Commit:a små, fokuserade ändringar.
4. Öppna **pull request** mot `main`. Länka issuet (`Closes #N`).
5. Den andre **reviewar** och godkänner.
6. CI måste vara grön innan merge.
7. Merge via "Squash and merge".

Mer detaljer om konventioner finns i [CLAUDE.md](CLAUDE.md).

## Projektstruktur

```
.
├── src/main/java/org/example/springbootspacegame/   # Backend-kod, paket per domän
├── src/main/resources/
│   ├── application.properties                       # Konfiguration
│   ├── db/migration/                                # Flyway SQL-migrationer (V1__...)
│   └── static/                                      # (frontend-build hamnar här senare)
├── src/test/java/                                   # Tester
├── compose.yaml                                     # Lokal Postgres
├── pom.xml
└── .github/workflows/ci.yml                         # CI
```

Frontend hamnar i en separat mapp (`frontend/`) som scaffoldas i [issue #4](../../issues).
