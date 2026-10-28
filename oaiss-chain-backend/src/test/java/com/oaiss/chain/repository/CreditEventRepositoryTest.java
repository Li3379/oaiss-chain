package com.oaiss.chain.repository;

import com.oaiss.chain.entity.CreditEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class CreditEventRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private CreditEventRepository repository;

    private CreditEvent event1;
    private CreditEvent event2;
    private CreditEvent deletedEvent;

    @BeforeEach
    void setUp() {
        event1 = createEvent(1L, 1, -20, 80, 60);
        event2 = createEvent(1L, 5, 5, 60, 65);
        deletedEvent = createEvent(1L, 2, -5, 65, 60);
        deletedEvent.setDeleted(true);

        entityManager.persist(event1);
        entityManager.persist(event2);
        entityManager.persist(deletedEvent);
        entityManager.flush();
    }

    private CreditEvent createEvent(Long enterpriseId, Integer eventType,
                                     Integer pointsChanged, Integer scoreBefore, Integer scoreAfter) {
        CreditEvent event = CreditEvent.builder()
                .enterpriseId(enterpriseId)
                .eventType(eventType)
                .eventDescription("Test event")
                .pointsChanged(pointsChanged)
                .scoreBefore(scoreBefore)
                .scoreAfter(scoreAfter)
                .triggeredAt(LocalDateTime.now())
                .build();
        event.setCreatedAt(LocalDateTime.now());
        event.setUpdatedAt(LocalDateTime.now());
        return event;
    }

    @Test
    @DisplayName("findByEnterpriseIdAndDeletedFalse should return enterprise events")
    void findByEnterpriseIdAndDeletedFalse_shouldReturnEvents() {
        Page<CreditEvent> result = repository.findByEnterpriseIdAndDeletedFalse(
                1L, PageRequest.of(0, 10));
        assertThat(result.getContent()).hasSize(2);
    }

    @Test
    @DisplayName("findByEnterpriseIdAndDeletedFalse should exclude deleted")
    void findByEnterpriseIdAndDeletedFalse_shouldExcludeDeleted() {
        Page<CreditEvent> result = repository.findByEnterpriseIdAndDeletedFalse(
                1L, PageRequest.of(0, 10));
        assertThat(result.getContent()).allMatch(e -> !e.getDeleted());
    }

    @Test
    @DisplayName("findByEnterpriseIdAndEventTypeAndDeletedFalse should filter by type")
    void findByEnterpriseIdAndEventType_shouldFilterByType() {
        Page<CreditEvent> result = repository.findByEnterpriseIdAndEventTypeAndDeletedFalse(
                1L, 1, PageRequest.of(0, 10));
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getEventType()).isEqualTo(1);
    }

    @Test
    @DisplayName("findByEnterpriseIdAndEventTypeAndDeletedFalse should return empty for no match")
    void findByEnterpriseIdAndEventType_shouldReturnEmptyForNoMatch() {
        Page<CreditEvent> result = repository.findByEnterpriseIdAndEventTypeAndDeletedFalse(
                999L, 1, PageRequest.of(0, 10));
        assertThat(result.getContent()).isEmpty();
    }
}
