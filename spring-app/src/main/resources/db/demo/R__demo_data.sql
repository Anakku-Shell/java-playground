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

-- §5.5: who wrote what. Matched by ISBN and name, so the script does not depend on the ids; the
-- (book_id, author_id) primary key makes a second run a no-op.
INSERT INTO book_authors (book_id, author_id)
SELECT b.id, a.id
FROM (VALUES ('9780441013593', 'Frank Herbert'),
             ('9780593098233', 'Frank Herbert'),
             ('9780441478125', 'Ursula K. Le Guin'),
             ('9780061054884', 'Ursula K. Le Guin'),
             ('9780141439518', 'Jane Austen'),
             ('9780141439587', 'Jane Austen'),
             ('9780141439662', 'Jane Austen'),
             ('9780345539434', 'Carl Sagan'),
             ('9780345376596', 'Carl Sagan'),
             ('9781631492228', 'Mary Beard')) AS v (isbn, author)
JOIN books b ON b.isbn = v.isbn
JOIN authors a ON a.name = v.author
ON CONFLICT DO NOTHING;

INSERT INTO members (email, full_name)
VALUES ('ada@library.test', 'Ada Lovelace'),
       ('alan@library.test', 'Alan Turing'),
       ('grace@library.test', 'Grace Hopper')
ON CONFLICT (email) DO NOTHING;

-- Loans, dated relative to the day the script ran (it reruns only when this file changes, so on an
-- old database the dates age): Ada has Dune out and returned Emma; Alan has the only copy of The
-- Dispossessed (no copies left); Grace's Pale Blue Dot is overdue. No natural key, so each loan is
-- inserted only if that book-member pair has none yet.
INSERT INTO loans (book_id, member_id, loaned_at, due_date, returned_at)
SELECT b.id, m.id, now() - v.days_ago * interval '1 day', current_date - v.days_ago + 14,
       CASE WHEN v.returned THEN now() - interval '1 day' END
FROM (VALUES ('9780441013593', 'ada@library.test', 3, false),
             ('9780141439587', 'ada@library.test', 20, true),
             ('9780061054884', 'alan@library.test', 5, false),
             ('9780345376596', 'grace@library.test', 30, false)) AS v (isbn, email, days_ago, returned)
JOIN books b ON b.isbn = v.isbn
JOIN members m ON m.email = v.email
WHERE NOT EXISTS (SELECT 1 FROM loans l WHERE l.book_id = b.id AND l.member_id = m.id);
