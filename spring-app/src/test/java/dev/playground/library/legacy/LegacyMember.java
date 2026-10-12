package dev.playground.library.legacy;

import lombok.ToString;
import lombok.Value;

/**
 * LEGACY EXAMPLE: {@code @Value} is Lombok's immutable class (private final fields, getters, no setters,
 * value-based {@code equals}/{@code hashCode}, {@code toString}, and the class is final): what a record
 * gives you since Java 16. {@code @ToString.Exclude} keeps the password out of logs; a record has to
 * override {@code toString} for that (§5.7). Guide: §6 Legacy.
 */
@Value
public class LegacyMember {

    String email;

    @ToString.Exclude
    String password;
}
