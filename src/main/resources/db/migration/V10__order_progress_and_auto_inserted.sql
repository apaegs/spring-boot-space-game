-- PR 2 of the resource system (#80, issue #46 v3 design):
-- gameplay-backend additions on the order queue.
--
-- 1. progress_ticks — per-order counter incremented by the EXTRACT handler
--    every tick it actually extracts something. The mode={"ticks": N}
--    branch compares this against N to decide when to complete. Plain INT
--    column, NOT NULL DEFAULT 0, so existing MOVE/LAND rows aren't touched.
--    Plain integer field lets Hibernate's dirty checking catch updates
--    reliably (mutating the params JSONB Map in place is fragile under
--    Hibernate's JSON handling).
--
-- 2. auto_inserted — was this order created by the auto-prerequisite
--    middleware (LAND before EXTRACT/SELL, TAKE_OFF before MOVE), or by
--    the player directly? PR 3 (frontend) uses this to render an
--    "↩ auto" badge so the player can tell which rows they didn't click.

ALTER TABLE ship_orders ADD COLUMN progress_ticks INT     NOT NULL DEFAULT 0 CHECK (progress_ticks >= 0);
ALTER TABLE ship_orders ADD COLUMN auto_inserted  BOOLEAN NOT NULL DEFAULT FALSE;
