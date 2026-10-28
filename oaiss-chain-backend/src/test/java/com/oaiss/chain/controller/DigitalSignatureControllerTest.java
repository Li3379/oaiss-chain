package com.oaiss.chain.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oaiss.chain.dto.RsaKeyPairResponse;
import com.oaiss.chain.dto.SignatureResult;
import com.oaiss.chain.dto.SignatureVerifyRequest;
import com.oaiss.chain.exception.BlockchainException;
import com.oaiss.chain.security.JwtTokenProvider;
import com.oaiss.chain.security.JwtUserDetails;
import com.oaiss.chain.service.DigitalSignatureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * DigitalSignatureController Unit Tests
 * 数字签名控制器单元测试
 */
@WebMvcTest(controllers = DigitalSignatureController.class)
@AutoConfigureMockMvc(addFilters = false)
class DigitalSignatureControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DigitalSignatureService digitalSignatureService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    private JwtUserDetails enterpriseUserDetails;
    private JwtUserDetails reviewerUserDetails;
    private RsaKeyPairResponse rsaKeyPairResponse;
    private SignatureResult signatureResult;

    @BeforeEach
    void setUp() {
        // Enterprise user (userType=1)
        enterpriseUserDetails = JwtUserDetails.builder()
                .userId(1L)
                .username("enterprise_user")
                .userType(1)
                .roles(List.of("ENTERPRISE"))
                .enterpriseId(1L)
                .enabled(true)
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .build();

        // Reviewer user (userType=2)
        reviewerUserDetails = JwtUserDetails.builder()
                .userId(2L)
                .username("reviewer_user")
                .userType(2)
                .roles(List.of("REVIEWER"))
                .enterpriseId(null)
                .enabled(true)
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .build();

        rsaKeyPairResponse = RsaKeyPairResponse.builder()
                .id(1L)
                .userId(1L)
                .publicKey("MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...")
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusYears(2))
                .keyStatus(1)
                .keyStatusText("有效")
                .keyVersion(1)
                .keyUsage(3)
                .build();

        signatureResult = SignatureResult.builder()
                .signature("YWJjZGVmZ2hpamtsbW5vcA==")
                .algorithm("SHA256withRSA")
                .timestamp(LocalDateTime.now())
                .signerId(1L)
                .build();

        // Clear security context
        SecurityContextHolder.clearContext();
    }

    // ==================== Generate KeyPair Tests ====================

    @Test
    @DisplayName("生成RSA密钥对成功测试")
    void testGenerateKeyPairSuccess() throws Exception {
        // Given
        UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(enterpriseUserDetails, null, 
                        List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        when(digitalSignatureService.generateKeyPair(1L)).thenReturn(rsaKeyPairResponse);

        // When & Then
        mockMvc.perform(post("/signature/keypair/generate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.userId").value(1))
                .andExpect(jsonPath("$.data.keyStatus").value(1))
                .andExpect(jsonPath("$.data.keyStatusText").value("有效"));

        verify(digitalSignatureService, times(1)).generateKeyPair(1L);
    }

    // ==================== Get KeyPair Tests ====================

    @Test
    @DisplayName("获取当前用户密钥对成功测试")
    void testGetKeyPairSuccess() throws Exception {
        // Given
        UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(enterpriseUserDetails, null, 
                        List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        when(digitalSignatureService.getKeyPair(1L)).thenReturn(rsaKeyPairResponse);

        // When & Then
        mockMvc.perform(get("/signature/keypair"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.userId").value(1))
                .andExpect(jsonPath("$.data.publicKey").value("MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA..."));

        verify(digitalSignatureService, times(1)).getKeyPair(1L);
    }

    @Test
    @DisplayName("获取密钥对失败-密钥对不存在")
    void testGetKeyPairNotFound() throws Exception {
        // Given
        UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(enterpriseUserDetails, null, 
                        List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        when(digitalSignatureService.getKeyPair(1L))
                .thenThrow(BlockchainException.rsaKeyPairNotFound(1L));

        // When & Then
        mockMvc.perform(get("/signature/keypair"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(5009));

        verify(digitalSignatureService, times(1)).getKeyPair(1L);
    }

    // ==================== Revoke KeyPair Tests ====================

    @Test
    @DisplayName("撤销密钥对成功测试")
    void testRevokeKeyPairSuccess() throws Exception {
        // Given
        UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(enterpriseUserDetails, null, 
                        List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        doNothing().when(digitalSignatureService).revokeKeyPair(1L);

        // When & Then
        mockMvc.perform(delete("/signature/keypair"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("密钥对已撤销"));

        verify(digitalSignatureService, times(1)).revokeKeyPair(1L);
    }

    // ==================== Sign Report Tests ====================

    @Test
    @DisplayName("对碳报告进行签名成功测试")
    void testSignReportSuccess() throws Exception {
        // Given
        UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(enterpriseUserDetails, null, 
                        List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        String reportData = "{\"reportId\":1,\"emissions\":1000}";
        
        when(digitalSignatureService.signReport(1L, reportData)).thenReturn(signatureResult);

        // When & Then
        mockMvc.perform(post("/signature/sign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reportData))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.signature").value("YWJjZGVmZ2hpamtsbW5vcA=="))
                .andExpect(jsonPath("$.data.algorithm").value("SHA256withRSA"))
                .andExpect(jsonPath("$.data.signerId").value(1));

        verify(digitalSignatureService, times(1)).signReport(1L, reportData);
    }

    @Test
    @DisplayName("对碳报告签名失败-密钥对不存在")
    void testSignReportKeyPairNotFound() throws Exception {
        // Given
        UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(enterpriseUserDetails, null, 
                        List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        String reportData = "{\"reportId\":1,\"emissions\":1000}";
        
        when(digitalSignatureService.signReport(1L, reportData))
                .thenThrow(BlockchainException.rsaKeyPairNotFound(1L));

        // When & Then
        mockMvc.perform(post("/signature/sign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reportData))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(5009));

        verify(digitalSignatureService, times(1)).signReport(1L, reportData);
    }

    // ==================== Verify Signature Tests ====================

    @Test
    @DisplayName("验证碳报告签名成功-签名有效")
    void testVerifySignatureValid() throws Exception {
        // Given
        UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(reviewerUserDetails, null, 
                        List.of(new SimpleGrantedAuthority("ROLE_REVIEWER")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        SignatureVerifyRequest request = SignatureVerifyRequest.builder()
                .reportId(1L)
                .signatureData("YWJjZGVmZ2hpamtsbW5vcA==")
                .reportData("{\"reportId\":1,\"emissions\":1000}")
                .signerId(1L)
                .build();

        when(digitalSignatureService.verifySignature(1L, request.getReportData(), request.getSignatureData()))
                .thenReturn(true);

        // When & Then
        mockMvc.perform(post("/signature/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.valid").value(true))
                .andExpect(jsonPath("$.data.signerId").value(1));

        verify(digitalSignatureService, times(1)).verifySignature(1L, request.getReportData(), request.getSignatureData());
    }

    @Test
    @DisplayName("验证碳报告签名-签名无效")
    void testVerifySignatureInvalid() throws Exception {
        // Given
        UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(reviewerUserDetails, null, 
                        List.of(new SimpleGrantedAuthority("ROLE_REVIEWER")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        SignatureVerifyRequest request = SignatureVerifyRequest.builder()
                .reportId(1L)
                .signatureData("aW52YWxpZF9zaWduYXR1cmU=")
                .reportData("{\"reportId\":1,\"emissions\":2000}")
                .signerId(1L)
                .build();

        when(digitalSignatureService.verifySignature(1L, request.getReportData(), request.getSignatureData()))
                .thenReturn(false);

        // When & Then
        mockMvc.perform(post("/signature/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.valid").value(false));

        verify(digitalSignatureService, times(1)).verifySignature(1L, request.getReportData(), request.getSignatureData());
    }

    @Test
    @DisplayName("验证碳报告签名-使用reportId作为signerId")
    void testVerifySignatureWithReportIdAsSignerId() throws Exception {
        // Given
        UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(reviewerUserDetails, null, 
                        List.of(new SimpleGrantedAuthority("ROLE_REVIEWER")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        SignatureVerifyRequest request = SignatureVerifyRequest.builder()
                .reportId(5L)
                .signatureData("YWJjZGVmZ2hpamtsbW5vcA==")
                .reportData("{\"reportId\":5,\"emissions\":1000}")
                .signerId(null) // No signerId provided, will use reportId
                .build();

        when(digitalSignatureService.verifySignature(5L, request.getReportData(), request.getSignatureData()))
                .thenReturn(true);

        // When & Then
        mockMvc.perform(post("/signature/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.valid").value(true))
                .andExpect(jsonPath("$.data.signerId").value(5));

        verify(digitalSignatureService, times(1)).verifySignature(5L, request.getReportData(), request.getSignatureData());
    }

    // ==================== Encrypt Data Tests ====================

    @Test
    @DisplayName("为审核员加密数据成功测试")
    void testEncryptForReviewerSuccess() throws Exception {
        // Given
        UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(enterpriseUserDetails, null, 
                        List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        String data = "Sensitive carbon report data";
        Long reviewerId = 2L;
        String encryptedData = "ZW5jcnlwdGVkX2RhdGFfaGVyZQ==";

        when(digitalSignatureService.encryptForReviewer(data, reviewerId)).thenReturn(encryptedData);

        // When & Then
        mockMvc.perform(post("/signature/encrypt")
                        .param("reviewerId", reviewerId.toString())
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(data))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(encryptedData));

        verify(digitalSignatureService, times(1)).encryptForReviewer(data, reviewerId);
    }

    @Test
    @DisplayName("加密数据失败-审核员密钥对不存在")
    void testEncryptForReviewerKeyPairNotFound() throws Exception {
        // Given
        UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(enterpriseUserDetails, null, 
                        List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        String data = "Sensitive carbon report data";
        Long reviewerId = 999L;

        when(digitalSignatureService.encryptForReviewer(data, reviewerId))
                .thenThrow(BlockchainException.rsaKeyPairNotFound(reviewerId));

        // When & Then
        mockMvc.perform(post("/signature/encrypt")
                        .param("reviewerId", reviewerId.toString())
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(data))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(5009));

        verify(digitalSignatureService, times(1)).encryptForReviewer(data, reviewerId);
    }

    // ==================== Decrypt Data Tests ====================

    @Test
    @DisplayName("审核员解密数据成功测试")
    void testDecryptForReviewerSuccess() throws Exception {
        // Given
        UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(reviewerUserDetails, null, 
                        List.of(new SimpleGrantedAuthority("ROLE_REVIEWER")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        String encryptedData = "ZW5jcnlwdGVkX2RhdGFfaGVyZQ==";
        String decryptedData = "Sensitive carbon report data";

        when(digitalSignatureService.decryptForReviewer(encryptedData, 2L)).thenReturn(decryptedData);

        // When & Then
        mockMvc.perform(post("/signature/decrypt")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(encryptedData))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(decryptedData));

        verify(digitalSignatureService, times(1)).decryptForReviewer(encryptedData, 2L);
        verify(digitalSignatureService, never()).decryptForEnterprise(anyString(), anyLong());
    }

    @Test
    @DisplayName("企业解密数据成功测试")
    void testDecryptForEnterpriseSuccess() throws Exception {
        // Given
        UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(enterpriseUserDetails, null, 
                        List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        String encryptedData = "ZW5jcnlwdGVkX2RhdGFfZnJvbV9yZXZpZXdlcg==";
        String decryptedData = "Reviewer feedback data";

        when(digitalSignatureService.decryptForEnterprise(encryptedData, 1L)).thenReturn(decryptedData);

        // When & Then
        mockMvc.perform(post("/signature/decrypt")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(encryptedData))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(decryptedData));

        verify(digitalSignatureService, times(1)).decryptForEnterprise(encryptedData, 1L);
        verify(digitalSignatureService, never()).decryptForReviewer(anyString(), anyLong());
    }

    @Test
    @DisplayName("解密数据失败-密钥对不存在")
    void testDecryptKeyPairNotFound() throws Exception {
        // Given
        UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(reviewerUserDetails, null, 
                        List.of(new SimpleGrantedAuthority("ROLE_REVIEWER")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        String encryptedData = "ZW5jcnlwdGVkX2RhdGFfaGVyZQ==";

        when(digitalSignatureService.decryptForReviewer(encryptedData, 2L))
                .thenThrow(BlockchainException.rsaKeyPairNotFound(2L));

        // When & Then
        mockMvc.perform(post("/signature/decrypt")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(encryptedData))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(5009));

        verify(digitalSignatureService, times(1)).decryptForReviewer(encryptedData, 2L);
    }

    // ==================== Additional Edge Case Tests ====================

    @Test
    @DisplayName("签名报告失败-密钥已过期")
    void testSignReportKeyExpired() throws Exception {
        // Given
        UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(enterpriseUserDetails, null, 
                        List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        String reportData = "{\"reportId\":1,\"emissions\":1000}";
        
        when(digitalSignatureService.signReport(1L, reportData))
                .thenThrow(BlockchainException.rsaKeyExpired(1L));

        // When & Then
        mockMvc.perform(post("/signature/sign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reportData))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(5014));

        verify(digitalSignatureService, times(1)).signReport(1L, reportData);
    }

    @Test
    @DisplayName("签名报告失败-密钥已失效")
    void testSignReportKeyRevoked() throws Exception {
        // Given
        UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(enterpriseUserDetails, null, 
                        List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        String reportData = "{\"reportId\":1,\"emissions\":1000}";
        
        when(digitalSignatureService.signReport(1L, reportData))
                .thenThrow(BlockchainException.rsaKeyRevoked(1L));

        // When & Then
        mockMvc.perform(post("/signature/sign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reportData))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(5015));

        verify(digitalSignatureService, times(1)).signReport(1L, reportData);
    }

    @Test
    @DisplayName("验证签名失败-签名者密钥不存在")
    void testVerifySignatureSignerKeyNotFound() throws Exception {
        // Given
        UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(reviewerUserDetails, null, 
                        List.of(new SimpleGrantedAuthority("ROLE_REVIEWER")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        SignatureVerifyRequest request = SignatureVerifyRequest.builder()
                .reportId(1L)
                .signatureData("YWJjZGVmZ2hpamtsbW5vcA==")
                .reportData("{\"reportId\":1,\"emissions\":1000}")
                .signerId(999L)
                .build();

        when(digitalSignatureService.verifySignature(999L, request.getReportData(), request.getSignatureData()))
                .thenThrow(BlockchainException.rsaKeyPairNotFound(999L));

        // When & Then
        mockMvc.perform(post("/signature/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(5009));

        verify(digitalSignatureService, times(1)).verifySignature(999L, request.getReportData(), request.getSignatureData());
    }

    @Test
    @DisplayName("验证签名请求参数验证失败-报告ID为空")
    void testVerifySignatureValidationFailReportIdNull() throws Exception {
        // Given
        UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(reviewerUserDetails, null, 
                        List.of(new SimpleGrantedAuthority("ROLE_REVIEWER")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        SignatureVerifyRequest request = SignatureVerifyRequest.builder()
                .reportId(null)
                .signatureData("YWJjZGVmZ2hpamtsbW5vcA==")
                .reportData("{\"reportId\":1,\"emissions\":1000}")
                .build();

        // When & Then
        mockMvc.perform(post("/signature/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(digitalSignatureService, never()).verifySignature(anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("验证签名请求参数验证失败-签名为空")
    void testVerifySignatureValidationFailSignatureEmpty() throws Exception {
        // Given
        UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(reviewerUserDetails, null, 
                        List.of(new SimpleGrantedAuthority("ROLE_REVIEWER")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        SignatureVerifyRequest request = SignatureVerifyRequest.builder()
                .reportId(1L)
                .signatureData("")
                .reportData("{\"reportId\":1,\"emissions\":1000}")
                .build();

        // When & Then
        mockMvc.perform(post("/signature/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(digitalSignatureService, never()).verifySignature(anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("验证签名请求参数验证失败-报告数据为空")
    void testVerifySignatureValidationFailReportDataEmpty() throws Exception {
        // Given
        UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(reviewerUserDetails, null, 
                        List.of(new SimpleGrantedAuthority("ROLE_REVIEWER")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        SignatureVerifyRequest request = SignatureVerifyRequest.builder()
                .reportId(1L)
                .signatureData("YWJjZGVmZ2hpamtsbW5vcA==")
                .reportData("")
                .build();

        // When & Then
        mockMvc.perform(post("/signature/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(digitalSignatureService, never()).verifySignature(anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("第三方用户生成密钥对成功测试")
    void testGenerateKeyPairThirdPartySuccess() throws Exception {
        // Given
        JwtUserDetails thirdPartyUser = JwtUserDetails.builder()
                .userId(3L)
                .username("thirdparty_user")
                .userType(3)
                .roles(List.of("THIRD_PARTY"))
                .enabled(true)
                .build();
                
        UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(thirdPartyUser, null, 
                        List.of(new SimpleGrantedAuthority("ROLE_THIRD_PARTY")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        RsaKeyPairResponse thirdPartyKeyResponse = RsaKeyPairResponse.builder()
                .id(3L)
                .userId(3L)
                .publicKey("THIRD_PARTY_PUBLIC_KEY_BASE64")
                .keyStatus(1)
                .keyStatusText("有效")
                .build();
        
        when(digitalSignatureService.generateKeyPair(3L)).thenReturn(thirdPartyKeyResponse);

        // When & Then
        mockMvc.perform(post("/signature/keypair/generate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.userId").value(3));

        verify(digitalSignatureService, times(1)).generateKeyPair(3L);
    }

    @Test
    @DisplayName("管理员验证签名成功测试")
    void testVerifySignatureAdminSuccess() throws Exception {
        // Given
        JwtUserDetails adminUser = JwtUserDetails.builder()
                .userId(10L)
                .username("admin_user")
                .userType(0)
                .roles(List.of("ADMIN"))
                .enabled(true)
                .build();
                
        UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(adminUser, null, 
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        SignatureVerifyRequest request = SignatureVerifyRequest.builder()
                .reportId(1L)
                .signatureData("YWJjZGVmZ2hpamtsbW5vcA==")
                .reportData("{\"reportId\":1,\"emissions\":1000}")
                .build();

        when(digitalSignatureService.verifySignature(1L, request.getReportData(), request.getSignatureData()))
                .thenReturn(true);

        // When & Then
        mockMvc.perform(post("/signature/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(true));

        verify(digitalSignatureService, times(1)).verifySignature(1L, request.getReportData(), request.getSignatureData());
    }
}
