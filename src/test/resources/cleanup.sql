-- Truncate everything tests touch so each @Test starts from a known-empty state.
-- CASCADE handles FK-linked rows (e.g. ships → users) automatically, so new tables
-- with a FK to users get cleaned for free — but list them explicitly anyway, so this
-- script stays self-documenting as a "tables that hold per-test data" inventory.
--
-- Do NOT truncate flyway_schema_history — Flyway tracks migrations there and a wiped
-- history would re-run migrations on the next test, breaking everything.
TRUNCATE TABLE users CASCADE;
