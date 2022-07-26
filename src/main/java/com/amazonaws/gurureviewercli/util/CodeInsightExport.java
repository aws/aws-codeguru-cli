package com.amazonaws.gurureviewercli.util;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import lombok.val;
import software.amazon.awssdk.services.codegurureviewer.model.RecommendationSummary;
import software.amazon.awssdk.services.codegurureviewer.model.Severity;

import com.amazonaws.gurureviewercli.model.ScanMetaData;
import com.amazonaws.gurureviewercli.model.bitbucket.CodeInsightsAnnotation;
import com.amazonaws.gurureviewercli.model.bitbucket.CodeInsightsReport;

/**
 * Export Report and Annotations file for BitBucket CodeInsights.
 */
public final class CodeInsightExport {
    private static final String REPORT_FILE_NAME = "report.json";
    private static final String ANNOTATIONS_FILE_NAME = "annotations.json";

    private static final JsonMapper JSON_MAPPER =
        JsonMapper.builder()
                  .serializationInclusion(JsonInclude.Include.NON_ABSENT)
                  .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                  .disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
                  .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
                  .enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE)
                  .build();

    public static void report(final Collection<RecommendationSummary> recommendations,
                              final ScanMetaData scanMetaData,
                              final Path outputDir) throws IOException {
        val reportTitle = "CodeGuru Reviewer report";
        val url = String.format("https://console.aws.amazon.com/codeguru/reviewer?region=%s#/codereviews/details/%s",
                                scanMetaData.getRegion(), scanMetaData.getCodeReviewArn());
        val report = CodeInsightsReport.builder()
                                       .title(reportTitle)
                                       .reporter("CodeGuru Reviewer CLI")
                                       .details(String.format("CodeGuru Reviewer reported %d recommendations",
                                                              recommendations.size()))
                                       .result(recommendations.isEmpty() ? "PASSED" : "FAILED")
                                       .link(url)
                                       .data(new ArrayList<>())
                                       .build();

        val annotations = recommendations.stream().map(r -> convert(r, reportTitle))
                                         .collect(Collectors.toList());

        JSON_MAPPER.writeValue(outputDir.resolve(REPORT_FILE_NAME).toFile(), report);
        JSON_MAPPER.writeValue(outputDir.resolve(ANNOTATIONS_FILE_NAME).toFile(), annotations);
    }

    private static CodeInsightsAnnotation convert(final RecommendationSummary recommendation,
                                                  final String reportTitle) {
        String description = recommendation.recommendationCategoryAsString();
        if (recommendation.ruleMetadata() != null) {
            description = recommendation.ruleMetadata().shortDescription();
        }

        return CodeInsightsAnnotation.builder()
                                     .title(reportTitle)
                                     .externalId(recommendation.recommendationId())
                                     .path(recommendation.filePath())
                                     .line(recommendation.startLine())
                                     .summary(description)
                                     .details("TODO: add details here.")
                                     .link("https://github.com/martinschaef/aws-codeguru-cli")
                                     .annotationType("Vulnerability".toUpperCase())
                                     .severity(convertSeverity(recommendation.severity()))
                                     .build();
    }

    private static String convertSeverity(Severity guruSeverity) {
        if (guruSeverity != null) {
            return guruSeverity.toString().toUpperCase(); // Bitbucket uses the same severity levels as CodeGuru.
        }
        return "Unknown";
    }

}
