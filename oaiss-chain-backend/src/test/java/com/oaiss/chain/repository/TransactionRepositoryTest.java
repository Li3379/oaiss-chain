package com.oaiss.chain.repository;

import com.oaiss.chain.entity.Transaction;
import com.oaiss.chain.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TransactionRepository 集成测试
 * 测试所有自定义查询方法
 *
 * @author OAISS Team
 */
@DataJpaTest
@ActiveProfiles("test")
class TransactionRepositoryTest {

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private UserRepository userRepository;

    private Transaction transaction1;
    private Transaction transaction2;
    private Transaction transaction3;
    private Transaction deletedTransaction;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        transactionRepository.deleteAll();
        
        LocalDateTime now = LocalDateTime.now();

        // 创建测试交易数据
        transaction1 = Transaction.builder()
                .tradeNo("TRX202401010001")
                .tradeType(1) // 拍卖
                .sellerId(100L)
                .buyerId(200L)
                .quantity(new BigDecimal("100.5000"))
                .unitPrice(new BigDecimal("50.00"))
                .totalAmount(new BigDecimal("5025.00"))
                .status(2) // 已完成
                .remark("测试交易1")
                .build();
        transaction1.setDeleted(false);
        transaction1.setCreatedAt(now);
        transaction1.setUpdatedAt(now);

        transaction2 = Transaction.builder()
                .tradeNo("TRX202401010002")
                .tradeType(2) // P2P
                .sellerId(101L)
                .buyerId(200L)
                .quantity(new BigDecimal("50.0000"))
                .unitPrice(new BigDecimal("45.00"))
                .totalAmount(new BigDecimal("2250.00"))
                .status(1) // 处理中
                .remark("测试交易2")
                .build();
        transaction2.setDeleted(false);
        transaction2.setCreatedAt(now);
        transaction2.setUpdatedAt(now);

        transaction3 = Transaction.builder()
                .tradeNo("TRX202401010003")
                .tradeType(1) // 拍卖
                .sellerId(100L)
                .buyerId(201L)
                .quantity(new BigDecimal("200.0000"))
                .unitPrice(new BigDecimal("55.00"))
                .totalAmount(new BigDecimal("11000.00"))
                .status(0) // 待处理
                .remark("测试交易3")
                .build();
        transaction3.setDeleted(false);
        transaction3.setCreatedAt(now);
        transaction3.setUpdatedAt(now);

        // 已删除的交易
        deletedTransaction = Transaction.builder()
                .tradeNo("TRX202401010004")
                .tradeType(1) // 拍卖
                .sellerId(100L)
                .buyerId(200L)
                .quantity(new BigDecimal("300.0000"))
                .unitPrice(new BigDecimal("60.00"))
                .totalAmount(new BigDecimal("18000.00"))
                .status(3) // 已取消
                .remark("已删除的交易")
                .build();
        deletedTransaction.setDeleted(true);
        deletedTransaction.setCreatedAt(now);
        deletedTransaction.setUpdatedAt(now);

        transactionRepository.save(transaction1);
        transactionRepository.save(transaction2);
        transactionRepository.save(transaction3);
        transactionRepository.save(deletedTransaction);
    }

    @Test
    @DisplayName("根据交易编号查找 - 交易存在时返回交易")
    void findByTradeNo_WhenTransactionExists_ShouldReturnTransaction() {
        // When
        Optional<Transaction> result = transactionRepository.findByTradeNo("TRX202401010001");

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getTradeNo()).isEqualTo("TRX202401010001");
        assertThat(result.get().getSellerId()).isEqualTo(100L);
        assertThat(result.get().getBuyerId()).isEqualTo(200L);
    }

    @Test
    @DisplayName("根据交易编号查找 - 交易不存在时返回空")
    void findByTradeNo_WhenTransactionNotExists_ShouldReturnEmpty() {
        // When
        Optional<Transaction> result = transactionRepository.findByTradeNo("NOT_EXIST");

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("根据卖方ID分页查询 - 返回未删除的交易")
    void findBySellerIdAndDeletedFalse_ShouldReturnNonDeletedTransactions() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);

        // When
        Page<Transaction> result = transactionRepository.findBySellerIdAndDeletedFalse(100L, pageable);

        // Then
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent())
                .extracting(Transaction::getSellerId)
                .allMatch(id -> id.equals(100L));
        assertThat(result.getContent())
                .extracting(Transaction::getTradeNo)
                .containsExactlyInAnyOrder("TRX202401010001", "TRX202401010003");
    }

    @Test
    @DisplayName("根据卖方ID分页查询 - 无匹配记录时返回空页")
    void findBySellerIdAndDeletedFalse_WhenNoMatch_ShouldReturnEmptyPage() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);

        // When
        Page<Transaction> result = transactionRepository.findBySellerIdAndDeletedFalse(999L, pageable);

        // Then
        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("根据买方ID分页查询 - 返回未删除的交易")
    void findByBuyerIdAndDeletedFalse_ShouldReturnNonDeletedTransactions() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);

        // When
        Page<Transaction> result = transactionRepository.findByBuyerIdAndDeletedFalse(200L, pageable);

        // Then
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent())
                .extracting(Transaction::getBuyerId)
                .allMatch(id -> id.equals(200L));
        assertThat(result.getContent())
                .extracting(Transaction::getTradeNo)
                .containsExactlyInAnyOrder("TRX202401010001", "TRX202401010002");
    }

    @Test
    @DisplayName("根据买方ID分页查询 - 无匹配记录时返回空页")
    void findByBuyerIdAndDeletedFalse_WhenNoMatch_ShouldReturnEmptyPage() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);

        // When
        Page<Transaction> result = transactionRepository.findByBuyerIdAndDeletedFalse(999L, pageable);

        // Then
        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("根据交易类型分页查询 - 返回未删除的交易")
    void findByTradeTypeAndDeletedFalse_ShouldReturnNonDeletedTransactions() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);

        // When - 查询拍卖类型(1)
        Page<Transaction> result = transactionRepository.findByTradeTypeAndDeletedFalse(1, pageable);

        // Then
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent())
                .extracting(Transaction::getTradeType)
                .allMatch(type -> type.equals(1));
        assertThat(result.getContent())
                .extracting(Transaction::getTradeNo)
                .containsExactlyInAnyOrder("TRX202401010001", "TRX202401010003");
    }

    @Test
    @DisplayName("根据交易类型分页查询 - P2P类型返回正确结果")
    void findByTradeTypeAndDeletedFalse_WhenP2PType_ShouldReturnP2PTransactions() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);

        // When - 查询P2P类型(2)
        Page<Transaction> result = transactionRepository.findByTradeTypeAndDeletedFalse(2, pageable);

        // Then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTradeNo()).isEqualTo("TRX202401010002");
        assertThat(result.getContent().get(0).getTradeType()).isEqualTo(2);
    }

    @Test
    @DisplayName("根据交易类型分页查询 - 无匹配记录时返回空页")
    void findByTradeTypeAndDeletedFalse_WhenNoMatch_ShouldReturnEmptyPage() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);

        // When - 查询配额分配类型(3)，不存在
        Page<Transaction> result = transactionRepository.findByTradeTypeAndDeletedFalse(3, pageable);

        // Then
        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("根据状态分页查询 - 返回未删除的交易")
    void findByStatusAndDeletedFalse_ShouldReturnNonDeletedTransactions() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);

        // When - 查询已完成状态(2)
        Page<Transaction> result = transactionRepository.findByStatusAndDeletedFalse(2, pageable);

        // Then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTradeNo()).isEqualTo("TRX202401010001");
        assertThat(result.getContent().get(0).getStatus()).isEqualTo(2);
    }

    @Test
    @DisplayName("根据状态分页查询 - 处理中状态返回正确结果")
    void findByStatusAndDeletedFalse_WhenProcessingStatus_ShouldReturnProcessingTransactions() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);

        // When - 查询处理中状态(1)
        Page<Transaction> result = transactionRepository.findByStatusAndDeletedFalse(1, pageable);

        // Then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTradeNo()).isEqualTo("TRX202401010002");
        assertThat(result.getContent().get(0).getStatus()).isEqualTo(1);
    }

    @Test
    @DisplayName("根据状态分页查询 - 无匹配记录时返回空页")
    void findByStatusAndDeletedFalse_WhenNoMatch_ShouldReturnEmptyPage() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);

        // When - 查询失败状态(4)，不存在
        Page<Transaction> result = transactionRepository.findByStatusAndDeletedFalse(4, pageable);

        // Then
        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("查询未删除的交易 - 返回所有未删除的交易")
    void findByDeletedFalse_ShouldReturnAllNonDeletedTransactions() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);

        // When
        Page<Transaction> result = transactionRepository.findByDeletedFalse(pageable);

        // Then
        assertThat(result.getContent()).hasSize(3);
        assertThat(result.getTotalElements()).isEqualTo(3);
        assertThat(result.getContent())
                .extracting(Transaction::getDeleted)
                .allMatch(deleted -> !deleted);
    }

    @Test
    @DisplayName("查询未删除的交易 - 分页功能正常")
    void findByDeletedFalse_WithPagination_ShouldReturnCorrectPage() {
        // Given
        Pageable firstPage = PageRequest.of(0, 2);
        Pageable secondPage = PageRequest.of(1, 2);

        // When
        Page<Transaction> firstResult = transactionRepository.findByDeletedFalse(firstPage);
        Page<Transaction> secondResult = transactionRepository.findByDeletedFalse(secondPage);

        // Then
        assertThat(firstResult.getContent()).hasSize(2);
        assertThat(firstResult.getTotalElements()).isEqualTo(3);
        assertThat(firstResult.getTotalPages()).isEqualTo(2);
        assertThat(firstResult.isFirst()).isTrue();
        assertThat(firstResult.hasNext()).isTrue();

        assertThat(secondResult.getContent()).hasSize(1);
        assertThat(secondResult.isLast()).isTrue();
    }

    @Test
    @DisplayName("综合搜索 - 根据卖方ID筛选")
    void search_BySellerId_ShouldReturnFilteredTransactions() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);

        // When
        Page<Transaction> result = transactionRepository.search(100L, null, null, null, pageable);

        // Then
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent())
                .extracting(Transaction::getSellerId)
                .allMatch(id -> id.equals(100L));
    }

    @Test
    @DisplayName("综合搜索 - 根据买方ID筛选")
    void search_ByBuyerId_ShouldReturnFilteredTransactions() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);

        // When
        Page<Transaction> result = transactionRepository.search(null, 200L, null, null, pageable);

        // Then
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent())
                .extracting(Transaction::getBuyerId)
                .allMatch(id -> id.equals(200L));
    }

    @Test
    @DisplayName("综合搜索 - 根据交易类型筛选")
    void search_ByTradeType_ShouldReturnFilteredTransactions() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);

        // When
        Page<Transaction> result = transactionRepository.search(null, null, 2, null, pageable);

        // Then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTradeType()).isEqualTo(2);
    }

    @Test
    @DisplayName("综合搜索 - 根据状态筛选")
    void search_ByStatus_ShouldReturnFilteredTransactions() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);

        // When
        Page<Transaction> result = transactionRepository.search(null, null, null, 2, pageable);

        // Then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getStatus()).isEqualTo(2);
    }

    @Test
    @DisplayName("综合搜索 - 多条件组合筛选")
    void search_WithMultipleConditions_ShouldReturnFilteredTransactions() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);

        // When - 卖方ID=100 且 交易类型=1（拍卖）
        Page<Transaction> result = transactionRepository.search(100L, null, 1, null, pageable);

        // Then
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent())
                .extracting(Transaction::getSellerId)
                .allMatch(id -> id.equals(100L));
        assertThat(result.getContent())
                .extracting(Transaction::getTradeType)
                .allMatch(type -> type.equals(1));
    }

    @Test
    @DisplayName("综合搜索 - 所有条件为空返回全部未删除交易")
    void search_WithAllNullConditions_ShouldReturnAllNonDeletedTransactions() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);

        // When
        Page<Transaction> result = transactionRepository.search(null, null, null, null, pageable);

        // Then
        assertThat(result.getContent()).hasSize(3);
        assertThat(result.getTotalElements()).isEqualTo(3);
    }

    @Test
    @DisplayName("综合搜索 - 无匹配记录时返回空页")
    void search_WhenNoMatch_ShouldReturnEmptyPage() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);

        // When - 使用不存在的条件组合
        Page<Transaction> result = transactionRepository.search(999L, null, null, null, pageable);

        // Then
        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("综合搜索 - 确保不返回已删除的交易")
    void search_ShouldNotReturnDeletedTransactions() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);

        // When - 搜索已删除交易的卖方ID和买方ID组合
        Page<Transaction> result = transactionRepository.search(100L, 200L, 1, 3, pageable);

        // Then - 应该返回空，因为唯一匹配的交易已被删除
        assertThat(result.getContent()).isEmpty();
    }

    @Test
    @DisplayName("JPA基础操作 - 保存交易成功")
    void save_ShouldPersistTransaction() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        Transaction newTransaction = Transaction.builder()
                .tradeNo("TRX202401019999")
                .tradeType(3) // 配额分配
                .sellerId(500L)
                .buyerId(600L)
                .quantity(new BigDecimal("1000.0000"))
                .unitPrice(new BigDecimal("30.00"))
                .totalAmount(new BigDecimal("30000.00"))
                .status(0)
                .remark("新交易")
                .build();
        newTransaction.setDeleted(false);
        newTransaction.setCreatedAt(now);
        newTransaction.setUpdatedAt(now);

        // When
        Transaction saved = transactionRepository.save(newTransaction);

        // Then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getTradeNo()).isEqualTo("TRX202401019999");

        // Verify can be found
        Optional<Transaction> found = transactionRepository.findByTradeNo("TRX202401019999");
        assertThat(found).isPresent();
    }

    @Test
    @DisplayName("JPA基础操作 - 删除交易成功")
    void delete_ShouldRemoveTransaction() {
        // Given
        String tradeNo = transaction1.getTradeNo();

        // When
        transactionRepository.delete(transaction1);

        // Then
        Optional<Transaction> result = transactionRepository.findByTradeNo(tradeNo);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("JPA基础操作 - 统计交易数量")
    void count_ShouldReturnCorrectCount() {
        // When
        long count = transactionRepository.count();

        // Then
        assertThat(count).isEqualTo(4); // 包括已删除的交易
    }
}
