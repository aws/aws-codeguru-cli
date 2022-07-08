package com.amazonaws.gurureviewercli.model.bitbucket;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;

/**
 * Bitbucket CodeInsight annotation.
 * Example
 *     {
 *           "external_id": "CodeGuruReviewer-02-annotation002",
 *           "title": "Bug report",
 *           "annotation_type": "BUG",
 *           "summary": "This line might introduce a bug.",
 *           "severity": "MEDIUM",
 *           "path": "my-service/src/main/java/com/myCompany/mysystem/logic/Helper.java",
 *           "line": 13
 *     }
 */
@Log4j2
@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CodeInsightsAnnotation {

    private String title;

    @JsonProperty("external_id")
    private String externalId;

    @JsonProperty("annotation_type")
    private String annotationType;

    private String path;

    private long line;

    private String summary;

    private String severity;
}
