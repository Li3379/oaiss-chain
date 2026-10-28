package com.oaiss.chain.entity;

import jakarta.persistence.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Entity Classes - Coverage Enhancement")
class EntityCoverageTest {

    static Stream<Class<?>> allEntityClasses() {
        return Stream.of(
                User.class, Enterprise.class, Reviewer.class, CarbonReport.class,
                CarbonNeutralProject.class, AuctionOrder.class, Transaction.class,
                MatchingResult.class, CreditScore.class, CreditEvent.class,
                EmissionRating.class, CarbonCoinAccount.class, CarbonCoinTransaction.class,
                RsaKeyPair.class, EnterpriseAdmission.class, ReviewerQualification.class,
                ThirdPartyOrg.class, OperationLog.class, BaseEntity.class
        );
    }

    @ParameterizedTest(name = "{0} - has @Entity or @MappedSuperclass")
    @MethodSource("allEntityClasses")
    void shouldBeJpaEntity(Class<?> clazz) {
        assertTrue(clazz.isAnnotationPresent(Entity.class) || clazz.isAnnotationPresent(MappedSuperclass.class));
    }

    @ParameterizedTest(name = "{0} - has @Table")
    @MethodSource("allEntityClasses")
    void shouldHaveTableAnnotation(Class<?> clazz) {
        if (clazz.isAnnotationPresent(MappedSuperclass.class)) return;
        assertTrue(clazz.isAnnotationPresent(Table.class));
    }

    @ParameterizedTest(name = "{0} - extends BaseEntity")
    @MethodSource("allEntityClasses")
    void shouldExtendBaseEntity(Class<?> clazz) {
        if (clazz.equals(BaseEntity.class)) return;
        assertTrue(BaseEntity.class.isAssignableFrom(clazz));
    }

    @ParameterizedTest(name = "{0} - has JPA fields")
    @MethodSource("allEntityClasses")
    void shouldHaveJpaFields(Class<?> clazz) {
        boolean hasJpaField = Arrays.stream(clazz.getDeclaredFields())
                .anyMatch(f -> f.isAnnotationPresent(Id.class) || f.isAnnotationPresent(Column.class));
        assertTrue(hasJpaField, clazz.getSimpleName() + " should have @Id or @Column fields");
    }

    @Test
    void baseEntity_timestamps() {
        User e = new User();
        LocalDateTime now = LocalDateTime.now();
        e.setCreatedAt(now);
        e.setUpdatedAt(now);
        assertEquals(now, e.getCreatedAt());
        assertEquals(now, e.getUpdatedAt());
    }

    @Test
    void baseEntity_softDelete() {
        User e = new User();
        assertFalse(e.getDeleted());
        e.setDeleted(true);
        assertTrue(e.getDeleted());
    }

    @Test
    void baseEntity_id() {
        User e = new User();
        e.setId(42L);
        assertEquals(42L, e.getId());
    }

    @Test
    void userEntity_fields() {
        User u = new User();
        u.setId(1L);
        u.setUsername("test");
        u.setPassword("pw");
        u.setUserType(1);
        assertEquals("test", u.getUsername());
        assertEquals(1, u.getUserType());
    }

    @Test
    void enterpriseEntity_fields() {
        Enterprise ent = new Enterprise();
        ent.setId(1L);
        ent.setEnterpriseName("Corp");
        ent.setCarbonQuota(new BigDecimal("10000"));
        assertEquals("Corp", ent.getEnterpriseName());
        assertEquals(new BigDecimal("10000"), ent.getCarbonQuota());
    }

    @Test
    void carbonReportEntity_fields() {
        CarbonReport r = new CarbonReport();
        r.setId(1L);
        r.setStatus(0);
        r.setReportNo("CR-001");
        assertEquals(0, r.getStatus());
        assertEquals("CR-001", r.getReportNo());
    }

    @Test
    void creditScoreEntity_fields() {
        CreditScore cs = new CreditScore();
        cs.setScore(85);
        cs.setLevel("EXCELLENT");
        assertEquals(85, cs.getScore());
        assertEquals("EXCELLENT", cs.getLevel());
    }

    @Test
    void transactionEntity_fields() {
        Transaction tx = new Transaction();
        tx.setId(1L);
        tx.setQuantity(new BigDecimal("100"));
        tx.setUnitPrice(new BigDecimal("50.0"));
        assertEquals(new BigDecimal("100"), tx.getQuantity());
        assertEquals(new BigDecimal("50.0"), tx.getUnitPrice());
    }

    @Test
    void enterpriseAdmissionEntity_fields() {
        EnterpriseAdmission ea = new EnterpriseAdmission();
        ea.setId(1L);
        ea.setEnterpriseId(1L);
        ea.setStatus(1);
        assertEquals(1L, ea.getEnterpriseId());
        assertEquals(1, ea.getStatus());
    }

    @Test
    void reviewerQualificationEntity_fields() {
        ReviewerQualification rq = new ReviewerQualification();
        rq.setId(1L);
        rq.setReviewerId(4L);
        rq.setCertificateNo("RQ-001");
        assertEquals(4L, rq.getReviewerId());
        assertEquals("RQ-001", rq.getCertificateNo());
    }

    @Test
    void carbonCoinAccountEntity_fields() {
        CarbonCoinAccount cca = new CarbonCoinAccount();
        cca.setId(1L);
        cca.setBalance(new BigDecimal("10000"));
        assertEquals(new BigDecimal("10000"), cca.getBalance());
    }

    @Test
    void emissionRatingEntity_fields() {
        EmissionRating er = new EmissionRating();
        er.setId(1L);
        er.setRatingLevel("B");
        er.setRatingScore(83);
        assertEquals("B", er.getRatingLevel());
        assertEquals(83, er.getRatingScore());
    }

    @Test
    void rsaKeyPairEntity_fields() {
        RsaKeyPair rkp = new RsaKeyPair();
        rkp.setId(1L);
        rkp.setUserId(2L);
        rkp.setPublicKey("abc123");
        assertEquals(2L, rkp.getUserId());
        assertEquals("abc123", rkp.getPublicKey());
    }

    @Test
    void operationLogEntity_fields() {
        OperationLog ol = new OperationLog();
        ol.setId(1L);
        ol.setUserId(1L);
        ol.setAction("CREATE");
        assertEquals("CREATE", ol.getAction());
    }

    @Test
    void creditEventEntity_fields() {
        CreditEvent ce = new CreditEvent();
        ce.setId(1L);
        ce.setEnterpriseId(1L);
        ce.setEventType(1);
        ce.setPointsChanged(-10);
        assertEquals(Integer.valueOf(1), ce.getEventType());
        assertEquals(Integer.valueOf(-10), ce.getPointsChanged());
    }

    @Test
    void thirdPartyOrgEntity_fields() {
        ThirdPartyOrg tpo = new ThirdPartyOrg();
        tpo.setId(1L);
        tpo.setOrgName("Regulator");
        tpo.setOrgCode("TP-001");
        assertEquals("Regulator", tpo.getOrgName());
    }

    @Test
    void matchingResultEntity_fields() {
        MatchingResult mr = new MatchingResult();
        mr.setId(1L);
        mr.setBuyOrderId(10L);
        mr.setSellOrderId(20L);
        mr.setMatchedQuantity(new BigDecimal("50"));
        assertEquals(new BigDecimal("50"), mr.getMatchedQuantity());
    }

    @Test
    void reviewerEntity_fields() {
        Reviewer rev = new Reviewer();
        rev.setId(1L);
        rev.setUserId(4L);
        rev.setQualificationNo("RQ-001");
        assertEquals(4L, rev.getUserId());
    }
}