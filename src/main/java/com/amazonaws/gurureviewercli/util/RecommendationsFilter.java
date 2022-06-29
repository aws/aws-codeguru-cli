package com.amazonaws.gurureviewercli.util;

import java.nio.file.FileSystems;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import lombok.val;
import software.amazon.awssdk.services.codegurureviewer.model.RecommendationSummary;

import com.amazonaws.gurureviewercli.model.configfile.CustomConfiguration;

/**
 * Utility class to filter CodeGuru Reviewer recommendations based on the criteria in a
 * custom configuration file.
 */
public final class RecommendationsFilter {

    private static final String GLOB_PREFIX = "glob:";

    private RecommendationsFilter() {
        // do not instantiate.
    }

    /**
     * Filter excluded recommendations.
     *
     * @param recommendations List of recommendations.
     * @param configuration   Custom Configuration file that defines filters.
     * @return Filtered list.
     */
    public static List<RecommendationSummary> filterRecommendations(
        final Collection<RecommendationSummary> recommendations,
        final CustomConfiguration configuration) {

        val matchers = new ArrayList<PathMatcher>();
        if (configuration.getExcludeFiles() != null) {
            for (val globString : configuration.getExcludeFiles()) {
                val matcher = FileSystems.getDefault().getPathMatcher(GLOB_PREFIX + globString);
                matchers.add(matcher);
            }
        }

        val result = new ArrayList<RecommendationSummary>();
        for (val rec : recommendations) {
            if (configuration.getExcludeBelowSeverity() != null) {
                val threshold = RecommendationPrinter.severityToInt(configuration.getExcludeBelowSeverity());
                if (RecommendationPrinter.severityToInt(rec) > threshold) {
                    continue;
                }
            }
            if (configuration.getExcludeById() != null &&
                configuration.getExcludeById()
                             .stream()
                             .anyMatch(id -> id.equals(rec.recommendationId()))) {
                continue;
            }

            if (matchers.stream().anyMatch(m -> m.matches(Paths.get(rec.filePath())))) {
                continue;
            }
            if (rec.ruleMetadata() == null) {
                continue; // Always drop rules without metadata
            }
            val metaData = rec.ruleMetadata();
            if (metaData.ruleTags() != null && configuration.getExcludeTags() != null) {
                if (configuration.getExcludeTags().stream().anyMatch(t -> metaData.ruleTags().contains(t))) {
                    continue;
                }
            }

            if (excludeRecommendation(rec, configuration)) {
                continue;
            }

            result.add(rec);
        }
        return result;
    }

    private static boolean excludeRecommendation(final RecommendationSummary recommendationSummary,
                                                 final CustomConfiguration configuration) {
        val metaData = recommendationSummary.ruleMetadata();
        if (configuration.getExcludeRecommendations() != null) {
            return configuration.getExcludeRecommendations().stream().anyMatch(ex -> {
                if (metaData.ruleId().equals(ex.getDetectorId())) {
                    if (ex.getLocations() != null && !ex.getLocations().isEmpty()) {
                        for (val globString : ex.getLocations()) {
                            val matcher = FileSystems.getDefault().getPathMatcher(GLOB_PREFIX + globString);
                            if (matcher.matches(Paths.get(recommendationSummary.filePath()))) {
                                return true;
                            }
                        }
                        return false;
                    } else {
                        return true;
                    }
                }
                return false;
            });
        }
        return false;
    }
}
