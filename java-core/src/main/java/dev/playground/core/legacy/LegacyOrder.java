package dev.playground.core.legacy;

import java.util.Objects;

/**
 * LEGACY EXAMPLE: a Java 8 value class written by hand. Everything {@link Order} gets from one line:
 * private final fields, a constructor, getters, {@code equals}, {@code hashCode} and {@code toString}. In
 * real code bases you also meet the mutable variant (no-arg constructor + setters), or Lombok writing it
 * (§6). Guide: §6 Legacy.
 */
public final class LegacyOrder {

    private final String customer;
    private final String category;
    private final int amount;

    public LegacyOrder(String customer, String category, int amount) {
        this.customer = customer;
        this.category = category;
        this.amount = amount;
    }

    public String getCustomer() {
        return customer;
    }

    public String getCategory() {
        return category;
    }

    public int getAmount() {
        return amount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        LegacyOrder other = (LegacyOrder) o;
        return amount == other.amount && customer.equals(other.customer) && category.equals(other.category);
    }

    @Override
    public int hashCode() {
        return Objects.hash(customer, category, amount);
    }

    @Override
    public String toString() {
        return "LegacyOrder{customer='" + customer + "', category='" + category + "', amount=" + amount + "}";
    }
}
