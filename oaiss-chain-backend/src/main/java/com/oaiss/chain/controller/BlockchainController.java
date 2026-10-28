package com.oaiss.chain.controller;

import com.oaiss.chain.dto.ApiResponse;
import com.oaiss.chain.service.BlockchainServicePort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import com.oaiss.chain.annotation.AuditLog;

/**
 * ==============================================================================
 * 【业务模块】：09. 超级账本（Hyperledger Fabric）区块链浏览器与存证溯源 (Blockchain Explorer)
 * 【服务角色】：企业用户 (ENTERPRISE)、系统管理员 (ADMIN)、第三方监管 (THIRD_PARTY)
 * 【对应前端页面与路由】：
 *   - 区块链浏览器: views/enterprise/Blockchain.vue -> 路由: /enterprise/blockchain/browser
 *   - 官网首页链状态: views/OfficialHome.vue -> 路由: /official-home
 *   - 管理员终审上链: views/admin/VerifyList.vue -> 路由: /admin/verify/list
 * 【调用前端 API 模块】：src/api/blockchain.ts
 * ==============================================================================
 */
@RestController
@RequestMapping("/blockchain")
@RequiredArgsConstructor
@Tag(name = "09. 区块链管理", description = "区块链状态查询、区块信息查询、链上交易记录查询")
public class BlockchainController {

    private final BlockchainServicePort blockchainService;

    /**
     * 【业务功能】：探活底层 Hyperledger Fabric 网络健康度（通道、节点状态、最新区块高度与智能合约版本）
     * 【对应前端页面】：views/enterprise/Blockchain.vue, views/OfficialHome.vue
     * 【对应前端API】：api/blockchain.ts -> getStatus()
     * 【可见UI操作】：区块链浏览器顶部“网络状态监控卡片”及官网首页底层节点运行指示灯
     */
    @AuditLog(module = "区块链", action = "检查区块链连接状态", description = "区块链 检查区块链连接状态")
    @GetMapping("/status")
    @Operation(summary = "检查区块链连接状态", description = "检查与Hyperledger Fabric区块链网络的连接状态，返回网络名称、通道数量、节点状态等信息")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "成功获取区块链状态"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未授权访问"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "权限不足"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "区块链连接异常")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    @PreAuthorize("hasAnyRole('ADMIN', 'REVIEWER', 'ENTERPRISE', 'THIRD_PARTY')")
    public ApiResponse<Map<String, Object>> checkStatus() {
        return ApiResponse.success(blockchainService.checkConnection());
    }

    /**
     * 【业务功能】：根据区块高度（Block Number）查询具体区块的头哈希、前序哈希与完整交易列表
     * 【对应前端页面】：views/enterprise/Blockchain.vue (/enterprise/blockchain/browser)
     * 【对应前端API】：api/blockchain.ts -> queryBlock()
     * 【可见UI操作】：在检索框输入区块号或点击区块高度徽标，弹出区块详情对话框
     */
    @AuditLog(module = "区块链", action = "QUERYBLOCK", description = "区块链 QUERYBLOCK")
    @GetMapping("/block/{blockNumber}")
    @Operation(summary = "查询区块信息", description = "根据区块高度查询区块链上的区块详细信息，包括区块哈希、交易列表、时间戳等")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "成功获取区块信息"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "区块号无效"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未授权访问"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "权限不足"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "区块不存在")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    @Parameter(name = "blockNumber", description = "区块高度/区块号", required = true, example = "1000")
    @PreAuthorize("hasAnyRole('ADMIN', 'THIRD_PARTY')")
    public ApiResponse<String> queryBlock(@PathVariable Long blockNumber) {
        return ApiResponse.success(blockchainService.queryBlock(blockNumber));
    }

    /**
     * 【业务功能】：根据交易哈希 (TxHash) 穿透溯源查询单笔存证记录（防伪溯源验证）
     * 【对应前端页面】：views/enterprise/Blockchain.vue, views/enterprise/CarbonUpload.vue
     * 【对应前端API】：api/blockchain.ts -> queryTransaction()
     * 【可见UI操作】：在搜索栏输入 64 位交易哈希或点击“上链凭证 TxHash”，穿透展示防伪存证原件
     */
    @AuditLog(module = "区块链", action = "QUERYTRANSACTION", description = "区块链 QUERYTRANSACTION")
    @GetMapping("/transaction/{txHash}")
    @Operation(summary = "查询链上交易", description = "根据交易哈希查询区块链上的交易详细信息，包括交易状态、交易时间、交易数据等")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "成功获取交易信息"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "交易哈希格式无效"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "交易不存在")
    })
    @Parameter(name = "txHash", description = "交易哈希值", required = true, example = "0x1234567890abcdef...")
    public ApiResponse<String> queryTransaction(@PathVariable String txHash) {
        return ApiResponse.success(blockchainService.queryTransaction(txHash));
    }

    /**
     * 【业务功能】：分页查询链上所有碳报告存证与交易结算事务流水列表
     * 【对应前端页面】：views/enterprise/Blockchain.vue (/enterprise/blockchain/browser)
     * 【对应前端API】：api/blockchain.ts -> getTransactions()
     * 【可见UI操作】：切换至“交易存证流”Tab查看全网提交上链的交易ID、发起方及时间戳
     */
    @AuditLog(module = "区块链", action = "查询链上交易列表", description = "区块链 查询链上交易列表")
    @GetMapping("/transactions")
    @Operation(summary = "查询链上交易列表", description = "分页查询区块链上的交易记录列表")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "成功获取交易列表"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未授权访问")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENTERPRISE', 'THIRD_PARTY')")
    public ApiResponse<Page<Map<String, Object>>> listTransactions(
            @Parameter(description = "页码（从1开始）", example = "1") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页大小", example = "20") @RequestParam(defaultValue = "20") Integer size) {
        return ApiResponse.success(blockchainService.listTransactions(page, size));
    }

    /**
     * 【业务功能】：查询底层区块链最新打包生成的区块列表（出块高度、区块哈希、包含交易数）
     * 【对应前端页面】：views/enterprise/Blockchain.vue (/enterprise/blockchain/browser)
     * 【对应前端API】：api/blockchain.ts -> getLatestBlocks()
     * 【可见UI操作】：浏览器左侧“最新区块高度瀑布流”实时展示最新出块动态
     */
    @AuditLog(module = "区块链", action = "获取最新区块列表", description = "区块链 获取最新区块列表")
    @GetMapping("/blocks/latest")
    @Operation(summary = "获取最新区块列表", description = "获取区块链上最新的区块列表")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "成功获取区块列表"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未授权访问")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENTERPRISE', 'THIRD_PARTY')")
    public ApiResponse<Page<Map<String, Object>>> listLatestBlocks(
            @Parameter(description = "页码（从1开始）", example = "1") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页大小", example = "20") @RequestParam(defaultValue = "20") Integer size) {
        return ApiResponse.success(blockchainService.listLatestBlocks(page, size));
    }
}
