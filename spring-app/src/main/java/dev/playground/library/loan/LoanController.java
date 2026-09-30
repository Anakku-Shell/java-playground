package dev.playground.library.loan;

import dev.playground.library.loan.dto.CreateLoanRequest;
import dev.playground.library.loan.dto.LoanResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Loans. Returning is an action on a loan, not a field edit, so it is a {@code POST} to a
 * sub-resource ({@code /api/loans/{id}/return}) rather than a {@code PUT} of the whole loan.
 * Guide: §5.5 Advanced JPA.
 */
@RestController
@RequestMapping("/api/loans")
public class LoanController {

    private final LoanService service;

    public LoanController(LoanService service) {
        this.service = service;
    }

    /** {@code ?memberId=} and {@code ?active=true|false} are optional and combine. */
    @GetMapping
    public List<LoanResponse> list(
            @RequestParam(required = false) @Positive Long memberId, @RequestParam(required = false) Boolean active) {
        return service.findAll(memberId, active);
    }

    @GetMapping("/{id}")
    public LoanResponse get(@PathVariable @Positive Long id) {
        return service.findById(id);
    }

    @PostMapping
    public ResponseEntity<LoanResponse> borrow(@Valid @RequestBody CreateLoanRequest request) {
        LoanResponse created = service.borrow(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PostMapping("/{id}/return")
    public LoanResponse returnLoan(@PathVariable @Positive Long id) {
        return service.returnLoan(id);
    }
}
