package studio.echo.platform.web.dto;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PageDto<T> {
    List<T> content;

    // pagination meta
    int page;            // 0-based
    int size;
    long totalElements;
    int totalPages;

    // convenience flags
    boolean first;
    boolean last;
    boolean hasNext;
    boolean hasPrevious;

    // optional
    String sort;         // e.g. "userId,DESC;name,ASC"

    /** Page<T> → PageDto<T> */
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

    /** Page<S> → PageDto<T> (매핑 포함) */
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