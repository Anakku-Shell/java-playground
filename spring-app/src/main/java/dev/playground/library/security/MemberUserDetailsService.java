package dev.playground.library.security;

import dev.playground.library.member.Member;
import dev.playground.library.member.MemberMapper;
import dev.playground.library.member.MemberRepository;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spring Security's hook to load a user by login name, used only when a member logs in
 * ({@link AuthenticationConfig}). Requests with a token never come here: the token already says who
 * the caller is. Both "no such email" and "no password" are {@code UsernameNotFoundException},
 * which the authentication provider reports as bad credentials, like a wrong password.
 * Guide: §5.7 Security.
 */
@Service
public class MemberUserDetailsService implements UserDetailsService {

    private final MemberRepository members;

    public MemberUserDetailsService(MemberRepository members) {
        this.members = members;
    }

    @Override
    @Transactional(readOnly = true)
    public MemberUserDetails loadUserByUsername(String email) {
        Member member = members.findByEmail(MemberMapper.normalizeEmail(email))
                .orElseThrow(() -> new UsernameNotFoundException("No member with that email"));
        if (member.getPasswordHash() == null) {
            // A member row from before passwords existed (V4).
            throw new UsernameNotFoundException("Member " + member.getId() + " has no password");
        }
        return new MemberUserDetails(member.getId(), member.getEmail(), member.getPasswordHash(), member.getRole());
    }
}
