package com.oaiss.chain.controller;

import com.oaiss.chain.entity.TradeAudit;
import com.oaiss.chain.security.JwtUserDetails;
import com.oaiss.chain.service.TradeAuditService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 成交审核端点接缝。
 *
 * <p>存在理由：S1–S6 把账本、分流、状态机、线索全部建好了，但**没有任何 HTTP 入口**——
 * 审核员无法在系统里裁决任何一笔成交，前端页面也无从接入。功能做完了却用不上，
 * 等于没做。
 *
 * <p>本测试同时锁定两件事：
 * <ol>
 *   <li>行为：三个端点分别把参数正确交给 Service，并返回审核结果</li>
 *   <li>授权：扮演审核员与管理员必须能过；企业用户与第三方监管必须被拒
 *       （第三方监管只读，不得裁决成交——这是它"不干预市场"的定位）</li>
 * </ol>
 */
class TradeAuditControllerTest {

    private TradeAuditService tradeAuditService;
    private TradeAuditController controller;

    @BeforeEach
    void setUp() {
        tradeAuditService = mock(TradeAuditService.class);
        controller = new TradeAuditController(tradeAuditService);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("审核通过：把交易ID、审核人、意见交给 Service")
    void approve_delegatesToService() {
        authenticateAs(7L, "REVIEWER");

        controller.approve(100L, new TradeAuditController.AuditDecisionRequest("核对无误"), currentPrincipal());

        verify(tradeAuditService).approve(100L, 7L, "核对无误");
    }

    @Test
    @DisplayName("审核驳回：把驳回理由一并交给 Service")
    void reject_passesReason() {
        authenticateAs(7L, "REVIEWER");

        controller.reject(100L, new TradeAuditController.AuditDecisionRequest(
                "成交价异常", TradeAudit.REASON_PRICE_DEVIATION), currentPrincipal());

        verify(tradeAuditService).reject(100L, 7L, "成交价异常", TradeAudit.REASON_PRICE_DEVIATION);
    }

    @Test
    @DisplayName("驳回理由缺省时使用 OTHER，不因漏传而失败")
    void reject_defaultsReason() {
        authenticateAs(7L, "REVIEWER");

        controller.reject(100L, new TradeAuditController.AuditDecisionRequest("其他问题", null), currentPrincipal());

        verify(tradeAuditService).reject(100L, 7L, "其他问题", TradeAudit.REASON_OTHER);
    }

    @Test
    @DisplayName("待审列表：按状态查询，默认待审核")
    void listPending_usesPendingStatus() {
        authenticateAs(7L, "REVIEWER");

        controller.listPending(1, 20, null);

        verify(tradeAuditService).listByStatus(eq(TradeAudit.AUDIT_PENDING), eq(1), eq(20), isNull());
    }

    @Test
    @DisplayName("类级授权：管理员与审核员可裁决，企业不得裁决")
    void classLevelAuthorization() {
        PreAuthorize annotation = TradeAuditController.class.getAnnotation(PreAuthorize.class);
        assertNotNull(annotation, "成交裁决端点必须有授权约束");
        String expr = annotation.value();

        assertTrue(expr.contains("REVIEWER"), "审核员应可裁决，实际: " + expr);
        assertTrue(expr.contains("ADMIN"), "管理员应可兜底裁决，实际: " + expr);
        assertFalse(expr.contains("THIRD_PARTY"),
                "第三方监管为只读核查角色，不得裁决成交（不干预市场），实际: " + expr);
        assertFalse(expr.contains("ENTERPRISE"), "企业不得裁决自己的成交，实际: " + expr);
    }

    @Test
    @DisplayName("每个裁决端点自身也标注了授权（防类级注解被移除后失守）")
    void eachEndpointDeclaresAuthorization() throws Exception {
        for (String name : List.of("approve", "reject")) {
            Method m = java.util.Arrays.stream(TradeAuditController.class.getMethods())
                    .filter(x -> x.getName().equals(name))
                    .findFirst().orElseThrow();
            assertTrue(m.isAnnotationPresent(PreAuthorize.class),
                    name + " 应显式标注 @PreAuthorize");
        }
    }

    private JwtUserDetails currentPrincipal() {
        return (JwtUserDetails) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
    }

    private void authenticateAs(Long userId, String role) {
        JwtUserDetails principal = JwtUserDetails.builder()
                .userId(userId)
                .username("u" + userId)
                .userType(role.equals("ENTERPRISE") ? 1 : role.equals("REVIEWER") ? 2 : 4)
                .roles(List.of(role))
                .enabled(true)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }
}