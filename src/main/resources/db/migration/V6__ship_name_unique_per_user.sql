-- A user can't have two ships with the same name. Two reasons:
--   1. UX: when the orders panel says "Falcon", that's unambiguous.
--   2. Race safety: the player-triggered `POST /api/ships` derives the next
--      auto-name from `countByUserId`. Two concurrent creates by the same
--      user could otherwise both pick "<username>'s ship 2". The
--      pessimistic lock in ShipService.createShipForCurrentUser handles the
--      common case; this constraint is the defence-in-depth that turns any
--      race we missed into a clean 409 instead of duplicate rows.
ALTER TABLE ships
    ADD CONSTRAINT ships_user_id_name_unique UNIQUE (user_id, name);
