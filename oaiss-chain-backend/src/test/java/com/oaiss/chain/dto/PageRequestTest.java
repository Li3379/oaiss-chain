package com.oaiss.chain.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PageRequest Tests")
class PageRequestTest {

    @Nested
    @DisplayName("Get Offset Tests")
    class GetOffsetTests {

        @Test
        @DisplayName("Should calculate offset for first page")
        void getOffset_WhenFirstPage_ShouldReturnZero() {
            PageRequest request = PageRequest.builder()
                    .pageNum(1)
                    .pageSize(10)
                    .build();

            assertEquals(0L, request.getOffset());
        }

        @Test
        @DisplayName("Should calculate offset for second page")
        void getOffset_WhenSecondPage_ShouldReturnPageSize() {
            PageRequest request = PageRequest.builder()
                    .pageNum(2)
                    .pageSize(10)
                    .build();

            assertEquals(10L, request.getOffset());
        }

        @Test
        @DisplayName("Should calculate offset for large page number")
        void getOffset_WhenLargePageNumber_ShouldCalculateCorrectly() {
            PageRequest request = PageRequest.builder()
                    .pageNum(100)
                    .pageSize(20)
                    .build();

            assertEquals(1980L, request.getOffset());
        }

        @Test
        @DisplayName("Should calculate offset with different page sizes")
        void getOffset_WhenDifferentPageSizes_ShouldCalculateCorrectly() {
            PageRequest request = PageRequest.builder()
                    .pageNum(3)
                    .pageSize(25)
                    .build();

            assertEquals(50L, request.getOffset());
        }
    }

    @Nested
    @DisplayName("To Pageable Tests")
    class ToPageableTests {

        @Test
        @DisplayName("Should convert to Pageable without sorting")
        void toPageable_WhenNoSorting_ShouldCreateUnsortedPageable() {
            PageRequest request = PageRequest.builder()
                    .pageNum(2)
                    .pageSize(15)
                    .build();

            Pageable pageable = request.toPageable();

            assertEquals(1, pageable.getPageNumber());
            assertEquals(15, pageable.getPageSize());
            assertFalse(pageable.getSort().isSorted());
        }

        @Test
        @DisplayName("Should convert to Pageable with ascending sort")
        void toPageable_WhenAscendingSort_ShouldCreateSortedPageable() {
            PageRequest request = PageRequest.builder()
                    .pageNum(1)
                    .pageSize(10)
                    .sortBy("name")
                    .sortOrder("asc")
                    .build();

            Pageable pageable = request.toPageable();

            assertEquals(0, pageable.getPageNumber());
            assertEquals(10, pageable.getPageSize());
            assertTrue(pageable.getSort().isSorted());
            assertEquals(Sort.Direction.ASC, pageable.getSort().getOrderFor("name").getDirection());
        }

        @Test
        @DisplayName("Should convert to Pageable with descending sort")
        void toPageable_WhenDescendingSort_ShouldCreateSortedPageable() {
            PageRequest request = PageRequest.builder()
                    .pageNum(1)
                    .pageSize(10)
                    .sortBy("createdAt")
                    .sortOrder("desc")
                    .build();

            Pageable pageable = request.toPageable();

            assertTrue(pageable.getSort().isSorted());
            assertEquals(Sort.Direction.DESC, pageable.getSort().getOrderFor("createdAt").getDirection());
        }

        @Test
        @DisplayName("Should default to descending sort for unknown order")
        void toPageable_WhenUnknownSortOrder_ShouldDefaultToDescending() {
            PageRequest request = PageRequest.builder()
                    .pageNum(1)
                    .pageSize(10)
                    .sortBy("id")
                    .sortOrder("invalid")
                    .build();

            Pageable pageable = request.toPageable();

            assertEquals(Sort.Direction.DESC, pageable.getSort().getOrderFor("id").getDirection());
        }

        @Test
        @DisplayName("Should handle uppercase sort order")
        void toPageable_WhenUppercaseSortOrder_ShouldWork() {
            PageRequest request = PageRequest.builder()
                    .pageNum(1)
                    .pageSize(10)
                    .sortBy("name")
                    .sortOrder("ASC")
                    .build();

            Pageable pageable = request.toPageable();

            assertEquals(Sort.Direction.ASC, pageable.getSort().getOrderFor("name").getDirection());
        }

        @Test
        @DisplayName("Should handle empty sortBy")
        void toPageable_WhenEmptySortBy_ShouldCreateUnsortedPageable() {
            PageRequest request = PageRequest.builder()
                    .pageNum(1)
                    .pageSize(10)
                    .sortBy("")
                    .sortOrder("asc")
                    .build();

            Pageable pageable = request.toPageable();

            assertFalse(pageable.getSort().isSorted());
        }
    }

    @Nested
    @DisplayName("Static Factory Tests")
    class StaticFactoryTests {

        @Test
        @DisplayName("Should create PageRequest with page and size")
        void of_WhenPageAndSize_ShouldCreatePageRequest() {
            PageRequest request = PageRequest.of(3, 25);

            assertEquals(3, request.getPageNum());
            assertEquals(25, request.getPageSize());
            assertNull(request.getSortBy());
            assertNull(request.getSortOrder());
        }

        @Test
        @DisplayName("Should create PageRequest with sorting")
        void of_WhenWithSorting_ShouldCreateSortedPageRequest() {
            PageRequest request = PageRequest.of(2, 20, "name", "asc");

            assertEquals(2, request.getPageNum());
            assertEquals(20, request.getPageSize());
            assertEquals("name", request.getSortBy());
            assertEquals("asc", request.getSortOrder());
        }
    }

    @Nested
    @DisplayName("Builder Tests")
    class BuilderTests {

        @Test
        @DisplayName("Should build with all fields")
        void builder_ShouldBuildCompleteRequest() {
            PageRequest request = PageRequest.builder()
                    .pageNum(5)
                    .pageSize(50)
                    .sortBy("createdAt")
                    .sortOrder("desc")
                    .keyword("test")
                    .startTime("2024-01-01")
                    .endTime("2024-12-31")
                    .build();

            assertEquals(5, request.getPageNum());
            assertEquals(50, request.getPageSize());
            assertEquals("createdAt", request.getSortBy());
            assertEquals("desc", request.getSortOrder());
            assertEquals("test", request.getKeyword());
            assertEquals("2024-01-01", request.getStartTime());
            assertEquals("2024-12-31", request.getEndTime());
        }
    }

    @Nested
    @DisplayName("Getter Setter Tests")
    class GetterSetterTests {

        @Test
        @DisplayName("Should set and get all fields")
        void settersAndGetters_ShouldWorkCorrectly() {
            PageRequest request = new PageRequest();

            request.setPageNum(10);
            request.setPageSize(100);
            request.setSortBy("updatedAt");
            request.setSortOrder("asc");
            request.setKeyword("search");
            request.setStartTime("2023-01-01");
            request.setEndTime("2023-12-31");

            assertEquals(10, request.getPageNum());
            assertEquals(100, request.getPageSize());
            assertEquals("updatedAt", request.getSortBy());
            assertEquals("asc", request.getSortOrder());
            assertEquals("search", request.getKeyword());
            assertEquals("2023-01-01", request.getStartTime());
            assertEquals("2023-12-31", request.getEndTime());
        }
    }

    @Nested
    @DisplayName("Default Values Tests")
    class DefaultValuesTests {

        @Test
        @DisplayName("Should have default values")
        void defaults_ShouldBeSet() {
            PageRequest request = new PageRequest();

            assertEquals(1, request.getPageNum());
            assertEquals(10, request.getPageSize());
            assertEquals("desc", request.getSortOrder());
        }
    }

    @Nested
    @DisplayName("All Args Constructor Tests")
    class AllArgsConstructorTests {

        @Test
        @DisplayName("Should create with all args constructor")
        void allArgsConstructor_ShouldCreateCompleteObject() {
            PageRequest request = new PageRequest(
                    7, 30, "id", "asc", "keyword", "start", "end"
            );

            assertEquals(7, request.getPageNum());
            assertEquals(30, request.getPageSize());
            assertEquals("id", request.getSortBy());
            assertEquals("asc", request.getSortOrder());
            assertEquals("keyword", request.getKeyword());
            assertEquals("start", request.getStartTime());
            assertEquals("end", request.getEndTime());
        }
    }

    @Nested
    @DisplayName("No Args Constructor Tests")
    class NoArgsConstructorTests {

        @Test
        @DisplayName("Should create with no args constructor")
        void noArgsConstructor_ShouldCreateObject() {
            PageRequest request = new PageRequest();

            assertNotNull(request);
        }
    }
}
