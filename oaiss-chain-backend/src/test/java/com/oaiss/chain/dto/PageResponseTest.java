package com.oaiss.chain.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PageResponse Tests")
class PageResponseTest {

    @Nested
    @DisplayName("Of Page Tests")
    class OfPageTests {

        @Test
        @DisplayName("Should create PageResponse from Spring Data Page")
        void of_WhenPageProvided_ShouldCreatePageResponse() {
            List<String> content = Arrays.asList("item1", "item2", "item3");
            Page<String> page = new PageImpl<>(content, PageRequest.of(0, 10), 30);

            PageResponse<String> response = PageResponse.of(page);

            assertEquals(3, response.getList().size());
            assertEquals(30L, response.getTotal());
            assertEquals(1, response.getPageNum());
            assertEquals(10, response.getPageSize());
            assertEquals(3, response.getPages());
            assertFalse(response.getHasPrevious());
            assertTrue(response.getHasNext());
            assertTrue(response.getIsFirst());
            assertFalse(response.getIsLast());
        }

        @Test
        @DisplayName("Should handle empty page")
        void of_WhenEmptyPage_ShouldCreateEmptyPageResponse() {
            Page<String> page = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 10), 0);

            PageResponse<String> response = PageResponse.of(page);

            assertTrue(response.getList().isEmpty());
            assertEquals(0L, response.getTotal());
            assertEquals(1, response.getPageNum());
            assertEquals(10, response.getPageSize());
            assertEquals(0, response.getPages());
        }

        @Test
        @DisplayName("Should handle middle page")
        void of_WhenMiddlePage_ShouldSetNavigationCorrectly() {
            List<String> content = Arrays.asList("item1", "item2");
            Page<String> page = new PageImpl<>(content, PageRequest.of(1, 10), 30);

            PageResponse<String> response = PageResponse.of(page);

            assertEquals(2, response.getPageNum());
            assertTrue(response.getHasPrevious());
            assertTrue(response.getHasNext());
            assertFalse(response.getIsFirst());
            assertFalse(response.getIsLast());
        }

        @Test
        @DisplayName("Should handle last page")
        void of_WhenLastPage_ShouldSetNavigationCorrectly() {
            List<String> content = Arrays.asList("item1", "item2");
            Page<String> page = new PageImpl<>(content, PageRequest.of(2, 10), 30);

            PageResponse<String> response = PageResponse.of(page);

            assertEquals(3, response.getPageNum());
            assertTrue(response.getHasPrevious());
            assertFalse(response.getHasNext());
            assertFalse(response.getIsFirst());
            assertTrue(response.getIsLast());
        }
    }

    @Nested
    @DisplayName("Of Page With Converter Tests")
    class OfPageWithConverterTests {

        @Test
        @DisplayName("Should convert entities while creating PageResponse")
        void of_WhenConverterProvided_ShouldConvertEntities() {
            List<Integer> numbers = Arrays.asList(1, 2, 3);
            Page<Integer> page = new PageImpl<>(numbers, PageRequest.of(0, 10), 3);
            Function<Integer, String> converter = Object::toString;

            PageResponse<String> response = PageResponse.of(page, converter);

            assertEquals(3, response.getList().size());
            assertEquals("1", response.getList().get(0));
            assertEquals("2", response.getList().get(1));
            assertEquals("3", response.getList().get(2));
            assertEquals(3L, response.getTotal());
        }

        @Test
        @DisplayName("Should handle empty page with converter")
        void of_WhenEmptyPageWithConverter_ShouldReturnEmptyList() {
            Page<Integer> page = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 10), 0);
            Function<Integer, String> converter = Object::toString;

            PageResponse<String> response = PageResponse.of(page, converter);

            assertTrue(response.getList().isEmpty());
        }
    }

    @Nested
    @DisplayName("Of List Tests")
    class OfListTests {

        @Test
        @DisplayName("Should create PageResponse from list and pagination params")
        void of_WhenListProvided_ShouldCreatePageResponse() {
            List<String> list = Arrays.asList("a", "b", "c");

            PageResponse<String> response = PageResponse.of(list, 100L, 2, 10);

            assertEquals(3, response.getList().size());
            assertEquals(100L, response.getTotal());
            assertEquals(2, response.getPageNum());
            assertEquals(10, response.getPageSize());
            assertEquals(10, response.getPages());
            assertTrue(response.getHasPrevious());
            assertTrue(response.getHasNext());
            assertFalse(response.getIsFirst());
            assertFalse(response.getIsLast());
        }

        @Test
        @DisplayName("Should calculate pages correctly with exact division")
        void of_WhenTotalDividesExactly_ShouldCalculateCorrectPages() {
            List<String> list = Arrays.asList("a", "b");

            PageResponse<String> response = PageResponse.of(list, 20L, 1, 10);

            assertEquals(2, response.getPages());
        }

        @Test
        @DisplayName("Should calculate pages correctly with remainder")
        void of_WhenTotalHasRemainder_ShouldRoundUpPages() {
            List<String> list = Arrays.asList("a");

            PageResponse<String> response = PageResponse.of(list, 15L, 1, 10);

            assertEquals(2, response.getPages());
        }

        @Test
        @DisplayName("Should handle first page")
        void of_WhenFirstPage_ShouldSetNavigationCorrectly() {
            List<String> list = Arrays.asList("a");

            PageResponse<String> response = PageResponse.of(list, 30L, 1, 10);

            assertTrue(response.getIsFirst());
            assertFalse(response.getHasPrevious());
        }

        @Test
        @DisplayName("Should handle last page")
        void of_WhenLastPage_ShouldSetNavigationCorrectly() {
            List<String> list = Arrays.asList("a");

            PageResponse<String> response = PageResponse.of(list, 20L, 2, 10);

            assertTrue(response.getIsLast());
            assertFalse(response.getHasNext());
        }
    }

    @Nested
    @DisplayName("Empty Tests")
    class EmptyTests {

        @Test
        @DisplayName("Should create empty PageResponse")
        void empty_ShouldCreateEmptyPageResponse() {
            PageResponse<String> response = PageResponse.empty();

            assertTrue(response.getList().isEmpty());
            assertEquals(0L, response.getTotal());
            assertEquals(1, response.getPageNum());
            assertEquals(10, response.getPageSize());
            assertEquals(0, response.getPages());
            assertFalse(response.getHasPrevious());
            assertFalse(response.getHasNext());
            assertTrue(response.getIsFirst());
            assertTrue(response.getIsLast());
        }
    }

    @Nested
    @DisplayName("Single Page Tests")
    class SinglePageTests {

        @Test
        @DisplayName("Should create single page result")
        void singlePage_ShouldCreateSinglePageResponse() {
            List<String> list = Arrays.asList("a", "b", "c");

            PageResponse<String> response = PageResponse.singlePage(list);

            assertEquals(3, response.getList().size());
            assertEquals(3L, response.getTotal());
            assertEquals(1, response.getPageNum());
            assertEquals(3, response.getPageSize());
            assertEquals(1, response.getPages());
            assertFalse(response.getHasPrevious());
            assertFalse(response.getHasNext());
            assertTrue(response.getIsFirst());
            assertTrue(response.getIsLast());
        }

        @Test
        @DisplayName("Should handle empty single page")
        void singlePage_WhenEmptyList_ShouldCreateEmptyResponse() {
            List<String> list = Collections.emptyList();

            PageResponse<String> response = PageResponse.singlePage(list);

            assertTrue(response.getList().isEmpty());
            assertEquals(0L, response.getTotal());
            assertEquals(0, response.getPageSize());
        }
    }

    @Nested
    @DisplayName("Builder Tests")
    class BuilderTests {

        @Test
        @DisplayName("Should build PageResponse with all fields")
        void builder_ShouldBuildCompletePageResponse() {
            List<Integer> list = Arrays.asList(1, 2, 3);

            PageResponse<Integer> response = PageResponse.<Integer>builder()
                    .list(list)
                    .total(100L)
                    .pageNum(5)
                    .pageSize(20)
                    .pages(5)
                    .hasPrevious(true)
                    .hasNext(false)
                    .isFirst(false)
                    .isLast(true)
                    .build();

            assertEquals(3, response.getList().size());
            assertEquals(100L, response.getTotal());
            assertEquals(5, response.getPageNum());
            assertEquals(20, response.getPageSize());
            assertEquals(5, response.getPages());
            assertTrue(response.getHasPrevious());
            assertFalse(response.getHasNext());
            assertFalse(response.getIsFirst());
            assertTrue(response.getIsLast());
        }
    }

    @Nested
    @DisplayName("Getter Setter Tests")
    class GetterSetterTests {

        @Test
        @DisplayName("Should set and get all fields")
        void settersAndGetters_ShouldWorkCorrectly() {
            PageResponse<String> response = new PageResponse<>();
            
            response.setList(Arrays.asList("test"));
            response.setTotal(50L);
            response.setPageNum(3);
            response.setPageSize(25);
            response.setPages(2);
            response.setHasPrevious(true);
            response.setHasNext(false);
            response.setIsFirst(false);
            response.setIsLast(true);

            assertEquals(1, response.getList().size());
            assertEquals(50L, response.getTotal());
            assertEquals(3, response.getPageNum());
            assertEquals(25, response.getPageSize());
            assertEquals(2, response.getPages());
            assertTrue(response.getHasPrevious());
            assertFalse(response.getHasNext());
            assertFalse(response.getIsFirst());
            assertTrue(response.getIsLast());
        }
    }

    @Nested
    @DisplayName("All Args Constructor Tests")
    class AllArgsConstructorTests {

        @Test
        @DisplayName("Should create with all args constructor")
        void allArgsConstructor_ShouldCreateCompleteObject() {
            List<String> list = Arrays.asList("x", "y");

            PageResponse<String> response = new PageResponse<>(
                    list, 10L, 1, 5, 2, false, true, true, false
            );

            assertEquals(2, response.getList().size());
            assertEquals(10L, response.getTotal());
            assertEquals(1, response.getPageNum());
            assertEquals(5, response.getPageSize());
            assertEquals(2, response.getPages());
        }
    }
}
