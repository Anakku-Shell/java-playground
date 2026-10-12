package dev.playground.library.member;

import dev.playground.library.member.dto.MemberResponse;
import dev.playground.library.security.CurrentMember;
import jakarta.validation.constraints.Positive;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Members. New members register through {@code POST /api/auth/register} (§5.7). HTTP only, like the
 * other controllers. Guide: §5.5 Advanced JPA, §5.7 Security.
 */
@RestController
@RequestMapping("/api/members")
public class MemberController {

    private final MemberService service;

    public MemberController(MemberService service) {
        this.service = service;
    }

    /**
     * Whoever the token belongs to. Spring MVC passes the current {@code Authentication} to any
     * handler parameter of that type; its name is the token's subject, the member id.
     */
    @GetMapping("/me")
    public MemberResponse me(Authentication authentication) {
        return service.findById(CurrentMember.from(authentication).id());
    }

    /** Any member, for librarians only (a URL rule in SecurityConfig). */
    @GetMapping("/{id}")
    public MemberResponse get(@PathVariable @Positive Long id) {
        return service.findById(id);
    }
}
