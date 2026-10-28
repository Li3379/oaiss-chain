package com.oaiss.chain.repository;

import com.oaiss.chain.entity.Certificate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CertificateRepository extends JpaRepository<Certificate, Long> {

    List<Certificate> findByUserIdAndDeletedFalseOrderByCreatedAtDesc(Long userId);

    Optional<Certificate> findBySerialNumberAndDeletedFalse(String serialNumber);

    boolean existsByUserIdAndStatusAndDeletedFalse(Long userId, Integer status);
}
