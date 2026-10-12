-- The schema is hand-written SQL, applied by Flyway in version order. Hibernate only checks that the
-- entities match it (ddl-auto: validate). Never edit this file once it has been applied anywhere:
-- Flyway stores its checksum and refuses to start if it changes. Add V2, V3... instead.
-- Guide: §5.4 Persistence with JPA.

CREATE TABLE authors (
    id         bigserial    PRIMARY KEY,
    name       varchar(200) NOT NULL,
    birth_year int,
    -- Filled by the database for now; the entities map them in §5.5 (auditing).
    created_at timestamptz  NOT NULL DEFAULT now(),
    updated_at timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE books (
    id             bigserial    PRIMARY KEY,
    -- Always 13 digits: the application normalises ISBN-10 before saving (§5.3).
    isbn           varchar(13)  NOT NULL CONSTRAINT books_isbn_key UNIQUE,
    title          varchar(300) NOT NULL,
    published_year int,
    total_copies   int          NOT NULL CONSTRAINT books_total_copies_check CHECK (total_copies >= 0),
    created_at     timestamptz  NOT NULL DEFAULT now(),
    updated_at     timestamptz  NOT NULL DEFAULT now()
);
