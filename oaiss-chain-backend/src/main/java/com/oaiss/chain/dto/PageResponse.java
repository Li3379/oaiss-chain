package com.oaiss.chain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 分页响应结果
 * Page Response DTO for pagination results
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResponse<T> {

    /**
     * 数据列表
     * Data list
     */
    private List<T> list;

    /**
     * 总记录数
     * Total records
     */
    private Long total;

    /**
     * 当前页码
     * Current page number
     */
    private Integer pageNum;

    /**
     * 每页数量
     * Page size
     */
    private Integer pageSize;

    /**
     * 总页数
     * Total pages
     */
    private Integer pages;

    /**
     * 是否有上一页
     * Has previous page
     */
    private Boolean hasPrevious;

    /**
     * 是否有下一页
     * Has next page
     */
    private Boolean hasNext;

    /**
     * 是否为第一页
     * Is first page
     */
    private Boolean isFirst;

    /**
     * 是否为最后一页
     * Is last page
     */
    private Boolean isLast;

    /**
     * 从Spring Data Page创建PageResponse
     * Create PageResponse from Spring Data Page
     */
    public static <T> PageResponse<T> of(Page<T> page) {
        return PageResponse.<T>builder()
                .list(page.getContent())
                .total(page.getTotalElements())
                .pageNum(page.getNumber() + 1)
                .pageSize(page.getSize())
                .pages(page.getTotalPages())
                .hasPrevious(page.hasPrevious())
                .hasNext(page.hasNext())
                .isFirst(page.isFirst())
                .isLast(page.isLast())
                .build();
    }

    /**
     * 从Spring Data Page创建PageResponse，并转换实体类型
     * Create PageResponse from Spring Data Page with entity conversion
     */
    public static <E, D> PageResponse<D> of(Page<E> page, Function<E, D> converter) {
        List<D> dtoList = page.getContent().stream()
                .map(converter)
                .collect(Collectors.toList());
        
        return PageResponse.<D>builder()
                .list(dtoList)
                .total(page.getTotalElements())
                .pageNum(page.getNumber() + 1)
                .pageSize(page.getSize())
                .pages(page.getTotalPages())
                .hasPrevious(page.hasPrevious())
                .hasNext(page.hasNext())
                .isFirst(page.isFirst())
                .isLast(page.isLast())
                .build();
    }

    /**
     * 从列表和分页参数创建PageResponse
     * Create PageResponse from list and pagination parameters
     */
    public static <T> PageResponse<T> of(List<T> list, Long total, Integer pageNum, Integer pageSize) {
        int pages = (int) Math.ceil((double) total / pageSize);
        
        return PageResponse.<T>builder()
                .list(list)
                .total(total)
                .pageNum(pageNum)
                .pageSize(pageSize)
                .pages(pages)
                .hasPrevious(pageNum > 1)
                .hasNext(pageNum < pages)
                .isFirst(pageNum == 1)
                .isLast(pageNum >= pages)
                .build();
    }

    /**
     * 创建空的PageResponse
     * Create empty PageResponse
     */
    public static <T> PageResponse<T> empty() {
        return PageResponse.<T>builder()
                .list(Collections.emptyList())
                .total(0L)
                .pageNum(1)
                .pageSize(10)
                .pages(0)
                .hasPrevious(false)
                .hasNext(false)
                .isFirst(true)
                .isLast(true)
                .build();
    }

    /**
     * 创建单页结果（不分页）
     * Create single page result (no pagination)
     */
    public static <T> PageResponse<T> singlePage(List<T> list) {
        return PageResponse.<T>builder()
                .list(list)
                .total((long) list.size())
                .pageNum(1)
                .pageSize(list.size())
                .pages(1)
                .hasPrevious(false)
                .hasNext(false)
                .isFirst(true)
                .isLast(true)
                .build();
    }
}
