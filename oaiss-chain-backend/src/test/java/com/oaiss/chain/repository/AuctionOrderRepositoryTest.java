package com.oaiss.chain.repository;

import com.oaiss.chain.entity.AuctionOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AuctionOrderRepository 数据访问层测试
 * 
 * @author OAISS Team
 */
@DataJpaTest
@ActiveProfiles("test")
@DisplayName("拍卖挂单仓储测试")
class AuctionOrderRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private AuctionOrderRepository auctionOrderRepository;

    private AuctionOrder buyOrder;
    private AuctionOrder sellOrder;

    @BeforeEach
    void setUp() {
        LocalDateTime now = LocalDateTime.now();
        
        // 创建买入挂单
        buyOrder = AuctionOrder.builder()
                .orderNo("AO20240101001")
                .userId(1L)
                .direction(1) // 买入
                .price(new BigDecimal("100.00"))
                .quantity(new BigDecimal("10.00"))
                .matchedQuantity(BigDecimal.ZERO)
                .status(0) // 待匹配
                .build();
        buyOrder.setDeleted(false);
        buyOrder.setCreatedAt(now);
        buyOrder.setUpdatedAt(now);

        // 创建卖出挂单
        sellOrder = AuctionOrder.builder()
                .orderNo("AO20240101002")
                .userId(2L)
                .direction(2) // 卖出
                .price(new BigDecimal("95.00"))
                .quantity(new BigDecimal("15.00"))
                .matchedQuantity(BigDecimal.ZERO)
                .status(0) // 待匹配
                .build();
        sellOrder.setDeleted(false);
        sellOrder.setCreatedAt(now);
        sellOrder.setUpdatedAt(now);
    }

    @Nested
    @DisplayName("findByUserIdAndDeletedFalse 测试")
    class FindByUserIdTests {

        @Test
        @DisplayName("根据用户ID查询挂单 - 应返回该用户的挂单")
        void findByUserIdAndDeletedFalse_ShouldReturnUserOrders() {
            // Given
            entityManager.persistAndFlush(buyOrder);
            entityManager.persistAndFlush(sellOrder);
            Pageable pageable = PageRequest.of(0, 10);

            // When
            Page<AuctionOrder> result = auctionOrderRepository.findByUserIdAndDeletedFalse(1L, pageable);

            // Then
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getUserId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("根据用户ID查询挂单 - 无挂单时返回空页")
        void findByUserIdAndDeletedFalse_WhenNoOrders_ShouldReturnEmpty() {
            // Given
            Pageable pageable = PageRequest.of(0, 10);

            // When
            Page<AuctionOrder> result = auctionOrderRepository.findByUserIdAndDeletedFalse(999L, pageable);

            // Then
            assertThat(result.getContent()).isEmpty();
        }
    }

    @Nested
    @DisplayName("买入挂单查询测试")
    class BuyOrderQueryTests {

        @Test
        @DisplayName("查询买入挂单 - 按价格降序排列")
        void findByDirectionAndStatusIn_OrderByPriceDesc_ShouldReturnBuyOrdersSorted() {
            // Given
            LocalDateTime now = LocalDateTime.now();
            
            AuctionOrder buy1 = AuctionOrder.builder()
                    .orderNo("AO20240101003")
                    .userId(1L)
                    .direction(1)
                    .price(new BigDecimal("100.00"))
                    .quantity(new BigDecimal("10.00"))
                    .matchedQuantity(BigDecimal.ZERO)
                    .status(1) // 匹配中
                    .build();
            buy1.setDeleted(false);
            buy1.setCreatedAt(now);
            buy1.setUpdatedAt(now);

            AuctionOrder buy2 = AuctionOrder.builder()
                    .orderNo("AO20240101004")
                    .userId(3L)
                    .direction(1)
                    .price(new BigDecimal("120.00"))
                    .quantity(new BigDecimal("5.00"))
                    .matchedQuantity(BigDecimal.ZERO)
                    .status(1) // 匹配中
                    .build();
            buy2.setDeleted(false);
            buy2.setCreatedAt(now);
            buy2.setUpdatedAt(now);

            entityManager.persistAndFlush(buy1);
            entityManager.persistAndFlush(buy2);

            List<Integer> statuses = Arrays.asList(1);

            // When
            List<AuctionOrder> result = auctionOrderRepository
                    .findByDirectionAndStatusInAndDeletedFalseOrderByPriceDesc(1, statuses);

            // Then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getPrice()).isEqualByComparingTo("120.00"); // 最高价优先
            assertThat(result.get(1).getPrice()).isEqualByComparingTo("100.00");
        }
    }

    @Nested
    @DisplayName("卖出挂单查询测试")
    class SellOrderQueryTests {

        @Test
        @DisplayName("查询卖出挂单 - 按价格升序排列")
        void findByDirectionAndStatusIn_OrderByPriceAsc_ShouldReturnSellOrdersSorted() {
            // Given
            LocalDateTime now = LocalDateTime.now();
            
            AuctionOrder sell1 = AuctionOrder.builder()
                    .orderNo("AO20240101005")
                    .userId(2L)
                    .direction(2)
                    .price(new BigDecimal("95.00"))
                    .quantity(new BigDecimal("10.00"))
                    .matchedQuantity(BigDecimal.ZERO)
                    .status(1) // 匹配中
                    .build();
            sell1.setDeleted(false);
            sell1.setCreatedAt(now);
            sell1.setUpdatedAt(now);

            AuctionOrder sell2 = AuctionOrder.builder()
                    .orderNo("AO20240101006")
                    .userId(4L)
                    .direction(2)
                    .price(new BigDecimal("80.00"))
                    .quantity(new BigDecimal("8.00"))
                    .matchedQuantity(BigDecimal.ZERO)
                    .status(1) // 匹配中
                    .build();
            sell2.setDeleted(false);
            sell2.setCreatedAt(now);
            sell2.setUpdatedAt(now);

            entityManager.persistAndFlush(sell1);
            entityManager.persistAndFlush(sell2);

            List<Integer> statuses = Arrays.asList(1);

            // When
            List<AuctionOrder> result = auctionOrderRepository
                    .findByDirectionAndStatusInAndDeletedFalseOrderByPriceAsc(2, statuses);

            // Then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getPrice()).isEqualByComparingTo("80.00"); // 最低价优先
            assertThat(result.get(1).getPrice()).isEqualByComparingTo("95.00");
        }
    }

    @Nested
    @DisplayName("findByDirectionAndStatusAndDeletedFalse 测试")
    class FindByDirectionAndStatusTests {

        @Test
        @DisplayName("根据方向和状态分页查询 - 应返回匹配挂单")
        void findByDirectionAndStatusAndDeletedFalse_ShouldReturnMatchingOrders() {
            // Given
            buyOrder.setStatus(1); // 设置为匹配状态
            entityManager.persistAndFlush(buyOrder);
            entityManager.persistAndFlush(sellOrder);
            Pageable pageable = PageRequest.of(0, 10);

            // When
            Page<AuctionOrder> result = auctionOrderRepository.findByDirectionAndStatusAndDeletedFalse(1, 1, pageable);

            // Then
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getDirection()).isEqualTo(1);
            assertThat(result.getContent().get(0).getStatus()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("findByDeletedFalse 测试")
    class FindByDeletedFalseTests {

        @Test
        @DisplayName("查询所有未删除挂单 - 应返回所有挂单")
        void findByDeletedFalse_ShouldReturnAllNotDeleted() {
            // Given
            entityManager.persistAndFlush(buyOrder);
            entityManager.persistAndFlush(sellOrder);
            Pageable pageable = PageRequest.of(0, 10);

            // When
            Page<AuctionOrder> result = auctionOrderRepository.findByDeletedFalse(pageable);

            // Then
            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getContent()).allMatch(o -> !o.getDeleted());
        }
    }

    @Nested
    @DisplayName("findByUserIdAndDirectionAndStatusAndDeletedFalse 测试")
    class FindByUserIdDirectionStatusTests {

        @Test
        @DisplayName("多条件查询 - 应返回匹配挂单")
        void findByUserIdAndDirectionAndStatusAndDeletedFalse_ShouldReturnMatchingOrders() {
            // Given
            buyOrder.setStatus(1); // 设置为匹配状态
            entityManager.persistAndFlush(buyOrder);
            Pageable pageable = PageRequest.of(0, 10);

            // When
            Page<AuctionOrder> result = auctionOrderRepository.findByUserIdAndDirectionAndStatusAndDeletedFalse(
                    1L, 1, 1, pageable);

            // Then
            assertThat(result.getContent()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("findByUserIdAndDirectionAndDeletedFalse 测试")
    class FindByUserIdAndDirectionTests {

        @Test
        @DisplayName("根据用户ID和方向查询 - 应返回匹配挂单")
        void findByUserIdAndDirectionAndDeletedFalse_ShouldReturnMatchingOrders() {
            // Given
            entityManager.persistAndFlush(buyOrder);
            entityManager.persistAndFlush(sellOrder);
            Pageable pageable = PageRequest.of(0, 10);

            // When
            Page<AuctionOrder> result = auctionOrderRepository.findByUserIdAndDirectionAndDeletedFalse(1L, 1, pageable);

            // Then
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getUserId()).isEqualTo(1L);
            assertThat(result.getContent().get(0).getDirection()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("JPA基础操作测试")
    class JpaBasicTests {

        @Test
        @DisplayName("保存挂单 - 应成功保存并生成ID")
        void save_ShouldPersistOrderWithId() {
            // When
            AuctionOrder saved = auctionOrderRepository.save(buyOrder);

            // Then
            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getDirection()).isEqualTo(1);
        }

        @Test
        @DisplayName("更新挂单状态 - 应成功更新")
        void update_ShouldUpdateOrderStatus() {
            // Given
            AuctionOrder saved = entityManager.persistAndFlush(buyOrder);
            saved.setStatus(2); // 已撮合

            // When
            AuctionOrder updated = auctionOrderRepository.save(saved);

            // Then
            assertThat(updated.getStatus()).isEqualTo(2);
        }

        @Test
        @DisplayName("删除挂单 - 应标记为已删除")
        void delete_ShouldMarkAsDeleted() {
            // Given
            AuctionOrder saved = entityManager.persistAndFlush(buyOrder);

            // When
            saved.setDeleted(true);
            auctionOrderRepository.save(saved);

            // Then
            var found = auctionOrderRepository.findById(saved.getId());
            assertThat(found).isPresent();
            assertThat(found.get().getDeleted()).isTrue();
        }
    }
}