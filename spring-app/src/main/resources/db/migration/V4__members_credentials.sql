-- Security (§5.7): members log in with a password and have a role. Guide: §5.7 Security.

-- The password as a PasswordEncoder hash, prefixed with the algorithm ("{bcrypt}$2a$10$..."), never
-- the password itself. NULL for the rows that existed before this migration: such a member cannot
-- log in until a password is set. 255, not the 70-odd chars of a BCrypt hash: a later default
-- (Argon2, ~125 chars) must fit next to the old hashes.
ALTER TABLE members ADD COLUMN password_hash varchar(255);

-- MEMBER borrows and returns their own loans; LIBRARIAN also manages the catalogue and all loans.
-- Stored as the enum's name (@Enumerated(STRING)); the CHECK keeps any other text out.
ALTER TABLE members ADD COLUMN role varchar(20) NOT NULL DEFAULT 'MEMBER'
    CONSTRAINT members_role_check CHECK (role IN ('MEMBER', 'LIBRARIAN'));
