package com.amazonaws.gurureviewercli.util;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.NonNull;
import lombok.val;

import com.amazonaws.gurureviewercli.model.ScanMetaData;
import com.amazonaws.services.codegurureviewer.model.RecommendationSummary;


/**
 * Util class to load scan metadata
 */
public final class JsonUtil {

    private static final String META_DATA_FILE_NAME = ".codeguru-reviewer-cli";

    private static final ObjectMapper OBJECT_MAPPER =
        new ObjectMapper().configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
                          .enable(SerializationFeature.INDENT_OUTPUT)
                          .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
                          .setPropertyNamingStrategy(PropertyNamingStrategy.UPPER_CAMEL_CASE)
                          .setSerializationInclusion(JsonInclude.Include.NON_EMPTY);

    /**
     * The scan drops a {@link ScanMetaData} file with the ARN
     * of the scan that was started and the source and build dirs used in the scan so the user can fetch the
     * results without manually providing all this information.
     *
     * @param guruReview The {@link ScanMetaData} that will be serialized.
     * @param workingDir The directory where the metadata will be written.
     * @throws IOException If the directory does not exist or is not writable.
     */
    public static void storeMetaData(@NonNull final ScanMetaData guruReview,
                                     @NonNull final Path workingDir) throws IOException {
        OBJECT_MAPPER.writeValue(workingDir.resolve(META_DATA_FILE_NAME).toFile(), guruReview);
    }

    /**
     * The scan drops a {@link ScanMetaData} file with the ARN
     * of the scan that was started and the source and build dirs used in the scan so the user can fetch the
     * results without manually providing all this information.
     *
     * @param workingDir The directory where the {@link ScanMetaData} object was serialized
     * @return The {@link ScanMetaData} object of the most recent scan.
     * @throws IOException If no {@link ScanMetaData} is found.
     */
    public static ScanMetaData loadMetaData(@NonNull final Path workingDir) throws IOException {
        val tmpFile = workingDir.resolve(META_DATA_FILE_NAME);
        if (!tmpFile.toFile().isFile()) {
            return null;
        }
        return OBJECT_MAPPER.readValue(tmpFile.toFile(), ScanMetaData.class);
    }

    public static List<RecommendationSummary> loadRecommendations(@NonNull final Path jsonFile) throws IOException {

        return OBJECT_MAPPER.readValue(jsonFile.toFile(), new TypeReference<List<RecommendationSummary>>(){});
    }

    public static void storeRecommendations(@NonNull final List<RecommendationSummary> recommendations,
                                            @NonNull final Path targetFile) throws IOException {
        OBJECT_MAPPER.writeValue(targetFile.toFile(), recommendations);
    }

    private JsonUtil() {
        // do not initialize utility
    }
}
