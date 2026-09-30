-- Sample data for the dev profile only (application-dev.yml adds db/demo to the Flyway locations).
-- A repeatable migration (R__ prefix): Flyway runs it after the versioned ones, and again whenever
-- its checksum changes. So it must be idempotent: running it twice adds nothing.
-- Guide: §5.4 Persistence with JPA.

-- Authors have no natural key to conflict on, so each row is inserted only if the name is new.
-- ORDER BY: an INSERT ... SELECT hands out ids in whatever order the rows come, so pin it.
INSERT INTO authors (name, birth_year)
SELECT v.name, v.birth_year
FROM (VALUES (1, 'Frank Herbert', 1920),
             (2, 'Ursula K. Le Guin', 1929),
             (3, 'Jane Austen', 1775),
             (4, 'Carl Sagan', 1934),
             (5, 'Mary Beard', 1955)) AS v (position, name, birth_year)
WHERE NOT EXISTS (SELECT 1 FROM authors a WHERE a.name = v.name)
ORDER BY v.position;

-- Books have a unique ISBN, so PostgreSQL's ON CONFLICT does the job.
INSERT INTO books (isbn, title, published_year, total_copies)
VALUES ('9780441013593', 'Dune', 1965, 3),
       ('9780593098233', 'Dune Messiah', 1969, 2),
       ('9780441478125', 'The Left Hand of Darkness', 1969, 2),
       ('9780061054884', 'The Dispossessed', 1974, 1),
       ('9780141439518', 'Pride and Prejudice', 1813, 4),
       ('9780141439587', 'Emma', 1815, 2),
       ('9780141439662', 'Sense and Sensibility', 1811, 1),
       ('9780345539434', 'Cosmos', 1980, 2),
       ('9780345376596', 'Pale Blue Dot', 1994, 1),
       ('9781631492228', 'SPQR: A History of Ancient Rome', 2015, 2)
ON CONFLICT (isbn) DO NOTHING;
