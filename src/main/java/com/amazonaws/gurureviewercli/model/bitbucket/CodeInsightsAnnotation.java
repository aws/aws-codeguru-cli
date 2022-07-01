package com.amazonaws.gurureviewercli.model.bitbucket;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;

/**
 * Bitbucket CodeInsight annotation.
 */
@Log4j2
@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CodeInsightsAnnotation {

    @JsonProperty("external_id")
    private String externalId;

    @JsonProperty("annotation_type")
    private String annotationType;

    private String path;

    private long line;

    private String summary;

    private String details;

    private String severity;
}
