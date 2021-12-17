package com.amazonaws.gurureviewercli.adapter;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;

import lombok.val;
import org.junit.jupiter.api.Test;

import com.amazonaws.gurureviewercli.model.ScanMetaData;
import com.amazonaws.gurureviewercli.util.JsonUtil;

class ResultsAdapterTest {

    @Test
    void saveResults() throws Exception {
        val recommendations =
            JsonUtil.loadRecommendations(Paths.get("test-data/recommendations/recommendations.json"));
        val scanMetaData = ScanMetaData.builder()
                                       .repositoryRoot(Paths.get("./").toAbsolutePath().normalize())
                                       .associationArn("123")
                                       .codeReviewArn("456")
                                       .sourceDirectories(Collections.emptyList())
                                       .build();
        val outDir = Files.createTempDirectory(Paths.get("./"), "test-output");
        ResultsAdapter.saveResults(outDir, recommendations, scanMetaData);
    }
}