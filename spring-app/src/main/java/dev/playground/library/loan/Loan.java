package dev.playground.library.loan;

import dev.playground.library.book.Book;
import dev.playground.library.member.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One book lent to one member, mapped to {@code loans} (V2). Active while {@code returnedAt} is
 * null. Guide: §5.5 Advanced JPA.
 */
@Entity
@Table(name = "loans")
public class Loan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // @ManyToOne is EAGER by default (a JPA 1.0 decision everyone now regrets): every loan loaded
    // would load its book and member too, query or not. LAZY puts a proxy there instead, loaded on
    // first use. optional = false: the column is NOT NULL, so Hibernate can use an inner join.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id")
    private Book book;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id")
    private Member member;

    // Instant <-> timestamptz (a point in time); LocalDate <-> date (a calendar day, no zone).
    @Column(nullable = false)
    private Instant loanedAt;

    @Column(nullable = false)
    private LocalDate dueDate;

    private Instant returnedAt;

    protected Loan() {}

    public Loan(Book book, Member member, Instant loanedAt, LocalDate dueDate) {
        this.book = book;
        this.member = member;
        this.loanedAt = loanedAt;
        this.dueDate = dueDate;
    }

    public Long getId() {
        return id;
    }

    public Book getBook() {
        return book;
    }

    public Member getMember() {
        return member;
    }

    public Instant getLoanedAt() {
        return loanedAt;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public Instant getReturnedAt() {
        return returnedAt;
    }

    public boolean isActive() {
        return returnedAt == null;
    }

    /** The service checks {@link #isActive()} first and answers 409; this is the entity's own guard. */
    public void markReturned(Instant at) {
        if (!isActive()) {
            throw new IllegalStateException("Loan " + id + " was already returned");
        }
        this.returnedAt = at;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof Loan other && id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return Loan.class.hashCode();
    }
}
