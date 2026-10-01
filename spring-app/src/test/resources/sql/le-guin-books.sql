-- Fixture for BookRepositoryIT (@Sql): plain SQL, run before the test method inside the test's
-- transaction, so it is rolled back with everything else. Handy for rows that are awkward to build
-- through entities, or shared by several tests. Guide: §5.8 Testing.
INSERT INTO authors (name, birth_year) VALUES ('Ursula K. Le Guin', 1929);

INSERT INTO books (isbn, title, published_year, total_copies)
VALUES ('9780441478125', 'The Left Hand of Darkness', 1969, 2),
       ('9780061054884', 'The Dispossessed', 1974, 1);

INSERT INTO book_authors (book_id, author_id)
SELECT b.id, a.id
FROM books b, authors a
WHERE b.isbn IN ('9780441478125', '9780061054884') AND a.name = 'Ursula K. Le Guin';
