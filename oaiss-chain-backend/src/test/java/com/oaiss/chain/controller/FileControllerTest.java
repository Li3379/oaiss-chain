package com.oaiss.chain.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oaiss.chain.constant.ErrorCode;
import com.oaiss.chain.security.JwtUserDetails;
import com.oaiss.chain.exception.BusinessException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import com.oaiss.chain.security.JwtTokenProvider;
import com.oaiss.chain.service.MinioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * FileController Unit Tests
 * 文件控制器单元测试
 */
@WebMvcTest(controllers = FileController.class)
@AutoConfigureMockMvc(addFilters = false)
class FileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MinioService minioService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    private MockMultipartFile testFile;
    private MinioService.UploadResult uploadResult;
    private MinioService.FileInfo fileInfo;

    @BeforeEach
    void setUp() {
        testFile = new MockMultipartFile(
                "file",
                "test-document.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "Test file content".getBytes()
        );

        uploadResult = new MinioService.UploadResult(
                "reports/1234567890_test.pdf",
                "http://localhost:9000/bucket/reports/1234567890_test.pdf",
                17L,
                "application/pdf"
        );

        fileInfo = new MinioService.FileInfo(
                "reports/1234567890_test.pdf",
                17L,
                "application/pdf",
                "etag123"
        );
    }

    // ==================== Upload File Tests ====================

    @Test
    @DisplayName("上传单个文件成功测试")
    void testUploadFileSuccess() throws Exception {
        // Given
        when(minioService.uploadFile(any(), anyString())).thenReturn(uploadResult);

        // When & Then
        mockMvc.perform(multipart("/file/upload")
                        .file(testFile)
                        .param("folder", "reports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("文件上传成功"))
                .andExpect(jsonPath("$.data.objectName").value("reports/1234567890_test.pdf"))
                .andExpect(jsonPath("$.data.url").value("http://localhost:9000/bucket/reports/1234567890_test.pdf"))
                .andExpect(jsonPath("$.data.size").value(17))
                .andExpect(jsonPath("$.data.contentType").value("application/pdf"));

        verify(minioService, times(1)).uploadFile(any(), anyString());
    }

    @Test
    @DisplayName("上传单个文件成功-认证用户写入owner元数据")
    void testUploadFileSuccessWithAuthenticatedOwner() throws Exception {
        when(minioService.uploadFile(any(), anyString(), eq(42L))).thenReturn(uploadResult);

        mockMvc.perform(multipart("/file/upload")
                        .file(testFile)
                        .param("folder", "reports")
                        .with(request -> {
                            JwtUserDetails user = JwtUserDetails.builder()
                                    .userId(42L).username("enterprise").userType(1)
                                    .roles(List.of("ENTERPRISE")).build();
                            SecurityContextHolder.getContext().setAuthentication(
                                    new UsernamePasswordAuthenticationToken(user, null,
                                            List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE"))));
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(minioService).uploadFile(any(), eq("reports"), eq(42L));
    }

    @Test
    @DisplayName("上传文件成功-无文件夹参数")
    void testUploadFileSuccessWithoutFolder() throws Exception {
        // Given
        when(minioService.uploadFile(any(), isNull())).thenReturn(uploadResult);

        // When & Then
        mockMvc.perform(multipart("/file/upload")
                        .file(testFile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.objectName").value("reports/1234567890_test.pdf"));

        verify(minioService, times(1)).uploadFile(any(), isNull());
    }

    @Test
    @DisplayName("上传文件失败-文件为空")
    void testUploadFileFailEmptyFile() throws Exception {
        // Given
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "empty.pdf", "application/pdf", new byte[0]
        );
        when(minioService.uploadFile(any(), anyString()))
                .thenThrow(new BusinessException(400, "文件不能为空"));

        // When & Then
        mockMvc.perform(multipart("/file/upload")
                        .file(emptyFile)
                        .param("folder", "reports"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        verify(minioService, times(1)).uploadFile(any(), anyString());
    }

    @Test
    @DisplayName("上传文件失败-文件大小超限")
    void testUploadFileFailSizeExceeded() throws Exception {
        // Given
        when(minioService.uploadFile(any(), anyString()))
                .thenThrow(new BusinessException(413, "文件大小超过限制"));

        // When & Then
        mockMvc.perform(multipart("/file/upload")
                        .file(testFile)
                        .param("folder", "reports"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(413));

        verify(minioService, times(1)).uploadFile(any(), anyString());
    }

    // ==================== Batch Upload Tests ====================

    @Test
    @DisplayName("批量上传文件成功测试")
    void testUploadFilesSuccess() throws Exception {
        // Given
        MockMultipartFile file1 = new MockMultipartFile(
                "files", "file1.pdf", "application/pdf", "Content1".getBytes()
        );
        MockMultipartFile file2 = new MockMultipartFile(
                "files", "file2.pdf", "application/pdf", "Content2".getBytes()
        );

        MinioService.UploadResult result1 = new MinioService.UploadResult(
                "batch/file1.pdf", "http://localhost:9000/bucket/batch/file1.pdf", 8L, "application/pdf"
        );
        MinioService.UploadResult result2 = new MinioService.UploadResult(
                "batch/file2.pdf", "http://localhost:9000/bucket/batch/file2.pdf", 8L, "application/pdf"
        );

        when(minioService.uploadFile(any(), anyString()))
                .thenReturn(result1)
                .thenReturn(result2);

        // When & Then
        mockMvc.perform(multipart("/file/upload/batch")
                        .file(file1)
                        .file(file2)
                        .param("folder", "batch"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("批量上传成功，共 2 个文件"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2));

        verify(minioService, times(2)).uploadFile(any(), anyString());
    }

    @Test
    @DisplayName("批量上传文件成功-无文件夹参数")
    void testUploadFilesSuccessWithoutFolder() throws Exception {
        // Given
        MockMultipartFile file1 = new MockMultipartFile(
                "files", "file1.pdf", "application/pdf", "Content1".getBytes()
        );

        when(minioService.uploadFile(any(), isNull())).thenReturn(uploadResult);

        // When & Then
        mockMvc.perform(multipart("/file/upload/batch")
                        .file(file1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("批量上传成功，共 1 个文件"));

        verify(minioService, times(1)).uploadFile(any(), isNull());
    }

    // ==================== Download File Tests ====================

    @Test
    @DisplayName("下载文件成功测试")
    void testDownloadFileSuccess() throws Exception {
        // Given
        InputStream mockInputStream = new ByteArrayInputStream("Test file content".getBytes());
        when(minioService.downloadFile(anyString())).thenReturn(mockInputStream);
        when(minioService.getFileInfo(anyString())).thenReturn(fileInfo);

        // When & Then
        mockMvc.perform(get("/file/download")
                        .param("objectName", "reports/1234567890_test.pdf"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"1234567890_test.pdf\""))
                .andExpect(header().string("Content-Type", "application/pdf"));

        verify(minioService, times(1)).downloadFile("reports/1234567890_test.pdf");
        verify(minioService, times(1)).getFileInfo("reports/1234567890_test.pdf");
    }

    @Test
    @DisplayName("下载文件失败-文件不存在")
    void testDownloadFileNotFound() throws Exception {
        // Given
        when(minioService.downloadFile(anyString()))
                .thenThrow(new BusinessException(404, "文件不存在"));

        // When & Then
        // Note: FileController throws BusinessException(ErrorCode.SYSTEM_ERROR, "文件下载失败", e)
        // when download fails. The exception is caught by test framework.
        mockMvc.perform(get("/file/download")
                        .param("objectName", "nonexistent/file.pdf"))
                .andExpect(status().isBadRequest());

        verify(minioService, times(1)).downloadFile("nonexistent/file.pdf");
    }

    @Test
    @DisplayName("下载文件失败-objectName为空")
    void testDownloadFileFailEmptyObjectName() throws Exception {
        // When & Then
        mockMvc.perform(get("/file/download")
                        .param("objectName", ""))
                .andExpect(status().isBadRequest());

        verify(minioService, never()).downloadFile(anyString());
    }

    // ==================== Delete File Tests ====================

    @Test
    @DisplayName("删除文件成功测试")
    void testDeleteFileSuccess() throws Exception {
        // Given - 文件所有者与当前企业用户一致 (Fail-Closed: 仅所有者可删)
        when(minioService.getFileOwner("reports/1234567890_test.pdf")).thenReturn(1L);
        doNothing().when(minioService).deleteFile(anyString());

        // When & Then
        mockMvc.perform(delete("/file")
                        .param("objectName", "reports/1234567890_test.pdf")
                        .with(request -> {
                            JwtUserDetails user = JwtUserDetails.builder()
                                    .userId(1L).username("test").userType(1)
                                    .roles(List.of("ENTERPRISE")).build();
                            SecurityContextHolder.getContext().setAuthentication(
                                    new UsernamePasswordAuthenticationToken(user, null,
                                            List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE"))));
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("文件删除成功"));

        verify(minioService, times(1)).deleteFile("reports/1234567890_test.pdf");
    }

    @Test
    @DisplayName("删除文件失败-文件不存在")
    void testDeleteFileNotFound() throws Exception {
        // Given - 用户拥有该文件权限，但存储中不存在抛出404
        when(minioService.getFileOwner("nonexistent/file.pdf")).thenReturn(1L);
        doThrow(new com.oaiss.chain.exception.BusinessException(404, "文件不存在"))
                .when(minioService).deleteFile(anyString());

        // When & Then
        mockMvc.perform(delete("/file")
                        .param("objectName", "nonexistent/file.pdf")
                        .with(request -> {
                            JwtUserDetails user = JwtUserDetails.builder()
                                    .userId(1L).username("test").userType(1)
                                    .roles(List.of("ENTERPRISE")).build();
                            SecurityContextHolder.getContext().setAuthentication(
                                    new UsernamePasswordAuthenticationToken(user, null,
                                            List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE"))));
                            return request;
                        }))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(404));

        verify(minioService, times(1)).deleteFile("nonexistent/file.pdf");
    }

    @Test
    @DisplayName("删除文件失败-objectName为空")
    void testDeleteFileFailEmptyObjectName() throws Exception {
        // When & Then
        mockMvc.perform(delete("/file")
                        .param("objectName", ""))
                .andExpect(status().isBadRequest());

        verify(minioService, never()).deleteFile(anyString());
    }

    // ==================== Batch Delete Files Tests ====================

    @Test
    @DisplayName("批量删除文件成功测试")
    void testDeleteFilesSuccess() throws Exception {
        // Given - 文件所有者均与当前用户一致
        List<String> objectNames = Arrays.asList(
                "reports/file1.pdf",
                "reports/file2.pdf",
                "reports/file3.pdf"
        );
        when(minioService.getFileOwner("reports/file1.pdf")).thenReturn(1L);
        when(minioService.getFileOwner("reports/file2.pdf")).thenReturn(1L);
        when(minioService.getFileOwner("reports/file3.pdf")).thenReturn(1L);
        doNothing().when(minioService).deleteFiles(anyList());

        // When & Then
        mockMvc.perform(delete("/file/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(objectNames))
                        .with(request -> {
                            JwtUserDetails user = JwtUserDetails.builder()
                                    .userId(1L).username("test").userType(1)
                                    .roles(List.of("ENTERPRISE")).build();
                            SecurityContextHolder.getContext().setAuthentication(
                                    new UsernamePasswordAuthenticationToken(user, null,
                                            List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE"))));
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("批量删除成功，共 3 个文件"));

        verify(minioService, times(1)).deleteFiles(objectNames);
    }

    @Test
    @DisplayName("批量删除文件-空列表")
    void testDeleteFilesEmptyList() throws Exception {
        // Given
        doNothing().when(minioService).deleteFiles(anyList());

        // When & Then
        mockMvc.perform(delete("/file/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Collections.emptyList()))
                        .with(request -> {
                            JwtUserDetails user = JwtUserDetails.builder()
                                    .userId(1L).username("test").userType(1)
                                    .roles(List.of("ENTERPRISE")).build();
                            SecurityContextHolder.getContext().setAuthentication(
                                    new UsernamePasswordAuthenticationToken(user, null,
                                            List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE"))));
                            return request;
                        }))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));

        verify(minioService, never()).deleteFiles(anyList());
    }

    // ==================== Get File Info Tests ====================

    @Test
    @DisplayName("获取文件信息成功测试")
    void testGetFileInfoSuccess() throws Exception {
        // Given
        when(minioService.getFileInfo(anyString())).thenReturn(fileInfo);

        // When & Then
        mockMvc.perform(get("/file/info")
                        .param("objectName", "reports/1234567890_test.pdf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.objectName").value("reports/1234567890_test.pdf"))
                .andExpect(jsonPath("$.data.size").value(17))
                .andExpect(jsonPath("$.data.contentType").value("application/pdf"))
                .andExpect(jsonPath("$.data.etag").value("etag123"));

        verify(minioService, times(1)).getFileInfo("reports/1234567890_test.pdf");
    }

    @Test
    @DisplayName("获取文件信息失败-文件不存在")
    void testGetFileInfoNotFound() throws Exception {
        // Given
        when(minioService.getFileInfo(anyString()))
                .thenThrow(new BusinessException(404, "文件不存在"));

        // When & Then
        mockMvc.perform(get("/file/info")
                        .param("objectName", "nonexistent/file.pdf"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(404));

        verify(minioService, times(1)).getFileInfo("nonexistent/file.pdf");
    }

    @Test
    @DisplayName("获取文件信息失败-objectName为空")
    void testGetFileInfoFailEmptyObjectName() throws Exception {
        // When & Then
        mockMvc.perform(get("/file/info")
                        .param("objectName", ""))
                .andExpect(status().isBadRequest());

        verify(minioService, never()).getFileInfo(anyString());
    }

    // ==================== File Exists Tests ====================

    @Test
    @DisplayName("检查文件存在-文件存在")
    void testFileExistsTrue() throws Exception {
        // Given
        when(minioService.fileExists(anyString())).thenReturn(true);

        // When & Then
        mockMvc.perform(get("/file/exists")
                        .param("objectName", "reports/1234567890_test.pdf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(true));

        verify(minioService, times(1)).fileExists("reports/1234567890_test.pdf");
    }

    @Test
    @DisplayName("检查文件存在-文件不存在")
    void testFileExistsFalse() throws Exception {
        // Given
        when(minioService.fileExists(anyString())).thenReturn(false);

        // When & Then
        mockMvc.perform(get("/file/exists")
                        .param("objectName", "nonexistent/file.pdf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(false));

        verify(minioService, times(1)).fileExists("nonexistent/file.pdf");
    }

    @Test
    @DisplayName("检查文件存在失败-objectName为空")
    void testFileExistsFailEmptyObjectName() throws Exception {
        // When & Then
        mockMvc.perform(get("/file/exists")
                        .param("objectName", ""))
                .andExpect(status().isBadRequest());

        verify(minioService, never()).fileExists(anyString());
    }

    // ==================== Get Presigned URL Tests ====================

    @Test
    @DisplayName("获取预签名URL成功测试")
    void testGetPresignedUrlSuccess() throws Exception {
        // Given
        String presignedUrl = "http://localhost:9000/bucket/reports/file.pdf?X-Amz-Signature=abc123";
        when(minioService.getPresignedUrl(anyString())).thenReturn(presignedUrl);

        // When & Then
        mockMvc.perform(get("/file/presigned-url")
                        .param("objectName", "reports/1234567890_test.pdf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(presignedUrl));

        verify(minioService, times(1)).getPresignedUrl("reports/1234567890_test.pdf");
    }

    @Test
    @DisplayName("获取预签名URL失败-文件不存在")
    void testGetPresignedUrlNotFound() throws Exception {
        // Given
        when(minioService.getPresignedUrl(anyString()))
                .thenThrow(new BusinessException(404, "文件不存在"));

        // When & Then
        mockMvc.perform(get("/file/presigned-url")
                        .param("objectName", "nonexistent/file.pdf"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(404));

        verify(minioService, times(1)).getPresignedUrl("nonexistent/file.pdf");
    }

    @Test
    @DisplayName("获取预签名URL失败-objectName为空")
    void testGetPresignedUrlFailEmptyObjectName() throws Exception {
        // When & Then
        mockMvc.perform(get("/file/presigned-url")
                        .param("objectName", ""))
                .andExpect(status().isBadRequest());

        verify(minioService, never()).getPresignedUrl(anyString());
    }

    // ==================== Get Presigned Upload URL Tests ====================

    @Test
    @DisplayName("获取上传预签名URL成功测试")
    void testGetPresignedUploadUrlSuccess() throws Exception {
        // Given
        String uploadUrl = "http://localhost:9000/bucket/uploads/new_file.pdf?X-Amz-Signature=xyz789";
        when(minioService.getPresignedUploadUrl(anyString())).thenReturn(uploadUrl);

        // When & Then
        mockMvc.perform(get("/file/presigned-upload-url")
                        .param("objectName", "uploads/new_file.pdf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(uploadUrl));

        verify(minioService, times(1)).getPresignedUploadUrl("uploads/new_file.pdf");
    }

    @Test
    @DisplayName("获取上传预签名URL失败-objectName为空")
    void testGetPresignedUploadUrlFailEmptyObjectName() throws Exception {
        // When & Then
        mockMvc.perform(get("/file/presigned-upload-url")
                        .param("objectName", ""))
                .andExpect(status().isBadRequest());

        verify(minioService, never()).getPresignedUploadUrl(anyString());
    }

    // ==================== List Files Tests ====================

    @Test
    @DisplayName("列出文件成功测试")
    void testListFilesSuccess() throws Exception {
        // Given
        MinioService.FileInfo file1 = new MinioService.FileInfo(
                "reports/2024/file1.pdf", 1024L, "application/pdf", "etag1"
        );
        MinioService.FileInfo file2 = new MinioService.FileInfo(
                "reports/2024/file2.pdf", 2048L, "application/pdf", "etag2"
        );
        List<MinioService.FileInfo> files = Arrays.asList(file1, file2);
        MinioService.FileListResult result = new MinioService.FileListResult(files, 2, 1, 20);
        when(minioService.listFiles(anyString(), any(), any())).thenReturn(result);

        // When & Then
        mockMvc.perform(get("/file/list")
                        .param("prefix", "reports/2024/"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.files").isArray())
                .andExpect(jsonPath("$.data.files.length()").value(2))
                .andExpect(jsonPath("$.data.total").value(2));

        verify(minioService, times(1)).listFiles(eq("reports/2024/"), any(), any());
    }

    @Test
    @DisplayName("企业用户列出文件成功-按owner范围查询")
    void testListFilesEnterpriseScopedSuccess() throws Exception {
        MinioService.FileInfo file1 = new MinioService.FileInfo(
                "reports/2024/file1.pdf", 1024L, "application/pdf", "etag1"
        );
        MinioService.FileListResult result = new MinioService.FileListResult(List.of(file1), 1, 1, 20);
        when(minioService.listFiles(anyString(), any(), any(), eq(42L), eq(false))).thenReturn(result);

        mockMvc.perform(get("/file/list")
                        .param("prefix", "reports/2024/")
                        .with(request -> {
                            JwtUserDetails user = JwtUserDetails.builder()
                                    .userId(42L).username("enterprise").userType(1)
                                    .roles(List.of("ENTERPRISE")).build();
                            SecurityContextHolder.getContext().setAuthentication(
                                    new UsernamePasswordAuthenticationToken(user, null,
                                            List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE"))));
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.files.length()").value(1));

        verify(minioService).listFiles(eq("reports/2024/"), isNull(), isNull(), eq(42L), eq(false));
    }

    @Test
    @DisplayName("列出文件成功-无前缀参数")
    void testListFilesSuccessWithoutPrefix() throws Exception {
        // Given
        MinioService.FileListResult result = new MinioService.FileListResult(
                Collections.singletonList(fileInfo), 1, 1, 20);
        when(minioService.listFiles(isNull(), any(), any())).thenReturn(result);

        // When & Then
        mockMvc.perform(get("/file/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.files").isArray())
                .andExpect(jsonPath("$.data.files.length()").value(1));

        verify(minioService, times(1)).listFiles(isNull(), any(), any());
    }

    @Test
    @DisplayName("列出文件成功-空列表")
    void testListFilesEmptyList() throws Exception {
        // Given
        MinioService.FileListResult result = new MinioService.FileListResult(
                Collections.emptyList(), 0, 1, 20);
        when(minioService.listFiles(anyString(), any(), any())).thenReturn(result);

        // When & Then
        mockMvc.perform(get("/file/list")
                        .param("prefix", "empty/"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.files").isArray())
                .andExpect(jsonPath("$.data.files.length()").value(0));

        verify(minioService, times(1)).listFiles(eq("empty/"), any(), any());
    }

    @Test
    @DisplayName("列出文件失败-系统错误")
    void testListFilesFailSystemError() throws Exception {
        // Given
        when(minioService.listFiles(anyString(), any(), any()))
                .thenThrow(new BusinessException(500, "获取文件列表失败"));

        // When & Then
        mockMvc.perform(get("/file/list")
                        .param("prefix", "reports/"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(500));

        verify(minioService, times(1)).listFiles(eq("reports/"), any(), any());
    }

    // ==================== Copy File Tests ====================

    @Test
    @DisplayName("复制文件成功测试")
    void testCopyFileSuccess() throws Exception {
        // Given
        doNothing().when(minioService).copyFile(anyString(), anyString());

        // When & Then
        mockMvc.perform(post("/file/copy")
                        .param("sourceObjectName", "reports/original.pdf")
                        .param("targetObjectName", "reports/copy.pdf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("文件复制成功"));

        verify(minioService, times(1)).copyFile("reports/original.pdf", "reports/copy.pdf");
    }

    @Test
    @DisplayName("复制文件失败-源文件不存在")
    void testCopyFileSourceNotFound() throws Exception {
        // Given
        doThrow(new BusinessException(404, "源文件不存在"))
                .when(minioService).copyFile(anyString(), anyString());

        // When & Then
        mockMvc.perform(post("/file/copy")
                        .param("sourceObjectName", "nonexistent/original.pdf")
                        .param("targetObjectName", "reports/copy.pdf"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(404));

        verify(minioService, times(1)).copyFile("nonexistent/original.pdf", "reports/copy.pdf");
    }

    @Test
    @DisplayName("复制文件失败-sourceObjectName为空")
    void testCopyFileFailEmptySourceObjectName() throws Exception {
        // When & Then
        mockMvc.perform(post("/file/copy")
                        .param("sourceObjectName", "")
                        .param("targetObjectName", "reports/copy.pdf"))
                .andExpect(status().isBadRequest());

        verify(minioService, never()).copyFile(anyString(), anyString());
    }

    @Test
    @DisplayName("复制文件失败-targetObjectName为空")
    void testCopyFileFailEmptyTargetObjectName() throws Exception {
        // When & Then
        mockMvc.perform(post("/file/copy")
                        .param("sourceObjectName", "reports/original.pdf")
                        .param("targetObjectName", ""))
                .andExpect(status().isBadRequest());

        verify(minioService, never()).copyFile(anyString(), anyString());
    }

    @Test
    @DisplayName("复制文件失败-两个参数都为空")
    void testCopyFileFailBothEmpty() throws Exception {
        // When & Then
        mockMvc.perform(post("/file/copy")
                        .param("sourceObjectName", "")
                        .param("targetObjectName", ""))
                .andExpect(status().isBadRequest());

        verify(minioService, never()).copyFile(anyString(), anyString());
    }

    // ==================== C5: File Delete Permission Tests ====================

    @Test
    @DisplayName("删除文件成功-管理员可删除任意文件")
    void testDeleteFileAdminCanDeleteAnyFile() throws Exception {
        // Given: admin user, file owned by another user
        when(minioService.getFileOwner("reports/test.pdf")).thenReturn(99L);

        // When & Then: admin should be able to delete
        mockMvc.perform(delete("/file")
                        .param("objectName", "reports/test.pdf")
                        .with(request -> {
                            JwtUserDetails user = JwtUserDetails.builder()
                                    .userId(1L).username("admin").userType(4)
                                    .roles(List.of("ADMIN")).build();
                            SecurityContextHolder.getContext().setAuthentication(
                                    new UsernamePasswordAuthenticationToken(user, null,
                                            List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(minioService).deleteFile("reports/test.pdf");
    }

    @Test
    @DisplayName("删除文件成功-文件所有者可删除自己的文件")
    void testDeleteFileOwnerCanDeleteOwnFile() throws Exception {
        // Given: non-admin user deleting their own file
        when(minioService.getFileOwner("reports/my-file.pdf")).thenReturn(42L);

        // When & Then
        mockMvc.perform(delete("/file")
                        .param("objectName", "reports/my-file.pdf")
                        .with(request -> {
                            JwtUserDetails user = JwtUserDetails.builder()
                                    .userId(42L).username("enterprise").userType(1)
                                    .roles(List.of("ENTERPRISE")).build();
                            SecurityContextHolder.getContext().setAuthentication(
                                    new UsernamePasswordAuthenticationToken(user, null,
                                            List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE"))));
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(minioService).deleteFile("reports/my-file.pdf");
    }

    @Test
    @DisplayName("删除文件失败-非所有者非管理员禁止删除")
    void testDeleteFileNonOwnerNonAdminForbidden() throws Exception {
        // Given: regular user trying to delete another user's file
        when(minioService.getFileOwner("reports/other-file.pdf")).thenReturn(99L);

        // When & Then: should be forbidden (403)
        mockMvc.perform(delete("/file")
                        .param("objectName", "reports/other-file.pdf")
                        .with(request -> {
                            JwtUserDetails user = JwtUserDetails.builder()
                                    .userId(42L).username("enterprise").userType(1)
                                    .roles(List.of("ENTERPRISE")).build();
                            SecurityContextHolder.getContext().setAuthentication(
                                    new UsernamePasswordAuthenticationToken(user, null,
                                            List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE"))));
                            return request;
                        }))
                .andExpect(status().isForbidden());

        verify(minioService, never()).deleteFile(anyString());
    }

    // ==================== Branch Coverage: Admin List Files ====================

    @Test
    @DisplayName("管理员列出文件-使用admin范围查询")
    void testListFilesAdminScopedSuccess() throws Exception {
        MinioService.FileInfo file1 = new MinioService.FileInfo(
                "reports/2024/file1.pdf", 1024L, "application/pdf", "etag1"
        );
        MinioService.FileListResult result = new MinioService.FileListResult(List.of(file1), 1, 1, 20);
        when(minioService.listFiles(anyString(), any(), any(), eq(1L), eq(true))).thenReturn(result);

        mockMvc.perform(get("/file/list")
                        .param("prefix", "reports/2024/")
                        .with(request -> {
                            JwtUserDetails user = JwtUserDetails.builder()
                                    .userId(1L).username("admin").userType(4)
                                    .roles(List.of("ADMIN")).build();
                            SecurityContextHolder.getContext().setAuthentication(
                                    new UsernamePasswordAuthenticationToken(user, null,
                                            List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.files.length()").value(1));

        verify(minioService).listFiles(eq("reports/2024/"), isNull(), isNull(), eq(1L), eq(true));
    }

    @Test
    @DisplayName("批量删除文件-非所有者禁止删除")
    void testDeleteFiles_NonOwnerForbidden() throws Exception {
        List<String> objectNames = List.of("reports/other-file.pdf");
        when(minioService.getFileOwner("reports/other-file.pdf")).thenReturn(99L);

        mockMvc.perform(delete("/file/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(objectNames))
                        .with(request -> {
                            JwtUserDetails user = JwtUserDetails.builder()
                                    .userId(42L).username("enterprise").userType(1)
                                    .roles(List.of("ENTERPRISE")).build();
                            SecurityContextHolder.getContext().setAuthentication(
                                    new UsernamePasswordAuthenticationToken(user, null,
                                            List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE"))));
                            return request;
                        }))
                .andExpect(status().isForbidden());

        verify(minioService, never()).deleteFiles(anyList());
    }

    @Test
    @DisplayName("批量上传文件-认证用户写入owner元数据")
    void testUploadFilesAuthenticatedOwner() throws Exception {
        MockMultipartFile file1 = new MockMultipartFile(
                "files", "file1.pdf", "application/pdf", "Content1".getBytes()
        );

        when(minioService.uploadFile(any(), anyString(), eq(42L))).thenReturn(uploadResult);

        mockMvc.perform(multipart("/file/upload/batch")
                        .file(file1)
                        .param("folder", "reports")
                        .with(request -> {
                            JwtUserDetails user = JwtUserDetails.builder()
                                    .userId(42L).username("enterprise").userType(1)
                                    .roles(List.of("ENTERPRISE")).build();
                            SecurityContextHolder.getContext().setAuthentication(
                                    new UsernamePasswordAuthenticationToken(user, null,
                                            List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE"))));
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("批量上传成功，共 1 个文件"));

        verify(minioService).uploadFile(any(), eq("reports"), eq(42L));
    }

    // ==================== Branch Coverage: download exception path ====================

    @Test
    @DisplayName("下载文件失败-内部异常应返回系统错误")
    void testDownloadFileInternalError() throws Exception {
        when(minioService.downloadFile(anyString()))
                .thenThrow(new RuntimeException("MinIO connection timeout"));

        mockMvc.perform(get("/file/download")
                        .param("objectName", "reports/file.pdf"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.SYSTEM_ERROR));

        verify(minioService, times(1)).downloadFile("reports/file.pdf");
    }

    // ==================== Branch Coverage: delete with null currentUser ====================

    @Test
    @DisplayName("删除文件-无认证用户时文件所有者检查")
    void testDeleteFileNoAuthUser() throws Exception {
        when(minioService.getFileOwner("reports/file.pdf")).thenReturn(null);
        doNothing().when(minioService).deleteFile(anyString());

        mockMvc.perform(delete("/file")
                        .param("objectName", "reports/file.pdf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(minioService).deleteFile("reports/file.pdf");
    }

    // ==================== Branch Coverage: upload batch with null currentUser ====================

    @Test
    @DisplayName("批量上传文件-无认证用户时不传owner")
    void testUploadFilesNoAuthUser() throws Exception {
        MockMultipartFile file1 = new MockMultipartFile(
                "files", "file1.pdf", "application/pdf", "Content1".getBytes()
        );

        when(minioService.uploadFile(any(), isNull())).thenReturn(uploadResult);

        mockMvc.perform(multipart("/file/upload/batch")
                        .file(file1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(minioService).uploadFile(any(), isNull());
    }

    // ==================== Branch Coverage: list files with null currentUser ====================

    @Test
    @DisplayName("列出文件-无认证用户时不传owner和admin参数")
    void testListFilesNoAuthUser() throws Exception {
        MinioService.FileListResult result = new MinioService.FileListResult(
                Collections.emptyList(), 0, 1, 20);
        when(minioService.listFiles(isNull(), any(), any())).thenReturn(result);

        mockMvc.perform(get("/file/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(minioService).listFiles(isNull(), any(), any());
    }

    // ==================== Branch Coverage: delete with null fileOwner and non-admin (Fail-Closed) ====================

    @Test
    @DisplayName("删除文件-文件无所有者信息时拒绝非管理员删除(Fail-Closed安全阻断)")
    void testDeleteFileNoOwnerBackwardCompat() throws Exception {
        when(minioService.getFileOwner("reports/legacy-file.pdf")).thenReturn(null);

        mockMvc.perform(delete("/file")
                        .param("objectName", "reports/legacy-file.pdf")
                        .with(request -> {
                            JwtUserDetails user = JwtUserDetails.builder()
                                    .userId(42L).username("enterprise").userType(1)
                                    .roles(List.of("ENTERPRISE")).build();
                            SecurityContextHolder.getContext().setAuthentication(
                                    new UsernamePasswordAuthenticationToken(user, null,
                                            List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE"))));
                            return request;
                        }))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.PERMISSION_DENIED));

        verify(minioService, never()).deleteFile(anyString());
    }

    @Test
    @DisplayName("删除文件-userType为null时非管理员仅可删除自己所属文件")
    void testDeleteFileNullUserType_checksFileOwner() throws Exception {
        when(minioService.getFileOwner("reports/file.pdf")).thenReturn(42L);
        doNothing().when(minioService).deleteFile(anyString());

        mockMvc.perform(delete("/file")
                        .param("objectName", "reports/file.pdf")
                        .with(request -> {
                            JwtUserDetails user = JwtUserDetails.builder()
                                    .userId(42L).username("enterprise").userType(null)
                                    .roles(List.of("ENTERPRISE")).build();
                            SecurityContextHolder.getContext().setAuthentication(
                                    new UsernamePasswordAuthenticationToken(user, null,
                                            List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE"))));
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(minioService).deleteFile("reports/file.pdf");
    }

    @Test
    @DisplayName("删除文件-文件所有者不匹配时阻断删除")
    void testDeleteFileFailWhenOwnerMismatch() throws Exception {
        when(minioService.getFileOwner("reports/file.pdf")).thenReturn(99L);

        mockMvc.perform(delete("/file")
                        .param("objectName", "reports/file.pdf")
                        .with(request -> {
                            JwtUserDetails user = JwtUserDetails.builder()
                                    .userId(42L).username("enterprise").userType(1)
                                    .roles(List.of("ENTERPRISE")).build();
                            SecurityContextHolder.getContext().setAuthentication(
                                    new UsernamePasswordAuthenticationToken(user, null,
                                            List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE"))));
                            return request;
                        }))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.PERMISSION_DENIED));

        verify(minioService, never()).deleteFile(anyString());
    }

    @Test
    @DisplayName("删除文件-管理员在文件所有者为空时仍可删除兜底")
    void testDeleteFileAdminCanDeleteWhenOwnerIsNull() throws Exception {
        when(minioService.getFileOwner("reports/orphan.pdf")).thenReturn(null);
        doNothing().when(minioService).deleteFile(anyString());

        mockMvc.perform(delete("/file")
                        .param("objectName", "reports/orphan.pdf")
                        .with(request -> {
                            JwtUserDetails user = JwtUserDetails.builder()
                                    .userId(1L).username("admin").userType(4)
                                    .roles(List.of("ADMIN")).build();
                            SecurityContextHolder.getContext().setAuthentication(
                                    new UsernamePasswordAuthenticationToken(user, null,
                                            List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(minioService).deleteFile("reports/orphan.pdf");
    }

    @Test
    @DisplayName("列出文件-admin用户使用admin范围查询")
    void testListFilesWithAdminUserType4_usesAdminView() throws Exception {
        MinioService.FileListResult result = new MinioService.FileListResult(
                Collections.emptyList(), 0, 1, 20);
        when(minioService.listFiles(anyString(), any(), any(), eq(1L), eq(true))).thenReturn(result);

        mockMvc.perform(get("/file/list")
                        .param("prefix", "reports/")
                        .with(request -> {
                            JwtUserDetails user = JwtUserDetails.builder()
                                    .userId(1L).username("admin").userType(4)
                                    .roles(List.of("ADMIN")).build();
                            SecurityContextHolder.getContext().setAuthentication(
                                    new UsernamePasswordAuthenticationToken(user, null,
                                            List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(minioService).listFiles(eq("reports/"), isNull(), isNull(), eq(1L), eq(true));
    }

    @Test
    @DisplayName("批量删除文件-管理员可删除任意文件")
    void testDeleteFilesAdminCanDeleteAnyFile() throws Exception {
        List<String> objectNames = List.of("reports/file1.pdf");
        when(minioService.getFileOwner("reports/file1.pdf")).thenReturn(99L);
        doNothing().when(minioService).deleteFiles(anyList());

        mockMvc.perform(delete("/file/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(objectNames))
                        .with(request -> {
                            JwtUserDetails user = JwtUserDetails.builder()
                                    .userId(1L).username("admin").userType(4)
                                    .roles(List.of("ADMIN")).build();
                            SecurityContextHolder.getContext().setAuthentication(
                                    new UsernamePasswordAuthenticationToken(user, null,
                                            List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(minioService).deleteFiles(objectNames);
    }

    @Test
    @DisplayName("批量删除文件-旧文件无所有者信息时拒绝普通用户删除(Fail-Closed)")
    void testDeleteFilesNoOwnerBackwardCompat() throws Exception {
        List<String> objectNames = List.of("reports/legacy.pdf");
        when(minioService.getFileOwner("reports/legacy.pdf")).thenReturn(null);

        mockMvc.perform(delete("/file/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(objectNames))
                        .with(request -> {
                            JwtUserDetails user = JwtUserDetails.builder()
                                    .userId(42L).username("enterprise").userType(1)
                                    .roles(List.of("ENTERPRISE")).build();
                            SecurityContextHolder.getContext().setAuthentication(
                                    new UsernamePasswordAuthenticationToken(user, null,
                                            List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE"))));
                            return request;
                        }))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.PERMISSION_DENIED));

        verify(minioService, never()).deleteFiles(anyList());
    }

    // ==================== 跨企业越权下载与预签名防御测试 ====================

    @Test
    @DisplayName("越权下载阻断-跨企业用户读取他人机密文件被403拦截")
    void testDownloadFileFailCrossEnterprise() throws Exception {
        when(minioService.getFileOwner("reports/tenant_a.pdf")).thenReturn(13L);
        when(minioService.getFileEnterpriseId("reports/tenant_a.pdf")).thenReturn(1L);

        mockMvc.perform(get("/file/download")
                        .param("objectName", "reports/tenant_a.pdf")
                        .with(request -> {
                            JwtUserDetails tenantB = JwtUserDetails.builder()
                                    .userId(14L).username("enterpriseB").userType(1).enterpriseId(2L)
                                    .roles(List.of("ENTERPRISE")).build();
                            SecurityContextHolder.getContext().setAuthentication(
                                    new UsernamePasswordAuthenticationToken(tenantB, null,
                                            List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE"))));
                            return request;
                        }))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.PERMISSION_DENIED));

        verify(minioService, never()).downloadFile(anyString());
    }

    @Test
    @DisplayName("越权下载阻断-无元数据文件对非特权用户Fail-Closed阻断")
    void testDownloadFileFailWhenOwnerIsNull() throws Exception {
        when(minioService.getFileOwner("reports/orphan.pdf")).thenReturn(null);
        when(minioService.getFileEnterpriseId("reports/orphan.pdf")).thenReturn(null);

        mockMvc.perform(get("/file/download")
                        .param("objectName", "reports/orphan.pdf")
                        .with(request -> {
                            JwtUserDetails tenantB = JwtUserDetails.builder()
                                    .userId(14L).username("enterpriseB").userType(1).enterpriseId(2L)
                                    .roles(List.of("ENTERPRISE")).build();
                            SecurityContextHolder.getContext().setAuthentication(
                                    new UsernamePasswordAuthenticationToken(tenantB, null,
                                            List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE"))));
                            return request;
                        }))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.PERMISSION_DENIED));

        verify(minioService, never()).downloadFile(anyString());
    }

    @Test
    @DisplayName("合规下载成功-同企业用户允许下载并强制携带安全响应头")
    void testDownloadFileSuccessSameEnterprise() throws Exception {
        when(minioService.getFileOwner("reports/tenant_a.pdf")).thenReturn(13L);
        when(minioService.getFileEnterpriseId("reports/tenant_a.pdf")).thenReturn(1L);
        when(minioService.downloadFile("reports/tenant_a.pdf"))
                .thenReturn(new ByteArrayInputStream("data".getBytes()));
        when(minioService.getFileInfo("reports/tenant_a.pdf")).thenReturn(fileInfo);

        mockMvc.perform(get("/file/download")
                        .param("objectName", "reports/tenant_a.pdf")
                        .with(request -> {
                            JwtUserDetails tenantA2 = JwtUserDetails.builder()
                                    .userId(15L).username("enterpriseA2").userType(1).enterpriseId(1L)
                                    .roles(List.of("ENTERPRISE")).build();
                            SecurityContextHolder.getContext().setAuthentication(
                                    new UsernamePasswordAuthenticationToken(tenantA2, null,
                                            List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE"))));
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; sandbox"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"tenant_a.pdf\""));
    }

    @Test
    @DisplayName("预签名URL签发越权阻断-跨企业获取预签名被403拦截")
    void testGetPresignedUrlFailCrossEnterprise() throws Exception {
        when(minioService.getFileOwner("reports/tenant_a.pdf")).thenReturn(13L);
        when(minioService.getFileEnterpriseId("reports/tenant_a.pdf")).thenReturn(1L);

        mockMvc.perform(get("/file/presigned-url")
                        .param("objectName", "reports/tenant_a.pdf")
                        .with(request -> {
                            JwtUserDetails tenantB = JwtUserDetails.builder()
                                    .userId(14L).username("enterpriseB").userType(1).enterpriseId(2L)
                                    .roles(List.of("ENTERPRISE")).build();
                            SecurityContextHolder.getContext().setAuthentication(
                                    new UsernamePasswordAuthenticationToken(tenantB, null,
                                            List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE"))));
                            return request;
                        }))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.PERMISSION_DENIED));

        verify(minioService, never()).getPresignedUrl(anyString());
    }

    // ==================== 路径穿越与直传危险扩展名阻断测试 ====================

    @Test
    @DisplayName("路径穿越防御-downloadFile传入../相对路径被400拦截")
    void testPathTraversalDownloadRejected() throws Exception {
        mockMvc.perform(get("/file/download")
                        .param("objectName", "../../etc/passwd"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR));
    }

    @Test
    @DisplayName("路径穿越防御-deleteFile传入..相对路径被400拦截")
    void testPathTraversalDeleteRejected() throws Exception {
        mockMvc.perform(delete("/file")
                        .param("objectName", "reports/../config.json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR));
    }

    @Test
    @DisplayName("直传预签名白名单阻断-禁止为svg文件申请直传URL")
    void testGetPresignedUploadUrlRejectsSvg() throws Exception {
        mockMvc.perform(get("/file/presigned-upload-url")
                        .param("objectName", "uploads/malicious.svg"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR));
    }

    @Test
    @DisplayName("直传预签名白名单阻断-禁止为html文件申请直传URL")
    void testGetPresignedUploadUrlRejectsHtml() throws Exception {
        mockMvc.perform(get("/file/presigned-upload-url")
                        .param("objectName", "uploads/phishing.html"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR));
    }

    @Test
    @DisplayName("直传预签名白名单阻断-禁止为双扩展名文件申请直传URL")
    void testGetPresignedUploadUrlRejectsDoubleExtension() throws Exception {
        mockMvc.perform(get("/file/presigned-upload-url")
                        .param("objectName", "uploads/test.svg.pdf"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR));
    }

    @Test
    @DisplayName("直传预签名白名单阻断-禁止为无扩展名文件申请直传URL")
    void testGetPresignedUploadUrlRejectsNoExtension() throws Exception {
        mockMvc.perform(get("/file/presigned-upload-url")
                        .param("objectName", "uploads/Makefile"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR));
    }
}
