package com.amazonaws.gurureviewercli.util;

import java.nio.file.Path;
import java.nio.file.Paths;

import lombok.val;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.amazonaws.gurureviewercli.model.configfile.CustomConfiguration;

class RecommendationsFilterTest {

    private static final Path TEST_DIR = Paths.get("test-data");

    private static final Path CONFIG_DIR = TEST_DIR.resolve("custom-configs");

    private static final Path RECOMMENDATIONS_DIR = TEST_DIR.resolve("recommendations");

    @Test
    void test_filterRecommendations_byRecommendation() throws Exception {
        val configFile = CustomConfiguration.load(CONFIG_DIR.resolve("exclude-recommendations.yml"));
        val recommendations =
            JsonUtil.loadRecommendations(RECOMMENDATIONS_DIR.resolve("exclude01.json"));
        val output = RecommendationsFilter.filterRecommendations(recommendations, configFile);

        Assertions.assertEquals(1, output.size());
    }

    @Test
    void test_filterRecommendations_byTag() throws Exception {
        val configFile = CustomConfiguration.load(CONFIG_DIR.resolve("exclude-tag.yml"));
        val recommendations =
            JsonUtil.loadRecommendations(RECOMMENDATIONS_DIR.resolve("exclude01.json"));
        val output = RecommendationsFilter.filterRecommendations(recommendations, configFile);

        Assertions.assertEquals(3, output.size());
    }

    @Test
    void test_filterRecommendations_bySeverity() throws Exception {
        val configFile = CustomConfiguration.load(CONFIG_DIR.resolve("exclude-severity.yml"));
        val recommendations =
            JsonUtil.loadRecommendations(RECOMMENDATIONS_DIR.resolve("exclude01.json"));
        val output = RecommendationsFilter.filterRecommendations(recommendations, configFile);

        Assertions.assertEquals(2, output.size());
    }

    @Test
    void test_filterRecommendations_byId() throws Exception {
        val configFile = CustomConfiguration.load(CONFIG_DIR.resolve("exclude-id.yml"));
        val recommendations =
            JsonUtil.loadRecommendations(RECOMMENDATIONS_DIR.resolve("exclude01.json"));
        val output = RecommendationsFilter.filterRecommendations(recommendations, configFile);

        Assertions.assertEquals(0, output.size());
    }

}