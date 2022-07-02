package com.amazonaws.gurureviewercli.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.common.collect.Lists;
import lombok.val;
import org.apache.http.HttpHost;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import software.amazon.awssdk.services.codegurureviewer.model.RecommendationSummary;
import software.amazon.awssdk.services.codegurureviewer.model.Severity;

import com.amazonaws.gurureviewercli.model.ScanMetaData;
import com.amazonaws.gurureviewercli.model.bitbucket.CodeInsightsAnnotation;
import com.amazonaws.gurureviewercli.model.bitbucket.CodeInsightsReport;

public final class CodeInsightUpload {
    private static final int ANNOTATIONS_BATCH_SIZE = 50;
    private static final long ANNOTATIONS_MAX_SIZE = 1000;
    private static final String ENV_COMMIT = "BITBUCKET_COMMIT";
    private static final String ENV_REPO_FULL_NAME = "BITBUCKET_REPO_FULL_NAME";

    private static final JsonMapper JSON_MAPPER =
        JsonMapper.builder()
                  .serializationInclusion(JsonInclude.Include.NON_ABSENT)
                  .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                  .disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
                  .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
                  .enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE)
                  .build();

    private static final String BITBUCKET_API_PATTERN =
        "http://api.bitbucket.org/2.0/repositories/%s/commit/%s/reports/%s";

    public static void report(final Collection<RecommendationSummary> recommendations,
                              final ScanMetaData scanMetaData) {
        val report = CodeInsightsReport.builder()
                                       .reporter("CodeGuru Reviewer CLI")
                                       .details("CodeGuru Reviewer " + scanMetaData.getCodeReviewArn())
                                       .result(recommendations.isEmpty() ? "Passed" : "Failed")
                                       .data(new ArrayList<>())
                                       .build();
        val annotations = recommendations.stream().map(CodeInsightUpload::convert).collect(Collectors.toList());
        try {
            uploadBitbucketCodeInsights(report, annotations, "CodeGuruReviewer-01");
        } catch (Exception e) {
            Log.error("Failed to upload to Bitbucket %s", e);
            Log.error(e);
        }
    }

    private static void uploadBitbucketCodeInsights(final CodeInsightsReport report,
                                                    final List<CodeInsightsAnnotation> annotations,
                                                    final String reportId) throws IOException {
        final String commit = System.getenv(ENV_COMMIT);
        final String repoName = System.getenv(ENV_REPO_FULL_NAME);

        val endpoint = String.format(BITBUCKET_API_PATTERN, repoName, commit, reportId);
        Log.info("Using Bitbucket endpoint %s.", endpoint);
        try {
            HttpPut reportPut = new HttpPut(endpoint);
            reportPut.setEntity(new StringEntity(JSON_MAPPER.writeValueAsString(report),
                                                 ContentType.APPLICATION_JSON));
            try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
                Log.info("Uploading report summary.");
                val response = httpclient.execute(new HttpHost("api.bitbucket.org"), reportPut);
                Log.info(response.toString());
                uploadAnnotation(endpoint, annotations, httpclient);
            }
        } catch (Exception e) {
            throw new IOException("Failed to upload to bitbucket.", e);
        }
    }

    private static void uploadAnnotation(final String endpoint,
                                         final List<CodeInsightsAnnotation> annotations,
                                         CloseableHttpClient httpClient) {

        if (!annotations.isEmpty()) {
            if (annotations.size() > ANNOTATIONS_MAX_SIZE) {
                Log.warn("Too many annotations. %d/%d will be uploaded.",
                         ANNOTATIONS_MAX_SIZE, annotations.size());
            }
            Lists.partition(annotations, ANNOTATIONS_BATCH_SIZE).forEach(batch -> {
                try {
                    HttpPost reportPost = new HttpPost(endpoint + "/annotations");
                    reportPost.setEntity(new StringEntity(JSON_MAPPER.writeValueAsString(batch),
                                                          ContentType.APPLICATION_JSON));
                    Log.info("Uploading annotations.");
                    val response = httpClient.execute(new HttpHost("api.bitbucket.org"), reportPost);
                    Log.info(response.toString());
                } catch (IOException e) {
                    throw new RuntimeException("Failed to serialize batch", e);
                }
            });
        }
    }

    private static CodeInsightsAnnotation convert(final RecommendationSummary recommendation) {
        String description = recommendation.recommendationCategoryAsString();
        if (recommendation.ruleMetadata() != null) {
            description = recommendation.ruleMetadata().shortDescription();
        }

        return CodeInsightsAnnotation.builder()
                                     .externalId(recommendation.recommendationId())
                                     .path(recommendation.filePath())
                                     .line(recommendation.startLine())
                                     .summary(description)
                                     .annotationType("Vulnerability")
                                     .details(recommendation.description())
                                     .severity(convertSeverity(recommendation.severity()))
                                     .build();
    }

    private static String convertSeverity(Severity guruSeverity) {
        if (guruSeverity != null) {
            return guruSeverity.toString(); // Bitbucket uses the same severity levels as CodeGuru.
        }
        return "Unknown";
    }
}
