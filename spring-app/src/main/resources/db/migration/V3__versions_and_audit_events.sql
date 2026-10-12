-- Optimistic locking (§5.6): a version number per row. Hibernate adds "and version = ?" to every
-- UPDATE of a versioned entity and bumps the number; an UPDATE that matches no row means another
-- transaction changed the row since it was read. DEFAULT 0 fills the rows that already exist.
-- Guide: §5.6 Transactions.

-- Bumped by every borrow of the book (Book.version, forced by LoanService), so two borrows of the
-- last copy collide even though neither changes a book column.
ALTER TABLE books ADD COLUMN version bigint NOT NULL DEFAULT 0;

-- Bumped by every borrow of the member too: the max-active rule counts the member's loans, and two
-- borrows of two different books by one member only collide on the member's row.
ALTER TABLE members ADD COLUMN version bigint NOT NULL DEFAULT 0;

-- Bumped when a loan is returned, so two returns of the same loan collide.
ALTER TABLE loans ADD COLUMN version bigint NOT NULL DEFAULT 0;

-- Propagation (§5.6): who asked for what, written by AuditService in a transaction of its own
-- (REQUIRES_NEW), so an event stays even when the request that caused it fails and rolls back.
CREATE TABLE audit_events (
    id          bigserial    PRIMARY KEY,
    action      varchar(50)  NOT NULL,
    detail      varchar(500) NOT NULL,
    occurred_at timestamptz  NOT NULL
);
