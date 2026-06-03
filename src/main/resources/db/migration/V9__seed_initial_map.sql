-- Seed the initial v1 map: 40 celestial bodies spread across the 100×100 grid
-- in a deliberate mix of kinds (rocky, asteroid, lava, ice, gas giant, star),
-- with per-body resource reserves and per-body buy prices.
--
-- This REPLACES the V5 planet seed entirely. The names are real-world-ish; the
-- coordinates form a deliberate "inner cluster around Earth → mid ring →
-- outer frontier" topology so that:
--   - new players (Earth at 50,50) have nearby low-reserve buyers and need to
--     fly outward for serious extraction;
--   - the gas giants live in the mid ring so HYDROGEN/HELIUM trading is a
--     mid-game pivot, not an opener;
--   - the rare lava planets sit far from spawn — RARE_METAL is the long-haul
--     prize.
--
-- Body UUIDs are deterministic ('...0001' through '...0040') so tests can
-- reference specific bodies and so re-running this migration in a dev DB
-- (rare, but possible after a manual wipe) lands the same identity for the
-- same name. Real production seeding goes through Flyway exactly once.
--
-- Body × resource yield matrix used here (design issue #46, v3 comment):
--   ✚   ≈  100–500
--   ✚✚  ≈ 1,000–4,000
--   ✚✚✚ ≈ 5,000–20,000

-- Clear the original V5 seed. CASCADE drops any FKs that would land in PR 2;
-- in this PR ships have no body FK so this is just defensive housekeeping.
DELETE FROM celestial_bodies;

-- =============================================================================
-- 1. Bodies
-- =============================================================================

INSERT INTO celestial_bodies (id, x, y, name, kind, description) VALUES
    -- Inner cluster around Earth (the spawn): industrial heart, a few buyers,
    -- not much left to extract.
    ('00000000-0000-0000-0000-000000000001', 50, 50, 'Earth',          'ROCKY_PLANET', 'The blue homeworld. Where every player''s journey begins. Industrial heart of the inner system.'),
    ('00000000-0000-0000-0000-000000000002', 60, 55, 'Mars',           'ROCKY_PLANET', 'Rust-red and quiet. A weathered industrial post.'),
    ('00000000-0000-0000-0000-000000000003', 48, 52, 'Luna',           'ROCKY_PLANET', 'Earth''s pale companion. Mostly mined out.'),
    ('00000000-0000-0000-0000-000000000004', 52, 46, 'Venus',          'ROCKY_PLANET', 'Hot, choked, but rich in trace metals. Tech traders dock here.'),
    ('00000000-0000-0000-0000-000000000005', 55, 58, 'Ceres',          'ASTEROID',     'Largest of the inner belt. A mining outpost in everything but name.'),
    ('00000000-0000-0000-0000-000000000006', 45, 54, 'Vesta',          'ASTEROID',     'Bright, irregular, iron-rich. Popular first stop for new pilots.'),
    ('00000000-0000-0000-0000-000000000007', 54, 42, 'Eros',           'ASTEROID',     'Elongated, fast-spinning. Small reserves but easy approach.'),
    ('00000000-0000-0000-0000-000000000008', 43, 48, 'Pallas',         'ASTEROID',     'Tilted orbit, ancient. Trace rare metals draw the curious.'),

    -- Mid ring: most of the gameplay surface. Gas giants here, ice moons,
    -- the first lava planet, plus enough rocky bodies to anchor the matrix.
    ('00000000-0000-0000-0000-000000000009', 35, 50, 'Mercury',        'ROCKY_PLANET', 'Sunbaked and small. Closer to the inner stars.'),
    ('00000000-0000-0000-0000-000000000010', 30, 70, 'Jupiter',        'GAS_GIANT',    'Gas giant with a storm older than recorded history. Hydrogen and helium for those who orbit.'),
    ('00000000-0000-0000-0000-000000000011', 70, 30, 'Saturn',         'GAS_GIANT',    'Ringed and serene. A favoured trading orbit on the eastern half of the map.'),
    ('00000000-0000-0000-0000-000000000012', 28, 68, 'Europa',         'ICE_PLANET',   'Ice shell over a deep ocean. Habitat colonies pay well for water.'),
    ('00000000-0000-0000-0000-000000000013', 72, 32, 'Titan',          'ICE_PLANET',   'Hazy nitrogen sky over hydrocarbon seas. Water and a whisper of hydrogen.'),
    ('00000000-0000-0000-0000-000000000014', 32, 72, 'Io',             'LAVA_PLANET',  'Volcanic and torn. The rare-metal yield is unmatched in the mid ring.'),
    ('00000000-0000-0000-0000-000000000015', 26, 72, 'Ganymede',       'ROCKY_PLANET', 'Largest moon of Jupiter''s neighbourhood. Solid rocky reserves.'),
    ('00000000-0000-0000-0000-000000000016', 33, 75, 'Callisto',       'ROCKY_PLANET', 'Cratered and ancient. Quiet. Some iron.'),
    ('00000000-0000-0000-0000-000000000017', 68, 28, 'Enceladus',      'ICE_PLANET',   'Tiny, geyser-bright. Water erupts straight to orbit.'),
    ('00000000-0000-0000-0000-000000000018', 74, 33, 'Mimas',          'ASTEROID',     'Looks like a moon, classified as an asteroid. Modest yield.'),
    ('00000000-0000-0000-0000-000000000019', 25, 75, 'Triton',         'ICE_PLANET',   'Retrograde, cold. The water buyers of the inner system pay handsomely.'),
    ('00000000-0000-0000-0000-000000000020', 35, 30, 'Hebe',           'ASTEROID',     'Bright A-type. Compact, accessible.'),
    ('00000000-0000-0000-0000-000000000021', 25, 25, 'Pluto',          'ROCKY_PLANET', 'Cold rocky world. Habitats here pay for water and hydrogen.'),
    ('00000000-0000-0000-0000-000000000022', 75, 75, 'Eris',           'ROCKY_PLANET', 'Scattered disc world. Iron with a side of rare metal.'),
    ('00000000-0000-0000-0000-000000000023', 20, 40, 'Haumea',         'ROCKY_PLANET', 'Spinning fast, dense core. Tech traders buy what comes out of Io and Lava.'),
    ('00000000-0000-0000-0000-000000000024', 40, 80, 'Psyche',         'ASTEROID',     'M-class metallic asteroid. Iron-heavy.'),

    -- Outer frontier: hardest to reach, biggest reserves, the only lava planet
    -- on the eastern half, the stars used purely as nav landmarks.
    ('00000000-0000-0000-0000-000000000025', 10, 10, 'Proxima B',      'ROCKY_PLANET', 'Tidally locked terrestrial. The nearest neighbour of the outer cluster.'),
    ('00000000-0000-0000-0000-000000000026', 90, 90, 'Sirius',         'STAR',         'A binary system. Bright, hot, and on the far edge of the map. Pure landmark.'),
    ('00000000-0000-0000-0000-000000000027', 12, 12, 'Alpha Centauri', 'ROCKY_PLANET', 'Triple-star companion. Industrial buyers cluster here.'),
    ('00000000-0000-0000-0000-000000000028', 88, 88, 'Barnard''s Star','STAR',         'Old red dwarf. Quiet, decorative.'),
    ('00000000-0000-0000-0000-000000000029', 8,  15, 'Wolf 359',       'STAR',         'Faint red dwarf on the far western edge. Reference point only.'),
    ('00000000-0000-0000-0000-000000000030', 15, 8,  'Tau Ceti',       'ROCKY_PLANET', 'Sun-like, southwest frontier. Habitat colony — pays for water.'),
    ('00000000-0000-0000-0000-000000000031', 85, 15, 'Lalande',        'ROCKY_PLANET', 'Eastern flank, modest. Industrial buyer for iron.'),
    ('00000000-0000-0000-0000-000000000032', 15, 85, 'Astraea',        'ASTEROID',     'Outer belt asteroid, far from spawn. Trace yields.'),
    ('00000000-0000-0000-0000-000000000033', 5,  50, 'Gliese 581',     'LAVA_PLANET',  'Western edge, the second lava world. Long flight from spawn, rich rare-metal reserves.'),
    ('00000000-0000-0000-0000-000000000034', 95, 50, 'Kepler-22b',     'ICE_PLANET',   'Far-east ice world. The water-buyer cluster on the eastern flank pivots around it.'),
    ('00000000-0000-0000-0000-000000000035', 50, 5,  'HD 209458',      'GAS_GIANT',    'Hot Jupiter on the southern frontier. Helium-rich.'),
    ('00000000-0000-0000-0000-000000000036', 50, 95, '51 Pegasi',      'GAS_GIANT',    'Northern frontier gas giant. The classic discovery.'),
    ('00000000-0000-0000-0000-000000000037', 5,  5,  'TRAPPIST-1',     'STAR',         'Small ultra-cool dwarf in the corner. Nav landmark.'),
    ('00000000-0000-0000-0000-000000000038', 95, 5,  'Hygiea',         'ASTEROID',     'Outer asteroid, water-iron mix.'),
    ('00000000-0000-0000-0000-000000000039', 5,  95, 'Iris',           'ASTEROID',     'Cold outer belt. Trace rare metal.'),
    ('00000000-0000-0000-0000-000000000040', 95, 95, 'TOI 700 d',      'ICE_PLANET',   'Habitable-zone ice. Outermost water source.');

-- =============================================================================
-- 2. Reserves (body × resource)
-- =============================================================================
-- One row per (body, resource) with reserve > 0. Absent rows = body lacks that
-- resource (NOT depleted — see the V8 comment on the schema).

INSERT INTO body_resources (body_id, resource_kind, reserve) VALUES
    -- Inner cluster: low-to-moderate reserves; the inner system has been
    -- worked for generations.
    ('00000000-0000-0000-0000-000000000001', 'IRON',       2000),  -- Earth, mined out
    ('00000000-0000-0000-0000-000000000001', 'WATER',      300),
    ('00000000-0000-0000-0000-000000000002', 'IRON',       6000),  -- Mars, decent reserves
    ('00000000-0000-0000-0000-000000000002', 'RARE_METAL', 200),
    ('00000000-0000-0000-0000-000000000003', 'IRON',       1500),  -- Luna
    ('00000000-0000-0000-0000-000000000004', 'IRON',       4000),  -- Venus
    ('00000000-0000-0000-0000-000000000004', 'RARE_METAL', 400),
    ('00000000-0000-0000-0000-000000000005', 'IRON',       3000),  -- Ceres
    ('00000000-0000-0000-0000-000000000005', 'WATER',      200),
    ('00000000-0000-0000-0000-000000000006', 'IRON',       2500),  -- Vesta
    ('00000000-0000-0000-0000-000000000007', 'IRON',       1200),  -- Eros
    ('00000000-0000-0000-0000-000000000008', 'IRON',       1800),  -- Pallas
    ('00000000-0000-0000-0000-000000000008', 'RARE_METAL', 150),

    -- Mid ring: full diversity, healthy reserves.
    ('00000000-0000-0000-0000-000000000009', 'IRON',       7000),  -- Mercury
    ('00000000-0000-0000-0000-000000000010', 'HYDROGEN',  18000),  -- Jupiter
    ('00000000-0000-0000-0000-000000000010', 'HELIUM',     3500),
    ('00000000-0000-0000-0000-000000000011', 'HYDROGEN',  15000),  -- Saturn
    ('00000000-0000-0000-0000-000000000011', 'HELIUM',     3000),
    ('00000000-0000-0000-0000-000000000012', 'WATER',     14000),  -- Europa
    ('00000000-0000-0000-0000-000000000013', 'WATER',     11000),  -- Titan
    ('00000000-0000-0000-0000-000000000014', 'IRON',       3500),  -- Io
    ('00000000-0000-0000-0000-000000000014', 'RARE_METAL',16000),
    ('00000000-0000-0000-0000-000000000015', 'IRON',       8000),  -- Ganymede
    ('00000000-0000-0000-0000-000000000016', 'IRON',       5500),  -- Callisto
    ('00000000-0000-0000-0000-000000000017', 'WATER',      9000),  -- Enceladus
    ('00000000-0000-0000-0000-000000000018', 'IRON',       2200),  -- Mimas
    ('00000000-0000-0000-0000-000000000018', 'WATER',      250),
    ('00000000-0000-0000-0000-000000000019', 'WATER',     12000),  -- Triton
    ('00000000-0000-0000-0000-000000000020', 'IRON',       1600),  -- Hebe
    ('00000000-0000-0000-0000-000000000021', 'IRON',       4500),  -- Pluto
    ('00000000-0000-0000-0000-000000000022', 'IRON',       9500),  -- Eris
    ('00000000-0000-0000-0000-000000000022', 'RARE_METAL', 350),
    ('00000000-0000-0000-0000-000000000023', 'IRON',      10000),  -- Haumea
    ('00000000-0000-0000-0000-000000000023', 'WATER',      450),
    ('00000000-0000-0000-0000-000000000024', 'IRON',       3800),  -- Psyche

    -- Outer frontier: biggest yields, longest flight.
    ('00000000-0000-0000-0000-000000000025', 'IRON',      12000),  -- Proxima B
    ('00000000-0000-0000-0000-000000000025', 'RARE_METAL', 380),
    -- 026 Sirius: star, no reserves
    ('00000000-0000-0000-0000-000000000027', 'IRON',       9000),  -- Alpha Centauri
    ('00000000-0000-0000-0000-000000000027', 'WATER',      420),
    -- 028 Barnard's Star: star, no reserves
    -- 029 Wolf 359: star, no reserves
    ('00000000-0000-0000-0000-000000000030', 'IRON',      11000),  -- Tau Ceti
    ('00000000-0000-0000-0000-000000000030', 'WATER',      380),
    ('00000000-0000-0000-0000-000000000031', 'IRON',       8500),  -- Lalande
    ('00000000-0000-0000-0000-000000000032', 'IRON',       1900),  -- Astraea
    ('00000000-0000-0000-0000-000000000033', 'IRON',       3800),  -- Gliese 581 (lava)
    ('00000000-0000-0000-0000-000000000033', 'RARE_METAL',19000),
    ('00000000-0000-0000-0000-000000000034', 'WATER',     20000),  -- Kepler-22b
    ('00000000-0000-0000-0000-000000000035', 'HYDROGEN',  17000),  -- HD 209458
    ('00000000-0000-0000-0000-000000000035', 'HELIUM',     4000),
    ('00000000-0000-0000-0000-000000000036', 'HYDROGEN',  16000),  -- 51 Pegasi
    ('00000000-0000-0000-0000-000000000036', 'HELIUM',     3800),
    -- 037 TRAPPIST-1: star, no reserves
    ('00000000-0000-0000-0000-000000000038', 'IRON',       2400),  -- Hygiea
    ('00000000-0000-0000-0000-000000000038', 'WATER',      280),
    ('00000000-0000-0000-0000-000000000039', 'IRON',       1700),  -- Iris
    ('00000000-0000-0000-0000-000000000039', 'RARE_METAL', 220),
    ('00000000-0000-0000-0000-000000000040', 'WATER',     18000);  -- TOI 700 d

-- =============================================================================
-- 3. Buy prices (body × resource)
-- =============================================================================
-- Per-body buy prices. Bodies that buy a resource never (in this seed) also
-- have meaningful reserves of it — forces "extract here, sell there" loops.
--
-- Price tiers used:
--   Bulk (IRON, WATER): 5–12 credits/unit
--   Fuel (HYDROGEN):    10–18
--   Tech (HELIUM):      50–100
--   Premium (RARE_METAL): 40–80

INSERT INTO body_buy_prices (body_id, resource_kind, price_per_unit) VALUES
    -- Earth: industrial heart, buys bulk imports.
    ('00000000-0000-0000-0000-000000000001', 'IRON',        9),
    ('00000000-0000-0000-0000-000000000001', 'WATER',       7),

    -- Mars: industrial post.
    ('00000000-0000-0000-0000-000000000002', 'IRON',        8),

    -- Venus: tech buyer (hot, exotic industry).
    ('00000000-0000-0000-0000-000000000004', 'HELIUM',     65),
    ('00000000-0000-0000-0000-000000000004', 'RARE_METAL', 55),

    -- Ceres: asteroid trading outpost — water for life support, hydrogen for ships.
    ('00000000-0000-0000-0000-000000000005', 'WATER',       8),
    ('00000000-0000-0000-0000-000000000005', 'HYDROGEN',   12),

    -- Pluto: habitat colony, hungry for water + hydrogen.
    ('00000000-0000-0000-0000-000000000021', 'WATER',      11),
    ('00000000-0000-0000-0000-000000000021', 'HYDROGEN',   14),

    -- Haumea: tech buyer, deep in the mid ring.
    ('00000000-0000-0000-0000-000000000023', 'HELIUM',     80),
    ('00000000-0000-0000-0000-000000000023', 'RARE_METAL', 70),

    -- Alpha Centauri: outer industrial center.
    ('00000000-0000-0000-0000-000000000027', 'IRON',       10),
    ('00000000-0000-0000-0000-000000000027', 'WATER',      9),

    -- Tau Ceti: outer habitat — premium water + hydrogen prices.
    ('00000000-0000-0000-0000-000000000030', 'WATER',      13),
    ('00000000-0000-0000-0000-000000000030', 'HYDROGEN',   16),

    -- Lalande: eastern industrial.
    ('00000000-0000-0000-0000-000000000031', 'IRON',       11),

    -- Kepler-22b: ice world but ALSO a buyer of rare metal (tech outpost on the ice).
    ('00000000-0000-0000-0000-000000000034', 'RARE_METAL', 75),

    -- TOI 700 d: outermost habitat, top water/hydrogen prices.
    ('00000000-0000-0000-0000-000000000040', 'WATER',      14),
    ('00000000-0000-0000-0000-000000000040', 'HYDROGEN',   18);
