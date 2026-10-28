package com.oaiss.chain.repository;

import com.oaiss.chain.entity.CarbonCoinAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 碳币账户 Repository
 *
 * @author OAISS Team
 */
@Repository
public interface CarbonCoinAccountRepository extends JpaRepository<CarbonCoinAccount, Long> {

    Optional<CarbonCoinAccount> findByUserIdAndDeletedFalse(Long userId);

    boolean existsByUserIdAndDeletedFalse(Long userId);
}
