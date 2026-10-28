package com.oaiss.chain.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ReportStatusEnum unit tests")
class ReportStatusEnumTest {

    @Test
    @DisplayName("fromCode should resolve all known codes")
    void fromCode_shouldResolveKnownCodes() {
        assertEquals(ReportStatusEnum.DRAFT, ReportStatusEnum.fromCode(0));
        assertEquals(ReportStatusEnum.SUBMITTED, ReportStatusEnum.fromCode(1));
        assertEquals(ReportStatusEnum.UNDER_REVIEW, ReportStatusEnum.fromCode(2));
        assertEquals(ReportStatusEnum.APPROVED, ReportStatusEnum.fromCode(3));
        assertEquals(ReportStatusEnum.REJECTED, ReportStatusEnum.fromCode(4));
        assertEquals(ReportStatusEnum.ON_CHAIN, ReportStatusEnum.fromCode(5));
    }

    @Test
    @DisplayName("fromCode should throw for unknown code")
    void fromCode_shouldThrowForUnknownCode() {
        assertThrows(IllegalArgumentException.class, () -> ReportStatusEnum.fromCode(99));
    }

    @Test
    @DisplayName("getCode should return correct integer codes")
    void getCode_shouldReturnCorrectCodes() {
        assertEquals(0, ReportStatusEnum.DRAFT.getCode());
        assertEquals(1, ReportStatusEnum.SUBMITTED.getCode());
        assertEquals(2, ReportStatusEnum.UNDER_REVIEW.getCode());
        assertEquals(3, ReportStatusEnum.APPROVED.getCode());
        assertEquals(4, ReportStatusEnum.REJECTED.getCode());
        assertEquals(5, ReportStatusEnum.ON_CHAIN.getCode());
    }

    @Test
    @DisplayName("isEditable should be true for DRAFT and REJECTED")
    void isEditable_shouldBeTrueForDraftAndRejected() {
        assertTrue(ReportStatusEnum.DRAFT.isEditable());
        assertTrue(ReportStatusEnum.REJECTED.isEditable());
    }

    @Test
    @DisplayName("isEditable should be false for other statuses")
    void isEditable_shouldBeFalseForOtherStatuses() {
        assertFalse(ReportStatusEnum.SUBMITTED.isEditable());
        assertFalse(ReportStatusEnum.UNDER_REVIEW.isEditable());
        assertFalse(ReportStatusEnum.APPROVED.isEditable());
        assertFalse(ReportStatusEnum.ON_CHAIN.isEditable());
    }

    @Test
    @DisplayName("isSubmittable should be true for DRAFT and REJECTED")
    void isSubmittable_shouldBeTrueForDraftAndRejected() {
        assertTrue(ReportStatusEnum.DRAFT.isSubmittable());
        assertTrue(ReportStatusEnum.REJECTED.isSubmittable());
    }

    @Test
    @DisplayName("isSubmittable should be false for other statuses")
    void isSubmittable_shouldBeFalseForOtherStatuses() {
        assertFalse(ReportStatusEnum.SUBMITTED.isSubmittable());
        assertFalse(ReportStatusEnum.UNDER_REVIEW.isSubmittable());
        assertFalse(ReportStatusEnum.APPROVED.isSubmittable());
        assertFalse(ReportStatusEnum.ON_CHAIN.isSubmittable());
    }

    @Test
    @DisplayName("isReviewable should be true for SUBMITTED and UNDER_REVIEW")
    void isReviewable_shouldBeTrueForSubmittedAndUnderReview() {
        assertTrue(ReportStatusEnum.SUBMITTED.isReviewable());
        assertTrue(ReportStatusEnum.UNDER_REVIEW.isReviewable());
    }

    @Test
    @DisplayName("isReviewable should be false for other statuses")
    void isReviewable_shouldBeFalseForOtherStatuses() {
        assertFalse(ReportStatusEnum.DRAFT.isReviewable());
        assertFalse(ReportStatusEnum.APPROVED.isReviewable());
        assertFalse(ReportStatusEnum.REJECTED.isReviewable());
        assertFalse(ReportStatusEnum.ON_CHAIN.isReviewable());
    }

    @Test
    @DisplayName("getDescription should return non-null descriptions")
    void getDescription_shouldReturnNonNullDescriptions() {
        for (ReportStatusEnum e : ReportStatusEnum.values()) {
            assertNotNull(e.getDescription());
            assertFalse(e.getDescription().isEmpty());
        }
    }
}
