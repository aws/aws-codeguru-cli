package com.amazonaws.gurureviewercli.model.bitbucket;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;

/**
 * Bitbucket CodeInsight report.
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

    private List<CodeInsightsReportData> data;

    @JsonProperty("external_id")
    private String externalId;

    @JsonProperty("reporter")
    private String reporter;

    @JsonProperty("report_type")
    private final String reportType = "Security";

}
