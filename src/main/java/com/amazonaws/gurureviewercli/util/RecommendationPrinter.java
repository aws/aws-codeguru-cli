package com.amazonaws.gurureviewercli.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;

import lombok.NonNull;
import lombok.val;
import software.amazon.awssdk.services.codegurureviewer.model.RecommendationSummary;
import software.amazon.awssdk.services.codegurureviewer.model.Severity;

/**
 * Utility class to print recommendations.
 */
public final class RecommendationPrinter {

    private RecommendationPrinter() {
        // do not instantiate
    }

    /**
     * Print recommendations to command line.
     *
     * @param recommendations List of recommendations
     */
    public static void print(final Collection<RecommendationSummary> recommendations) {
        val sortedRecommendations = new ArrayList<>(recommendations);
        sortedRecommendations.sort(Comparator.comparing(RecommendationPrinter::severityToInt));
        for (val recommendation : sortedRecommendations) {
            val sb = new StringBuilder();
            sb.append("-----\n");
            sb.append(String.format("ID: %s, rule %s with severity %s%n",
                                    recommendation.recommendationId(),
                                    recommendation.ruleMetadata().ruleId(),
                                    recommendation.severity()));
            sb.append(String.format("In %s line %d%n", recommendation.filePath(), recommendation.startLine()));
            sb.append(recommendation.description());
            sb.append("\n");
            if (severityToInt(recommendation) < 2) {
                Log.error(sb.toString());
            } else if (severityToInt(recommendation) == 2) {
                Log.warn(sb.toString());
            } else {
                Log.info(sb.toString());
            }
        }
    }

    /**
     * Convert the severity of a {@link RecommendationSummary} to integer, where lower number
     * means higher severity.
     *
     * @param rs A {@link RecommendationSummary}.
     * @return Integer value for severity, where 0 is the highest.
     */
    public static Integer severityToInt(final RecommendationSummary rs) {
        if (rs == null || rs.severity() == null) {
            return 5;
        }
        return severityToInt(rs.severity().toString());
    }

    /**
     * Convert the severity of a {@link RecommendationSummary} to integer, where lower number
     * means higher severity.
     *
     * @param severity Severity as String.
     * @return Integer value for severity, where 0 is the highest.
     */
    public static Integer severityToInt(final @NonNull String severity) {
        if (Severity.CRITICAL.toString().equalsIgnoreCase(severity)) {
            return 0;
        } else if (Severity.HIGH.toString().equalsIgnoreCase(severity)) {
            return 1;
        } else if (Severity.MEDIUM.toString().equalsIgnoreCase(severity)) {
            return 2;
        } else if (Severity.LOW.toString().equalsIgnoreCase(severity)) {
            return 3;
        }
        return 5;
    }
}
