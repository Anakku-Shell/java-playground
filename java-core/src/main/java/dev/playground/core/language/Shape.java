package dev.playground.core.language;

/**
 * A sealed interface: only the listed types may implement it, so the compiler knows every
 * possible Shape and can check that a switch covers them all. Guide: §4.1 Modern language.
 *
 * <p>Permitted subclasses must be final, sealed or non-sealed; records are implicitly final.
 */
public sealed interface Shape permits Circle, Square, Rectangle {}
