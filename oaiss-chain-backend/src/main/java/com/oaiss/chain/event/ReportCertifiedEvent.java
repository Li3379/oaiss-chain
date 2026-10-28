package com.oaiss.chain.event;

import org.springframework.context.ApplicationEvent;

/**
 * Event fired after a carbon report is certified, triggering async blockchain commit.
 *
 * @author OAISS Team
 */
public class ReportCertifiedEvent extends ApplicationEvent {

    private final Long reportId;
    private final String reportData;

    public ReportCertifiedEvent(Object source, Long reportId, String reportData) {
        super(source);
        this.reportId = reportId;
        this.reportData = reportData;
    }

    public Long getReportId() { return reportId; }
    public String getReportData() { return reportData; }
}