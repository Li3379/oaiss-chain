package com.oaiss.chain.service;

import com.oaiss.chain.dto.SearchResponse;
import com.oaiss.chain.entity.Enterprise;
import com.oaiss.chain.entity.Transaction;
import com.oaiss.chain.entity.User;
import com.oaiss.chain.repository.CarbonReportRepository;
import com.oaiss.chain.repository.EnterpriseRepository;
import com.oaiss.chain.repository.TransactionRepository;
import com.oaiss.chain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * S6 接缝：交易流水关键词检索。
 *
 * <p>存在理由：{@code GET /search/trades} 的参数只有 sellerId/buyerId/tradeType/status，
 * 没有 keyword；P2P 页面的关键词搜索是前端把「我的交易」全量拉下来本地过滤的。
 * 而原始需求 §3.3.2 明确要求「市场信息查询」支持关键词，且「按企业账户进行」检索——
 * 企业身份是主键维度，审核员要按企业查工单、监管要按企业查流水，三者是同一条能力。
 *
 * <p>关键词覆盖三个字段，缺一不可：交易编号（企业自己手上的凭证）、
 * 企业名称（监管与审核员的自然检索方式）、统一社会信用代码（唯一的法定标识，
 * 名称会变、信用代码不变）。
 *
 * <p>会漏掉的：中文分词的召回质量、以及大数据量下的索引效率——前者需要分词组件，
 * 后者需要执行计划分析，都不是本接缝能覆盖的。
 */
class SearchTradeKeywordTest {

    private TransactionRepository transactionRepository;
    private CarbonReportRepository carbonReportRepository;
    private EnterpriseRepository enterpriseRepository;
    private UserRepository userRepository;
    private SearchService searchService;

    @BeforeEach
    void setUp() {
        transactionRepository = mock(TransactionRepository.class);
        carbonReportRepository = mock(CarbonReportRepository.class);
        enterpriseRepository = mock(EnterpriseRepository.class);
        userRepository = mock(UserRepository.class);
        searchService = new SearchService(carbonReportRepository, transactionRepository,
                enterpriseRepository, userRepository, null);

        when(transactionRepository.searchWithUserBoundAndKeyword(
                any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), Pageable.ofSize(10), 0));
        when(transactionRepository.searchWithKeyword(
                any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), Pageable.ofSize(10), 0));
        when(transactionRepository.searchWithUserBound(
                any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), Pageable.ofSize(10), 0));
        when(transactionRepository.search(
                any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), Pageable.ofSize(10), 0));
        when(enterpriseRepository.findAll()).thenReturn(List.of());
    }

    @Test
    @DisplayName("传入 keyword 时走带关键词的查询，而不是全量筛选")
    void keyword_usesKeywordQuery() {
        searchService.searchTradesWithKeyword(null, "碳能", null, null, null, null, 1, 10);

        verify(transactionRepository).searchWithKeyword(
                eq("碳能"), isNull(), isNull(), isNull(), isNull(), any(Pageable.class));
        verify(transactionRepository, never()).search(
                any(), any(), any(), any(), any(Pageable.class));
    }

    @Test
    @DisplayName("未传 keyword 时行为不变（走原查询，避免无谓的 like 开销）")
    void noKeyword_keepsExistingQuery() {
        searchService.searchTradesWithKeyword(null, null, null, null, null, null, 1, 10);

        // 原路径是 searchWithUserBound（boundUserId 为 null 时同样用它），不是 search()
        verify(transactionRepository).searchWithUserBound(
                any(), any(), any(), any(), any(), any(Pageable.class));
        verify(transactionRepository, never()).searchWithKeyword(
                any(), any(), any(), any(), any(), any(Pageable.class));
    }

    @Test
    @DisplayName("空白 keyword 视为未传（避免 like '%%' 全表扫描）")
    void blankKeyword_treatedAsAbsent() {
        searchService.searchTradesWithKeyword(null, "   ", null, null, null, null, 1, 10);

        verify(transactionRepository).searchWithUserBound(
                any(), any(), any(), any(), any(), any(Pageable.class));
        verify(transactionRepository, never()).searchWithKeyword(
                any(), any(), any(), any(), any(), any(Pageable.class));
    }

    @Test
    @DisplayName("关键词检索仍受企业租户边界约束（越权检索不可因新参数被绕过）")
    void keyword_stillHonorsTenantBoundary() {
        com.oaiss.chain.security.JwtUserDetails enterpriseUser = com.oaiss.chain.security.JwtUserDetails.builder()
                .userId(5L)
                .username("ent")
                .userType(1)
                .roles(List.of("ENTERPRISE"))
                .enabled(true)
                .build();

        searchService.searchTradesWithKeyword(enterpriseUser, "碳能", null, null, null, null, 1, 10);

        // 企业用户必须带上 userId 边界，走带边界的查询
        verify(transactionRepository).searchWithUserBoundAndKeyword(
                eq(5L), eq("碳能"), isNull(), isNull(), isNull(), isNull(), any(Pageable.class));
    }

    @Test
    @DisplayName("关键词前后空白被裁剪，不参与匹配")
    void keyword_isTrimmed() {
        searchService.searchTradesWithKeyword(null, "  碳能  ", null, null, null, null, 1, 10);

        verify(transactionRepository).searchWithKeyword(
                eq("碳能"), any(), any(), any(), any(), any(Pageable.class));
    }
}