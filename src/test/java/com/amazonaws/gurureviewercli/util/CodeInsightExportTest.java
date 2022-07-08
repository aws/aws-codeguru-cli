package com.amazonaws.gurureviewercli.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import lombok.val;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.amazonaws.gurureviewercli.model.ScanMetaData;
import com.amazonaws.gurureviewercli.model.bitbucket.CodeInsightsAnnotation;

class CodeInsightExportTest {
    private static final Path TEST_DIR = Paths.get("test-data");

    private static final Path RECOMMENDATIONS_DIR = TEST_DIR.resolve("recommendations");

    private static final JsonMapper JSON_MAPPER =
        JsonMapper.builder()
                  .serializationInclusion(JsonInclude.Include.NON_ABSENT)
                  .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                  .disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
                  .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
                  .enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE)
                  .build();

    @Test
    void test_happyCase() throws Exception {
        val recommendations =
            JsonUtil.loadRecommendations(RECOMMENDATIONS_DIR.resolve("exclude01.json"));

        val outDir = Files.createTempDirectory("test-output");
        val scanMetaData = ScanMetaData.builder()
                                       .associationArn("asdf")
                                       .region("1234")
                                       .build();
        CodeInsightExport.report(recommendations, scanMetaData, outDir);

        Assertions.assertTrue(outDir.resolve("report.json").toFile().isFile());
        val annotations = JSON_MAPPER.readValue(outDir.resolve("annotations.json").toFile(),
                              new TypeReference<List<CodeInsightsAnnotation>>() {});
        Assertions.assertEquals(recommendations.size(), annotations.size());
    }
}