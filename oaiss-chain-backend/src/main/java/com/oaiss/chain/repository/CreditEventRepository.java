package com.oaiss.chain.repository;

import com.oaiss.chain.entity.CreditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 信誉事件 Repository
 *
 * @author OAISS Team
 */
public interface CreditEventRepository extends JpaRepository<CreditEvent, Long> {

    /**
     * 查询企业信誉事件历史
     */
    Page<CreditEvent> findByEnterpriseIdAndDeletedFalse(Long enterpriseId, Pageable pageable);

    /**
     * 查询企业指定类型的事件
     */
    Page<CreditEvent> findByEnterpriseIdAndEventTypeAndDeletedFalse(Long enterpriseId, Integer eventType, Pageable pageable);
}
