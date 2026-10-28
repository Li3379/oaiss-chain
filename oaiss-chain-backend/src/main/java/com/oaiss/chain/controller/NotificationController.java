package com.oaiss.chain.controller;

import com.oaiss.chain.annotation.AuditLog;
import com.oaiss.chain.annotation.RateLimit;
import com.oaiss.chain.dto.ApiResponse;
import com.oaiss.chain.entity.Notification;
import com.oaiss.chain.security.JwtUserDetails;
import com.oaiss.chain.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * ==============================================================================
 * 【业务模块】：21. 站内信业务通知与实时消息中心 (Notification Center)
 * 【服务角色】：全系统角色（企业 ENTERPRISE、审核员 REVIEWER、第三方监管 THIRD_PARTY、管理员 ADMIN）
 * 【对应前端页面与路由】：
 *   - 顶部导航通知铃铛: components/NotificationBell.vue (全局 layout/index.vue)
 * 【调用前端 API 模块】：src/api/notification.ts
 * ==============================================================================
 */
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
@Tag(name = "21. 通知管理", description = "通知服务、未读通知查询、标记已读等功能")
@SecurityRequirement(name = "Bearer Authentication")
@PreAuthorize("hasAnyRole('ENTERPRISE', 'REVIEWER', 'THIRD_PARTY', 'ADMIN')")
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * 【业务功能】：分页查询当前登录用户的站内信通知列表（审核结果、成交确认、违约告警等）
     * 【对应前端页面】：components/NotificationBell.vue (顶部通知下拉弹窗)
     * 【对应前端API】：api/notification.ts -> getNotifications()
     * 【可见UI操作】：点击页面顶部右上角【铃铛图标】展开通知抽屉或气泡列表
     */
    @AuditLog(module = "通知管理", action = "LISTNOTIFICATIONS", description = "通知管理 查询通知列表")
    @GetMapping
    @Operation(summary = "获取通知列表")
    public ApiResponse<Page<Notification>> listNotifications(
            @AuthenticationPrincipal JwtUserDetails currentUser,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return ApiResponse.success(
                notificationService.getUserNotifications(currentUser.getUserId(),
                        PageRequest.of(Math.max(1, page) - 1, size)));
    }

    /**
     * 【业务功能】：获取当前用户的未读业务通知数量
     * 【对应前端页面】：components/NotificationBell.vue
     * 【对应前端API】：api/notification.ts -> getUnreadCount()
     * 【可见UI操作】：顶部导航栏通知铃铛右上角的红色未读数角标 (Badge) 渲染
     */
    @AuditLog(module = "通知管理", action = "GETUNREADCOUNT", description = "通知管理 获取未读数量")
    @GetMapping("/unread-count")
    @Operation(summary = "获取未读通知数量")
    public ApiResponse<Long> getUnreadCount(
            @AuthenticationPrincipal JwtUserDetails currentUser) {
        return ApiResponse.success(notificationService.getUnreadCount(currentUser.getUserId()));
    }

    /**
     * 【业务功能】：将指定单条业务通知标记为已读状态
     * 【对应前端页面】：components/NotificationBell.vue
     * 【对应前端API】：api/notification.ts -> markAsRead()
     * 【可见UI操作】：点击某条通知消息进行查阅，或点击右侧小圆点标记已读
     */
    @AuditLog(module = "通知管理", action = "MARKASREAD", description = "通知管理 标记通知已读")
    @PutMapping("/{id}/read")
    @RateLimit(key = "notification_read", limit = 30, period = 60)
    @Operation(summary = "标记单条通知已读")
    public ApiResponse<Void> markAsRead(
            @AuthenticationPrincipal JwtUserDetails currentUser,
            @PathVariable Long id) {
        notificationService.markAsRead(id, currentUser.getUserId());
        return ApiResponse.success(null);
    }

    /**
     * 【业务功能】：一键将当前用户的所有未读消息标记为已读
     * 【对应前端页面】：components/NotificationBell.vue
     * 【对应前端API】：api/notification.ts -> markAllAsRead()
     * 【可见UI操作】：点击通知弹窗右上角的【全部标为已读】按钮
     */
    @AuditLog(module = "通知管理", action = "MARKALLASREAD", description = "通知管理 全部标记已读")
    @PutMapping("/read-all")
    @RateLimit(key = "notification_read_all", limit = 10, period = 60)
    @Operation(summary = "一键标记所有通知已读")
    public ApiResponse<Void> markAllAsRead(
            @AuthenticationPrincipal JwtUserDetails currentUser) {
        notificationService.markAllAsRead(currentUser.getUserId());
        return ApiResponse.success(null);
    }
}
