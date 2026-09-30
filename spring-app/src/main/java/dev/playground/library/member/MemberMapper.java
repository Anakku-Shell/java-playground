package dev.playground.library.member;

import dev.playground.library.member.dto.CreateMemberRequest;
import dev.playground.library.member.dto.MemberResponse;
import java.util.Locale;

/** Manual mapping between {@link Member} and its DTOs. Guide: §5.5 Advanced JPA. */
public final class MemberMapper {

    private MemberMapper() {}

    /** The canonical form of an email: {@code Ada@Library.test} and {@code ada@library.test} are one member. */
    public static String normalizeEmail(String email) {
        // Locale.ROOT: a Turkish default locale would lowercase "I" to a dotless "ı".
        return email.toLowerCase(Locale.ROOT);
    }

    public static MemberResponse toResponse(Member member) {
        return new MemberResponse(member.getId(), member.getEmail(), member.getFullName());
    }

    public static Member toNewMember(CreateMemberRequest request) {
        return new Member(normalizeEmail(request.email()), request.fullName());
    }
}
