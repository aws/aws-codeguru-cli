package com.amazonaws.gurureviewercli.model;

import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.val;
import software.amazon.awssdk.services.codegurureviewer.model.RecommendationSummary;

/**
 * Serializable recommendation class
 */
@Data
@NoArgsConstructor
public class Recommendation {

    private String filePath;
    private String recommendationId;
    private Integer startLine;
    private Integer endLine;
    private String description;
    private String recommendationCategory;
    private RuleMetadata ruleMetadata;
    private String severity;

    @Data
    public static final class RuleMetadata {
        private String ruleId;
        private String ruleName;
        private String shortDescription;
        private String longDescription;
        private List<String> ruleTags;
    }

    public RecommendationSummary toRecommendationSummary() {
        val rm = software.amazon.awssdk.services.codegurureviewer.model.
            RuleMetadata.builder()
                        .ruleId(ruleMetadata.ruleId)
                        .longDescription(ruleMetadata.longDescription)
                        .shortDescription(ruleMetadata.shortDescription)
                        .ruleName(ruleMetadata.ruleName)
                        .ruleTags(ruleMetadata.ruleTags)
                        .build();
        return RecommendationSummary.builder()
                                    .description(description)
                                    .recommendationId(recommendationId)
                                    .recommendationCategory(recommendationCategory)
                                    .filePath(filePath)
                                    .startLine(startLine)
                                    .endLine(endLine)
                                    .severity(severity)
                                    .ruleMetadata(rm)
                                    .build();
    }
}
