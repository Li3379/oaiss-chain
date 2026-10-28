package com.oaiss.chain.aop;

import com.oaiss.chain.annotation.RequirePermission;
import com.oaiss.chain.entity.AccountPermissionList;
import com.oaiss.chain.entity.EntryPermission;
import com.oaiss.chain.exception.BusinessException;
import com.oaiss.chain.repository.AccountPermissionListRepository;
import com.oaiss.chain.repository.EntryPermissionRepository;
import com.oaiss.chain.security.JwtUserDetails;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 权限校验切面测试
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PermissionAspectTest {

    @Mock
    private EntryPermissionRepository entryPermissionRepository;
    @Mock
    private AccountPermissionListRepository accountPermissionListRepository;
    @Mock
    private ProceedingJoinPoint joinPoint;
    @Mock
    private MethodSignature methodSignature;

    @InjectMocks
    private PermissionAspect permissionAspect;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("权限校验 - 未登录抛出异常")
    void checkPermission_NotLoggedIn_ShouldThrow() {
        setupMethod("defaultMethod");
        SecurityContextHolder.clearContext();

        assertThrows(BusinessException.class, () -> permissionAspect.checkPermission(joinPoint));
    }

    @Test
    @DisplayName("权限校验 - 普通用户通过默认权限")
    void checkPermission_DefaultPermission_ShouldProceed() throws Throwable {
        setupMethod("defaultMethod");
        setUser(1, 1);
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = permissionAspect.checkPermission(joinPoint);
        assertEquals("ok", result);
    }

    @Test
    @DisplayName("权限校验 - adminOnly非管理员抛出异常")
    void checkPermission_AdminOnly_NonAdmin_ShouldThrow() {
        setupMethod("adminOnlyMethod");
        setUser(1, 1); // enterprise user

        assertThrows(BusinessException.class, () -> permissionAspect.checkPermission(joinPoint));
    }

    @Test
    @DisplayName("权限校验 - adminOnly管理员通过")
    void checkPermission_AdminOnly_Admin_ShouldProceed() throws Throwable {
        setupMethod("adminOnlyMethod");
        setUser(1, 99); // admin
        when(joinPoint.proceed()).thenReturn("admin_ok");

        Object result = permissionAspect.checkPermission(joinPoint);
        assertEquals("admin_ok", result);
    }

    @Test
    @DisplayName("权限校验 - enterpriseOnly非企业用户抛出异常")
    void checkPermission_EnterpriseOnly_NonEnterprise_ShouldThrow() {
        setupMethod("enterpriseOnlyMethod");
        setUser(2, 2); // reviewer

        assertThrows(BusinessException.class, () -> permissionAspect.checkPermission(joinPoint));
    }

    @Test
    @DisplayName("权限校验 - reviewerOnly非审核员抛出异常")
    void checkPermission_ReviewerOnly_NonReviewer_ShouldThrow() {
        setupMethod("reviewerOnlyMethod");
        setUser(1, 1); // enterprise

        assertThrows(BusinessException.class, () -> permissionAspect.checkPermission(joinPoint));
    }

    @Test
    @DisplayName("权限校验 - thirdPartyOnly非第三方抛出异常")
    void checkPermission_ThirdPartyOnly_NonThirdParty_ShouldThrow() {
        setupMethod("thirdPartyOnlyMethod");
        setUser(1, 1); // enterprise

        assertThrows(BusinessException.class, () -> permissionAspect.checkPermission(joinPoint));
    }

    @Test
    @DisplayName("权限校验 - API路径权限配置拒绝时抛出异常")
    void checkPermission_ApiDenied_ShouldThrow() {
        setupMethod("defaultMethod");
        setUser(1, 1);
        setHttpRequest("POST", "/api/v1/admin/users");

        EntryPermission perm = EntryPermission.builder().userType(1).apiPath("/api/v1/admin/users")
                .httpMethod("POST").allowed(false).build();
        when(entryPermissionRepository.findByApiPathAndHttpMethodAndDeletedFalse(anyString(), anyString()))
                .thenReturn(Optional.of(perm));

        assertThrows(BusinessException.class, () -> permissionAspect.checkPermission(joinPoint));
    }

    @Test
    @DisplayName("权限校验 - API路径用户类型不匹配时抛出异常")
    void checkPermission_UserTypeMismatch_ShouldThrow() {
        setupMethod("defaultMethod");
        setUser(1, 1); // enterprise
        setHttpRequest("GET", "/api/v1/reviewer/data");

        EntryPermission perm = EntryPermission.builder().userType(2).apiPath("/api/v1/reviewer/data")
                .httpMethod("GET").allowed(true).build();
        when(entryPermissionRepository.findByApiPathAndHttpMethodAndDeletedFalse(anyString(), anyString()))
                .thenReturn(Optional.of(perm));

        assertThrows(BusinessException.class, () -> permissionAspect.checkPermission(joinPoint));
    }

    @Test
    @DisplayName("权限校验 - 管理员跳过用户类型检查")
    void checkPermission_AdminSkipsTypeCheck() throws Throwable {
        setupMethod("defaultMethod");
        setUser(1, 99); // admin
        setHttpRequest("GET", "/api/v1/reviewer/data");

        EntryPermission perm = EntryPermission.builder().userType(2).apiPath("/api/v1/reviewer/data")
                .httpMethod("GET").allowed(true).build();
        when(entryPermissionRepository.findByApiPathAndHttpMethodAndDeletedFalse(anyString(), anyString()))
                .thenReturn(Optional.of(perm));
        when(joinPoint.proceed()).thenReturn("admin_bypass");

        Object result = permissionAspect.checkPermission(joinPoint);
        assertEquals("admin_bypass", result);
    }

    @Test
    @DisplayName("权限校验 - 无HTTP请求时正常通过")
    void checkPermission_NoHttpRequest_ShouldProceed() throws Throwable {
        setupMethod("defaultMethod");
        setUser(1, 1);
        RequestContextHolder.resetRequestAttributes();
        when(joinPoint.proceed()).thenReturn("no_request");

        Object result = permissionAspect.checkPermission(joinPoint);
        assertEquals("no_request", result);
    }

    @Test
    @DisplayName("权限校验 - 带权限编码的注解正常通过")
    void checkPermission_WithPermissionCode_ShouldProceed() throws Throwable {
        setupMethod("withPermissionCodeMethod");
        setUser(1, 1);
        when(accountPermissionListRepository.findByPermissionCodeAndDeletedFalse("carbon:report:submit"))
                .thenReturn(Optional.of(AccountPermissionList.builder().build()));
        when(joinPoint.proceed()).thenReturn("code_ok");

        Object result = permissionAspect.checkPermission(joinPoint);
        assertEquals("code_ok", result);
    }

    @Test
    @DisplayName("权限校验 - userType为0或null时允许所有用户类型")
    void checkPermission_NullUserType_ShouldAllow() throws Throwable {
        setupMethod("defaultMethod");
        setUser(1, 1);
        setHttpRequest("GET", "/api/v1/public/data");

        EntryPermission perm = EntryPermission.builder().userType(null).apiPath("/api/v1/public/data")
                .httpMethod("GET").allowed(true).build();
        when(entryPermissionRepository.findByApiPathAndHttpMethodAndDeletedFalse(anyString(), anyString()))
                .thenReturn(Optional.of(perm));
        when(joinPoint.proceed()).thenReturn("public_ok");

        Object result = permissionAspect.checkPermission(joinPoint);
        assertEquals("public_ok", result);
    }

    // ==================== Branch Coverage: Pass-through for Role-Restricted Annotations ====================

    @Test
    @DisplayName("权限校验 - enterpriseOnly企业用户通过")
    void checkPermission_EnterpriseOnly_EnterpriseUser_ShouldProceed() throws Throwable {
        setupMethod("enterpriseOnlyMethod");
        setUser(1, 1); // enterprise user (userType=1)
        when(joinPoint.proceed()).thenReturn("enterprise_ok");

        Object result = permissionAspect.checkPermission(joinPoint);
        assertEquals("enterprise_ok", result);
    }

    @Test
    @DisplayName("权限校验 - reviewerOnly审核员通过")
    void checkPermission_ReviewerOnly_ReviewerUser_ShouldProceed() throws Throwable {
        setupMethod("reviewerOnlyMethod");
        setUser(1, 2); // reviewer (userType=2)
        when(joinPoint.proceed()).thenReturn("reviewer_ok");

        Object result = permissionAspect.checkPermission(joinPoint);
        assertEquals("reviewer_ok", result);
    }

    @Test
    @DisplayName("权限校验 - thirdPartyOnly第三方通过")
    void checkPermission_ThirdPartyOnly_ThirdPartyUser_ShouldProceed() throws Throwable {
        setupMethod("thirdPartyOnlyMethod");
        setUser(1, 3); // third party (userType=3)
        when(joinPoint.proceed()).thenReturn("third_party_ok");

        Object result = permissionAspect.checkPermission(joinPoint);
        assertEquals("third_party_ok", result);
    }

    // ==================== Branch Coverage: Additional Branches ====================

    @Test
    @DisplayName("权限校验 - 非JwtUserDetails principal应抛出异常")
    void checkPermission_InvalidPrincipal_ShouldThrow() {
        setupMethod("defaultMethod");
        // Set authentication with a non-JwtUserDetails principal (e.g., String)
        org.springframework.security.authentication.UsernamePasswordAuthenticationToken auth =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        "string_principal", null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThrows(BusinessException.class, () -> permissionAspect.checkPermission(joinPoint));
    }

    @Test
    @DisplayName("权限校验 - 未认证authentication应抛出异常")
    void checkPermission_NotAuthenticated_ShouldThrow() {
        setupMethod("defaultMethod");
        // Set an unauthenticated authentication (principal = "anonymousUser")
        org.springframework.security.authentication.UsernamePasswordAuthenticationToken auth =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        "anonymousUser", null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThrows(BusinessException.class, () -> permissionAspect.checkPermission(joinPoint));
    }

    @Test
    @DisplayName("权限校验 - 权限编码不存在时应抛出异常")
    void checkPermission_UnknownPermissionCode_ShouldThrow() {
        setupMethod("withPermissionCodeMethod");
        setUser(1, 1); // non-admin user
        when(accountPermissionListRepository.findByPermissionCodeAndDeletedFalse("carbon:report:submit"))
                .thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> permissionAspect.checkPermission(joinPoint));
    }

    @Test
    @DisplayName("权限校验 - 管理员拥有所有权限编码跳过检查")
    void checkPermission_AdminSkipsPermissionCodeCheck() throws Throwable {
        setupMethod("withPermissionCodeMethod");
        setUser(1, 99); // admin
        when(joinPoint.proceed()).thenReturn("admin_code_ok");

        Object result = permissionAspect.checkPermission(joinPoint);
        assertEquals("admin_code_ok", result);
        // Should not even call the permission code repository for admin
        verify(accountPermissionListRepository, never()).findByPermissionCodeAndDeletedFalse(anyString());
    }

    @Test
    @DisplayName("权限校验 - 带context path的请求应正确去除前缀")
    void checkPermission_WithContextPath_ShouldStripPrefix() throws Throwable {
        setupMethod("defaultMethod");
        setUser(1, 1);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/myapp/api/v1/data");
        request.setContextPath("/myapp");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        // After stripping context path, the lookup path should be /api/v1/data
        EntryPermission perm = EntryPermission.builder().userType(null).apiPath("/api/v1/data")
                .httpMethod("GET").allowed(true).build();
        when(entryPermissionRepository.findByApiPathAndHttpMethodAndDeletedFalse(eq("/api/v1/data"), eq("GET")))
                .thenReturn(Optional.of(perm));
        when(joinPoint.proceed()).thenReturn("context_stripped");

        Object result = permissionAspect.checkPermission(joinPoint);
        assertEquals("context_stripped", result);
    }

    @Test
    @DisplayName("权限校验 - API路径权限配置不存在时允许通过")
    void checkPermission_NoEntryPermission_ShouldProceed() throws Throwable {
        setupMethod("defaultMethod");
        setUser(1, 1);
        setHttpRequest("GET", "/api/v1/some/path");
        when(entryPermissionRepository.findByApiPathAndHttpMethodAndDeletedFalse(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(joinPoint.proceed()).thenReturn("no_perm_config");

        Object result = permissionAspect.checkPermission(joinPoint);
        assertEquals("no_perm_config", result);
    }

    @Test
    @DisplayName("权限校验 - userType为0时允许所有用户类型")
    void checkPermission_UserTypeZero_ShouldAllowAll() throws Throwable {
        setupMethod("defaultMethod");
        setUser(1, 1); // enterprise
        setHttpRequest("GET", "/api/v1/open/data");

        EntryPermission perm = EntryPermission.builder().userType(0).apiPath("/api/v1/open/data")
                .httpMethod("GET").allowed(true).build();
        when(entryPermissionRepository.findByApiPathAndHttpMethodAndDeletedFalse(anyString(), anyString()))
                .thenReturn(Optional.of(perm));
        when(joinPoint.proceed()).thenReturn("open_ok");

        Object result = permissionAspect.checkPermission(joinPoint);
        assertEquals("open_ok", result);
    }

    // ==================== Branch Coverage: Unauthenticated non-null auth (line 56) ====================

    @Test
    @DisplayName("权限校验 - 未认证但非null的authentication应抛出异常")
    void checkPermission_UnauthenticatedNonNullAuth_ShouldThrow() {
        setupMethod("defaultMethod");
        // 2-arg constructor → authenticated = false
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken("user", null);
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThrows(BusinessException.class, () -> permissionAspect.checkPermission(joinPoint));
    }

    // ==================== Branch Coverage: Null context path (line 120) ====================

    @Test
    @DisplayName("权限校验 - contextPath为null时不去除前缀")
    void checkPermission_NullContextPath_ShouldProceed() throws Throwable {
        setupMethod("defaultMethod");
        setUser(1, 1);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/data");
        request.setContextPath(null);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        // contextPath is null → first && operand false → no stripping, lookup uses "/api/v1/data"
        when(entryPermissionRepository.findByApiPathAndHttpMethodAndDeletedFalse(eq("/api/v1/data"), eq("GET")))
                .thenReturn(Optional.empty());
        when(joinPoint.proceed()).thenReturn("null_ctx_ok");

        Object result = permissionAspect.checkPermission(joinPoint);
        assertEquals("null_ctx_ok", result);
    }

    // ==================== Branch Coverage: Context path doesn't match request path (line 120) ====================

    @Test
    @DisplayName("权限校验 - contextPath与请求路径不匹配时不去除前缀")
    void checkPermission_ContextPathNotMatching_ShouldProceed() throws Throwable {
        setupMethod("defaultMethod");
        setUser(1, 1);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/data");
        request.setContextPath("/otherapp");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        // contextPath="/otherapp" but requestPath="/api/v1/data" → startsWith false → no stripping
        when(entryPermissionRepository.findByApiPathAndHttpMethodAndDeletedFalse(eq("/api/v1/data"), eq("GET")))
                .thenReturn(Optional.empty());
        when(joinPoint.proceed()).thenReturn("no_match_ok");

        Object result = permissionAspect.checkPermission(joinPoint);
        assertEquals("no_match_ok", result);
    }

    // ==================== Branch Coverage: Matching user type (line 139) ====================

    @Test
    @DisplayName("权限校验 - 用户类型匹配时允许通过")
    void checkPermission_MatchingUserType_ShouldProceed() throws Throwable {
        setupMethod("defaultMethod");
        setUser(1, 1); // enterprise, userType=1
        setHttpRequest("GET", "/api/v1/enterprise/data");

        EntryPermission perm = EntryPermission.builder().userType(1).apiPath("/api/v1/enterprise/data")
                .httpMethod("GET").allowed(true).build();
        when(entryPermissionRepository.findByApiPathAndHttpMethodAndDeletedFalse(anyString(), anyString()))
                .thenReturn(Optional.of(perm));
        when(joinPoint.proceed()).thenReturn("match_ok");

        Object result = permissionAspect.checkPermission(joinPoint);
        assertEquals("match_ok", result);
    }

    private void setupMethod(String methodName) {
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        try {
            Method method = TestMethods.class.getMethod(methodName);
            when(methodSignature.getMethod()).thenReturn(method);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    private void setUser(long userId, int userType) {
        String role = switch (userType) {
            case 1 -> "ENTERPRISE";
            case 2 -> "REVIEWER";
            case 3 -> "THIRD_PARTY";
            case 99 -> "ADMIN";
            default -> "USER";
        };
        JwtUserDetails user = JwtUserDetails.builder()
                .userId(userId).username("user" + userId).userType(userType)
                .roles(List.of(role)).enabled(true).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    private void setHttpRequest(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setContextPath("");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    /** Test method container */
    @SuppressWarnings("unused")
    public static class TestMethods {
        @RequirePermission
        public void defaultMethod() {}

        @RequirePermission(adminOnly = true)
        public void adminOnlyMethod() {}

        @RequirePermission(enterpriseOnly = true)
        public void enterpriseOnlyMethod() {}

        @RequirePermission(reviewerOnly = true)
        public void reviewerOnlyMethod() {}

        @RequirePermission(thirdPartyOnly = true)
        public void thirdPartyOnlyMethod() {}

        @RequirePermission("carbon:report:submit")
        public void withPermissionCodeMethod() {}
    }
}
