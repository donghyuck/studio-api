package studio.echo.platform.web.dto;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;
import lombok.Value;

/**
 * A DTO for paginated data that provides a standardized structure for paginated
 * API responses.
 *
 * @param <T> the type of the content
 */
@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PageDto<T> {
    /** The content of the page. */
    List<T> content;

    // pagination meta
    /** The page number (0-based). */
    int page;
    /** The size of the page. */
    int size;
    /** The total number of elements. */
    long totalElements;
    /** The total number of pages. */
    int totalPages;

    // convenience flags
    /** Whether this is the first page. */
    boolean first;
    /** Whether this is the last page. */
    boolean last;
    /** Whether there is a next page. */
    boolean hasNext;
    /** Whether there is a previous page. */
    boolean hasPrevious;

    // optional
    /** The sort order (e.g., "userId,DESC;name,ASC"). */
    String sort;

    /**
     * Creates a {@code PageDto} from a Spring Data {@link Page}.
     *
     * @param <T>  the type of the content
     * @param page the page object
     * @return a new {@code PageDto} instance
     */
    public static <T> PageDto<T> from(Page<T> page) {
        return PageDto.<T>builder()
                .content(page.getContent())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .hasNext(page.hasNext())
                .hasPrevious(page.hasPrevious())
                .sort(toSortString(page.getSort()))
                .build();
    }

    /**
     * Creates a {@code PageDto} from a Spring Data {@link Page} with a mapper
     * function.
     *
     * @param <S>    the source type
     * @param <T>    the target type
     * @param page   the page object
     * @param mapper the mapper function to apply to the content
     * @return a new {@code PageDto} instance
     */
    public static <S, T> PageDto<T> from(Page<S> page, Function<S, T> mapper) {
        List<T> mapped = page.getContent().stream().map(mapper).collect(Collectors.toList());
        return PageDto.<T>builder()
                .content(mapped)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .hasNext(page.hasNext())
                .hasPrevious(page.hasPrevious())
                .sort(toSortString(page.getSort()))
                .build();
    }

    private static String toSortString(Sort sort) {
        if (sort == null || sort.isUnsorted()) return null;
        return sort.stream()
                .map(o -> o.getProperty() + "," + o.getDirection().name())
                .collect(Collectors.joining(";"));
    }
}