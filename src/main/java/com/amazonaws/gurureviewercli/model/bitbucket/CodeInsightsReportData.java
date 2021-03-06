package com.amazonaws.gurureviewercli.model.bitbucket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;

/**
 * Bitbucket CodeInsight report data.
 * See https://developer.atlassian.com/cloud/bitbucket/rest/api-group-reports/#api-group-reports
 */
@Log4j2
@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CodeInsightsReportData {

    private String title;

    private String type;

    private Object value;
}
