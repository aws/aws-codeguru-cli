package com.amazonaws.gurureviewercli.model.configfile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;

import com.amazonaws.gurureviewercli.util.Log;

/**
 * Guru Configuration file. Customers can leave such a file in their repository to customize the behavior of
 * CodeGuru. E.g., to suppress recommendations in parts of the repository.
 */
@Log4j2
@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CustomConfiguration {

    private static final ObjectMapper YAML_MAPPER =
        YAMLMapper.builder()
                  .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
                  .build();

    public static CustomConfiguration load(final Path file) throws IOException {
        try {
            return YAML_MAPPER.readValue(file.toFile(), CustomConfiguration.class);
        } catch (JsonParseException | JsonMappingException e) {
            Log.error("Failed to parse " + file);
            Log.error(e);
        }
        return null;
    }

    private static final String VERSION = "1.0";

    @Builder.Default
    private String version = VERSION;

    @Builder.Default
    private List<String> excludeFiles = new ArrayList<>();

    private String excludeBelowSeverity;

    @Builder.Default
    private List<String> excludeTags = new ArrayList<>();

    @Builder.Default
    private List<String> excludeById = new ArrayList<>();

    @Builder.Default
    private List<ExcludeRecommendation> excludeRecommendations = new ArrayList<>();

}
