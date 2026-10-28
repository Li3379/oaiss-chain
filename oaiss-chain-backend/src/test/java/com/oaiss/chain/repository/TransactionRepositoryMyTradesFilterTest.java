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

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class TransactionRepositoryMyTradesFilterTest {

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("findByUserIdRelated filters by identity before pagination")
    void findByUserIdRelated_WithIdentityFilter_ShouldReturnMatchingSideTrades() {
        User currentUser = saveUser("dual-role", "Dual Role");
        User buyerCounterparty = saveUser("buyer-user", "Buyer User");
        User sellerCounterparty = saveUser("seller-user", "Seller User");

        saveTrade("TRX202401010101", 2, currentUser.getId(), buyerCounterparty.getId(), LocalDateTime.now().minusHours(2));
        saveTrade("TRX202401010102", 2, sellerCounterparty.getId(), currentUser.getId(), LocalDateTime.now().minusHours(1));

        Pageable pageable = PageRequest.of(0, 10);

        Page<Transaction> sellerResults = transactionRepository.findByUserIdRelated(
                currentUser.getId(), 2, null, null, null, "seller", null, null, pageable);
        Page<Transaction> buyerResults = transactionRepository.findByUserIdRelated(
                currentUser.getId(), 2, null, null, null, "buyer", null, null, pageable);

        assertThat(sellerResults.getContent())
                .extracting(Transaction::getTradeNo)
                .containsExactly("TRX202401010101");
        assertThat(buyerResults.getContent())
                .extracting(Transaction::getTradeNo)
                .containsExactly("TRX202401010102");
    }

    @Test
    @DisplayName("findByUserIdRelated matches keyword against counterparty name")
    void findByUserIdRelated_WithKeywordFilter_ShouldMatchCounterpartyName() {
        User currentUser = saveUser("current-user", "Current User");
        User matchingBuyer = saveUser("green-buyer", "Green Buyer");
        User otherSeller = saveUser("blue-seller", "Blue Seller");

        saveTrade("TRX202401010201", 2, currentUser.getId(), matchingBuyer.getId(), LocalDateTime.now().minusMinutes(30));
        saveTrade("TRX202401010202", 2, otherSeller.getId(), currentUser.getId(), LocalDateTime.now().minusMinutes(15));

        Pageable pageable = PageRequest.of(0, 10);

        Page<Transaction> result = transactionRepository.findByUserIdRelated(
                currentUser.getId(), 2, null, null, "green", null, null, null, pageable);

        assertThat(result.getContent())
                .extracting(Transaction::getTradeNo)
                .containsExactly("TRX202401010201");
    }

    private User saveUser(String username, String realName) {
        User user = User.builder()
                .username(username)
                .password("pwd")
                .realName(realName)
                .userType(1)
                .status(1)
                .build();
        LocalDateTime now = LocalDateTime.now();
        user.setDeleted(false);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        return userRepository.save(user);
    }

    private void saveTrade(String tradeNo, Integer tradeType, Long sellerId, Long buyerId, LocalDateTime createdAt) {
        Transaction trade = Transaction.builder()
                .tradeNo(tradeNo)
                .tradeType(tradeType)
                .sellerId(sellerId)
                .buyerId(buyerId)
                .quantity(new BigDecimal("10.0000"))
                .unitPrice(new BigDecimal("40.00"))
                .totalAmount(new BigDecimal("400.00"))
                .status(0)
                .remark("test")
                .build();
        trade.setDeleted(false);
        trade.setCreatedAt(createdAt);
        trade.setUpdatedAt(createdAt);
        transactionRepository.save(trade);
    }
}
