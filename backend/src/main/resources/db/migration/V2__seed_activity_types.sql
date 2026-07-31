-- Seed the default activity categories used by the app. Idempotent: each row is
-- inserted only if a type with the same name does not already exist, so this is
-- safe on databases that were populated before this migration.

INSERT INTO activity_type (name, description, is_instrumental, is_routinary, created_at, updated_at)
SELECT * FROM (
    SELECT 'Work'     AS name, 'Work-related activities'        AS description, 1 AS is_instrumental, 1 AS is_routinary, NOW() AS created_at, NOW() AS updated_at UNION ALL
    SELECT 'Study',              'Studying and learning',                 1, 1, NOW(), NOW() UNION ALL
    SELECT 'Leisure',           'Free time and relaxation',              0, 0, NOW(), NOW() UNION ALL
    SELECT 'Exercise',          'Sport and physical activity',           0, 1, NOW(), NOW() UNION ALL
    SELECT 'Food',              'Meals and eating',                      1, 1, NOW(), NOW() UNION ALL
    SELECT 'Hygiene',           'Personal care and hygiene',             1, 1, NOW(), NOW() UNION ALL
    SELECT 'Commute',           'Travelling and commuting',              1, 1, NOW(), NOW()
) AS seed
WHERE NOT EXISTS (
    SELECT 1 FROM activity_type at WHERE at.name = seed.name
);
