package com.oaiss.chain.aop;

import com.oaiss.chain.entity.OperationLog;
import com.oaiss.chain.repository.OperationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 审计日志写入器
 *
 * <p>独立 Spring bean，承接 {@link AuditLogAspect} 的日志落库职责。
 * 必须是独立 bean（而非 aspect 内部方法）——Spring 的 {@code @Async} 基于代理，
 * 切面内部 {@code this.} 调用会绕过代理导致同步执行（曾使每个审计端点在请求线程同步写 DB）。
 * 将方法移到独立 bean 后，{@code @Async} 代理生效，日志写入在 {@code auditLogTaskExecutor}
 * 线程池异步执行。</p>
 *
 * @author OAISS Chain Team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogWriter {

    private final OperationLogRepository operationLogRepository;

    /**
     * 异步保存审计日志到数据库。
     * 通过 @Async 注解确保在独立线程池（auditLogTaskExecutor）中执行。
     */
    @Async("auditLogTaskExecutor")
    public void saveAsync(OperationLog operationLog) {
        try {
            operationLogRepository.save(operationLog);
        } catch (Exception e) {
            log.error("Async audit log save failed", e);
        }
    }
}
