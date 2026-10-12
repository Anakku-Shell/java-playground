package dev.playground.core.functional;

/**
 * A home-made functional interface: exactly one abstract method, so any lambda
 * {@code price -> ...} can implement it. {@code @FunctionalInterface} is optional but makes the
 * compiler reject a second abstract method. Default methods do not count. Guide: §4.2 Collections
 * &amp; functional.
 */
@FunctionalInterface
public interface PriceRule {

    double apply(double price);

    /** Applies this rule, then {@code next}: the same idea as {@code Function.andThen}. */
    default PriceRule then(PriceRule next) {
        return price -> next.apply(apply(price));
    }
}
