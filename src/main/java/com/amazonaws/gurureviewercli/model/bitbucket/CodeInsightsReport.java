package com.amazonaws.gurureviewercli.model.bitbucket;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;

/**
 * Bitbucket CodeInsight report format.
 * Example:
 * {
 *     "title": "Amazon CodeGuru Reviewer Scan Report",
 *     "details": "Some more text.",
 *     "report_type": "SECURITY",
 *     "reporter": "Amazon CodeGuru Reviewer",
 *     "link": "http://www.CodeGuruReviewer.com/reports/001",
 *     "result": "FAILED",
 *     "data": [
 *         {
 *             "title": "Duration (seconds)",
 *             "type": "DURATION",
 *             "value": 14
 *         },
 *         {
 *             "title": "Safe to merge?",
 *             "type": "BOOLEAN",
 *             "value": false
 *         }
 *     ]
 * }
 */
@Log4j2
@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CodeInsightsReport {

    private String title;

    private String details;

    private String result;

    private String link;

    private List<CodeInsightsReportData> data;

    @JsonProperty("reporter")
    private String reporter;

    @JsonProperty("report_type")
    private final String reportType = "SECURITY";

}
