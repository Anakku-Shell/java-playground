package dev.playground.library.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/** {@link Paging#sanitize}: the allow-list and the id that makes the order total. Guide: §5.5. */
class PagingTest {

    private static final Set<String> SORTABLE = Set.of("id", "title");

    @Test
    void addsTheIdAsTheLastSortKey() {
        var sanitized = Paging.sanitize(PageRequest.of(2, 20, Sort.by("title")), SORTABLE);

        assertThat(sanitized.getSort()).isEqualTo(Sort.by("title", "id"));
        assertThat(sanitized.getPageNumber()).isEqualTo(2);
        assertThat(sanitized.getPageSize()).isEqualTo(20);
    }

    @Test
    void keepsASortByIdAsItIs() {
        // Appending it again would give "order by id desc, id asc".
        Sort byIdDescending = Sort.by(Sort.Direction.DESC, "id");

        assertThat(Paging.sanitize(PageRequest.of(0, 20, byIdDescending), SORTABLE)
                        .getSort())
                .isEqualTo(byIdDescending);
    }

    @Test
    void refusesAPropertyOutsideTheAllowList() {
        assertThatThrownBy(() -> Paging.sanitize(PageRequest.of(0, 20, Sort.by("authors.name")), SORTABLE))
                .isInstanceOf(InvalidSortException.class);
    }
}
