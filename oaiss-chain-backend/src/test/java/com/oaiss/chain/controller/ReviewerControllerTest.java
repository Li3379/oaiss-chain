package com.oaiss.chain.controller;

import com.oaiss.chain.entity.CarbonReport;
import com.oaiss.chain.entity.Reviewer;
import com.oaiss.chain.entity.ReviewerQualification;
import com.oaiss.chain.repository.ReviewerRepository;
import com.oaiss.chain.security.JwtTokenProvider;
import com.oaiss.chain.security.JwtUserDetails;
import com.oaiss.chain.service.ReviewerQualificationService;
import com.oaiss.chain.service.ReviewerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReviewerController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("ReviewerController unit tests")
class ReviewerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReviewerService reviewerService;

    @MockBean
    private ReviewerQualificationService reviewerQualificationService;

    @MockBean
    private ReviewerRepository reviewerRepository;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    private void setAuthentication() {
        JwtUserDetails user = JwtUserDetails.builder()
                .userId(1L)
                .username("reviewer")
                .roles(List.of("REVIEWER"))
                .enabled(true)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities())
        );
    }

    @Test
    @WithMockUser(username = "reviewer", roles = "REVIEWER")
    @DisplayName("GET /reviewer/qualification/my should return current reviewer qualifications")
    void getMyQualification_shouldReturnQualifications() throws Exception {
        setAuthentication();
        Reviewer reviewer = Reviewer.builder().userId(1L).build();
        reviewer.setId(9L);
        ReviewerQualification qualification = ReviewerQualification.builder()
                .reviewerId(9L)
                .qualificationType("CERT")
                .certificateNo("Q-001")
                .build();

        when(reviewerRepository.findByUserIdAndDeletedFalse(1L)).thenReturn(Optional.of(reviewer));
        when(reviewerQualificationService.getMyCertificate(9L)).thenReturn(List.of(qualification));

        mockMvc.perform(get("/reviewer/qualification/my")
                        .principal(() -> "reviewer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].certificateNo").value("Q-001"));
    }

    @Test
    @WithMockUser(username = "reviewer", roles = "REVIEWER")
    @DisplayName("GET /reviewer/info should return reviewer info")
    void getReviewerInfo_shouldReturnReviewerInfo() throws Exception {
        setAuthentication();
        Reviewer reviewer = Reviewer.builder().userId(1L).qualificationNo("RV-001").name("审核员甲").build();
        when(reviewerService.getReviewerInfo(1L)).thenReturn(reviewer);

        mockMvc.perform(get("/reviewer/info")
                        .principal(() -> "reviewer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("审核员甲"));
    }

    @Test
    @WithMockUser(username = "reviewer", roles = "REVIEWER")
    @DisplayName("GET /reviewer/reports/pending should return pending page")
    void getPendingReports_shouldReturnPendingPage() throws Exception {
        setAuthentication();
        CarbonReport report = CarbonReport.builder().title("待审报告").reportNo("CR-001").build();
        Page<CarbonReport> page = new PageImpl<>(List.of(report));
        when(reviewerService.getPendingReports(eq(1L), eq(2), eq(5))).thenReturn(page);

        mockMvc.perform(get("/reviewer/reports/pending")
                        .param("page", "2")
                        .param("size", "5")
                        .principal(() -> "reviewer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].title").value("待审报告"));
    }

    @Test
    @WithMockUser(username = "reviewer", roles = "REVIEWER")
    @DisplayName("GET /reviewer/history should return history page")
    void getReviewHistory_shouldReturnHistoryPage() throws Exception {
        setAuthentication();
        CarbonReport report = CarbonReport.builder().title("已审报告").reportNo("CR-002").build();
        Page<CarbonReport> page = new PageImpl<>(List.of(report));
        when(reviewerService.getReviewHistory(eq(1L), eq(1), eq(10))).thenReturn(page);

        mockMvc.perform(get("/reviewer/history")
                        .param("page", "1")
                        .param("size", "10")
                        .principal(() -> "reviewer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].title").value("已审报告"));
    }

    @Test
    @WithMockUser(username = "reviewer", roles = "REVIEWER")
    @DisplayName("GET /reviewer/statistics should return statistics map")
    void getStatistics_shouldReturnStatistics() throws Exception {
        setAuthentication();
        when(reviewerService.getStatistics(1L)).thenReturn(Map.of("reviewedCount", 7, "pendingCount", 2));

        mockMvc.perform(get("/reviewer/statistics")
                        .principal(() -> "reviewer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewedCount").value(7))
                .andExpect(jsonPath("$.data.pendingCount").value(2));
    }
}
