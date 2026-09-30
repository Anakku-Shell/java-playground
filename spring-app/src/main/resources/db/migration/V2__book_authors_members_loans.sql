-- Relations (§5.5): books <-> authors (many-to-many through a join table), members, and loans
-- (each loan points to one book and one member). V1 is applied and never edited: changes go here.
-- Guide: §5.5 Advanced JPA.

-- The join table of Book.authors. It has no entity of its own: Hibernate writes its rows when a
-- book's author set changes. Deleting a book deletes its links (CASCADE); deleting an author who
-- still has books is refused (no ON DELETE action = NO ACTION, checked at the end of the statement).
CREATE TABLE book_authors (
    book_id   bigint NOT NULL CONSTRAINT book_authors_book_id_fkey REFERENCES books (id) ON DELETE CASCADE,
    author_id bigint NOT NULL CONSTRAINT book_authors_author_id_fkey REFERENCES authors (id),
    PRIMARY KEY (book_id, author_id)
);
-- The primary key index starts with book_id, so it serves "authors of a book". This one serves
-- "books of an author" (the ?authorId= filter, and the check before deleting an author).
CREATE INDEX book_authors_author_id_idx ON book_authors (author_id);

CREATE TABLE members (
    id         bigserial    PRIMARY KEY,
    -- Stored lowercase by the application, so the unique constraint is case-insensitive in effect.
    email      varchar(254) NOT NULL CONSTRAINT members_email_key UNIQUE,
    full_name  varchar(200) NOT NULL,
    created_at timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE loans (
    id          bigserial   PRIMARY KEY,
    -- No ON DELETE: a book or member with loans cannot be deleted.
    book_id     bigint      NOT NULL CONSTRAINT loans_book_id_fkey REFERENCES books (id),
    member_id   bigint      NOT NULL CONSTRAINT loans_member_id_fkey REFERENCES members (id),
    loaned_at   timestamptz NOT NULL,
    due_date    date        NOT NULL,
    -- NULL while the book is out: "active" means returned_at IS NULL.
    returned_at timestamptz
);
CREATE INDEX loans_member_id_idx ON loans (member_id);
-- A partial index: only active loans, the rows the availability count reads.
CREATE INDEX loans_active_book_id_idx ON loans (book_id) WHERE returned_at IS NULL;
