package com.oaiss.chain.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ApiResponse unit tests")
class ApiResponseTest {

    @Test
    @DisplayName("success without data should populate default code message and meta")
    void successWithoutData_shouldPopulateDefaults() {
        ApiResponse<Void> response = ApiResponse.success();

        assertEquals(200, response.getCode());
        assertEquals("操作成功", response.getMessage());
        assertNull(response.getData());
        assertNotNull(response.getMeta());
        assertNotNull(response.getMeta().getRequestId());
        assertTrue(response.getMeta().getRequestId().startsWith("req_"));
        assertNotNull(response.getMeta().getTimestamp());
        assertNull(response.getMeta().getPagination());
    }

    @Test
    @DisplayName("success with custom message should keep payload")
    void successWithCustomMessage_shouldKeepPayload() {
        ApiResponse<String> response = ApiResponse.success("payload", "done");

        assertEquals(200, response.getCode());
        assertEquals("done", response.getMessage());
        assertEquals("payload", response.getData());
        assertNotNull(response.getMeta());
    }

    @Test
    @DisplayName("paged success should build pagination meta")
    void pagedSuccess_shouldBuildPagination() {
        ApiResponse<String> response = ApiResponse.success("payload", 2, 10, 21L);

        assertEquals(200, response.getCode());
        assertEquals("payload", response.getData());
        assertNotNull(response.getMeta());
        assertNotNull(response.getMeta().getPagination());
        assertEquals(2, response.getMeta().getPagination().getPage());
        assertEquals(10, response.getMeta().getPagination().getSize());
        assertEquals(21L, response.getMeta().getPagination().getTotal());
        assertEquals(3, response.getMeta().getPagination().getTotalPages());
    }

    @Test
    @DisplayName("error with details should keep code details and meta")
    void errorWithDetails_shouldKeepDetails() {
        ApiResponse<String> response = ApiResponse.error(500, "boom", "detail");

        assertEquals(500, response.getCode());
        assertEquals("boom", response.getMessage());
        assertEquals("detail", response.getData());
        assertNotNull(response.getMeta());
        assertNotNull(response.getMeta().getRequestId());
    }
}
