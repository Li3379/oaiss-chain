package com.oaiss.chain.repository;

import com.oaiss.chain.entity.RsaKeyPair;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * RSA密钥对数据访问层
 * RSA Key Pair Repository
 * 
 * @author OAISS Team
 */
@Repository
public interface RsaKeyPairRepository extends JpaRepository<RsaKeyPair, Long> {

    /**
     * 根据用户ID查找有效的密钥对
     * Find active key pair by user ID
     * 
     * @param userId 用户ID
     * @return 密钥对（如果存在）
     */
    Optional<RsaKeyPair> findByUserIdAndDeletedFalse(Long userId);

    /**
     * 根据用户ID和密钥状态查找密钥对
     * Find key pair by user ID and key status
     * 
     * @param userId 用户ID
     * @param keyStatus 密钥状态
     * @return 密钥对（如果存在）
     */
    Optional<RsaKeyPair> findByUserIdAndKeyStatusAndDeletedFalse(Long userId, Integer keyStatus);

    /**
     * 根据用户ID查找最新版本的密钥对
     * Find latest version key pair by user ID
     * 
     * @param userId 用户ID
     * @return 密钥对（如果存在）
     */
    @Query("SELECT r FROM RsaKeyPair r WHERE r.userId = :userId AND r.deleted = false " +
           "ORDER BY r.keyVersion DESC LIMIT 1")
    Optional<RsaKeyPair> findLatestByUserId(@Param("userId") Long userId);

    /**
     * 检查用户是否已有有效密钥对
     * Check if user has active key pair
     * 
     * @param userId 用户ID
     * @return 是否存在
     */
    boolean existsByUserIdAndDeletedFalse(Long userId);

    /**
     * 根据用户ID和密钥版本查找密钥对
     * Find key pair by user ID and key version
     * 
     * @param userId 用户ID
     * @param keyVersion 密钥版本
     * @return 密钥对（如果存在）
     */
    Optional<RsaKeyPair> findByUserIdAndKeyVersionAndDeletedFalse(Long userId, Integer keyVersion);

    /**
     * 查找所有未加密的密钥对
     * Find all key pairs where the private key is not yet encrypted
     *
     * @return 未加密的密钥对列表
     */
    List<RsaKeyPair> findByEncryptedAndDeletedFalse(Boolean encrypted);
}
