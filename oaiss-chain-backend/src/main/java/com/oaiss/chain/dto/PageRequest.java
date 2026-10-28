package com.oaiss.chain.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


/**
 * 分页请求参数
 * Page Request DTO for pagination
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageRequest {

    /**
     * 当前页码，从1开始
     * Current page number, starting from 1
     */
    @NotNull(message = "页码不能为空")
    @Min(value = 1, message = "页码最小为1")
    private Integer pageNum = 1;

    /**
     * 每页数量
     * Page size
     */
    @NotNull(message = "每页数量不能为空")
    @Min(value = 1, message = "每页数量最小为1")
    @Max(value = 100, message = "每页数量最大为100")
    private Integer pageSize = 10;

    /**
     * 排序字段
     * Sort field
     */
    private String sortBy;

    /**
     * 排序方向 (asc/desc)
     * Sort direction
     */
    private String sortOrder = "desc";

    /**
     * 搜索关键词
     * Search keyword
     */
    private String keyword;

    /**
     * 开始时间（用于时间范围筛选）
     * Start time for date range filter
     */
    private String startTime;

    /**
     * 结束时间（用于时间范围筛选）
     * End time for date range filter
     */
    private String endTime;

    /**
     * 获取JPA分页偏移量
     * Get offset for JPA pagination
     */
    public long getOffset() {
        return (long) (pageNum - 1) * pageSize;
    }

    /**
     * 获取Spring Data Pageable
     * Convert to Spring Data Pageable
     */
    public org.springframework.data.domain.Pageable toPageable() {
        if (sortBy != null && !sortBy.isEmpty()) {
            org.springframework.data.domain.Sort.Direction direction = 
                "asc".equalsIgnoreCase(sortOrder) 
                    ? org.springframework.data.domain.Sort.Direction.ASC 
                    : org.springframework.data.domain.Sort.Direction.DESC;
            return org.springframework.data.domain.PageRequest.of(
                pageNum - 1, 
                pageSize, 
                org.springframework.data.domain.Sort.by(direction, sortBy)
            );
        }
        return org.springframework.data.domain.PageRequest.of(pageNum - 1, pageSize);
    }

    /**
     * 默认分页请求
     * Default page request
     */
    public static PageRequest of(int pageNum, int pageSize) {
        return PageRequest.builder()
                .pageNum(pageNum)
                .pageSize(pageSize)
                .build();
    }

    /**
     * 带排序的分页请求
     * Page request with sorting
     */
    public static PageRequest of(int pageNum, int pageSize, String sortBy, String sortOrder) {
        return PageRequest.builder()
                .pageNum(pageNum)
                .pageSize(pageSize)
                .sortBy(sortBy)
                .sortOrder(sortOrder)
                .build();
    }
}
