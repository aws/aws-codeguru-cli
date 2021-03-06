package com.amazonaws.gurureviewercli.util;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import com.contrastsecurity.sarif.SarifSchema210;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import lombok.NonNull;
import software.amazon.awssdk.services.codegurureviewer.model.RecommendationSummary;

import com.amazonaws.gurureviewercli.model.Recommendation;

/**
 * Util class to load scan metadata
 */
public final class JsonUtil {

    private static final ObjectMapper OBJECT_MAPPER =
        JsonMapper.builder()
                  .enable(SerializationFeature.INDENT_OUTPUT)
                  .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
                  .visibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
                  .serializationInclusion(JsonInclude.Include.NON_EMPTY)
                  .build();

    public static List<RecommendationSummary> loadRecommendations(@NonNull final Path jsonFile) throws IOException {
        return OBJECT_MAPPER.readValue(jsonFile.toFile(), new TypeReference<List<Recommendation>>() {
                            })
                            .stream().map(Recommendation::toRecommendationSummary).collect(Collectors.toList());
    }

    public static void storeRecommendations(@NonNull final List<RecommendationSummary> recommendations,
                                            @NonNull final Path targetFile) throws IOException {
        OBJECT_MAPPER.writeValue(targetFile.toFile(), recommendations);
    }

    public static void writeSarif(@NonNull final SarifSchema210 sarif, @NonNull final Path targetFile)
        throws IOException {
        OBJECT_MAPPER.writeValue(targetFile.toFile(), sarif);
    }

    private JsonUtil() {
        // do not initialize utility
    }
}
