package com.amazonaws.gurureviewercli.util;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.contrastsecurity.sarif.ArtifactLocation;
import com.contrastsecurity.sarif.Location;
import com.contrastsecurity.sarif.Message;
import com.contrastsecurity.sarif.MultiformatMessageString;
import com.contrastsecurity.sarif.PhysicalLocation;
import com.contrastsecurity.sarif.PropertyBag;
import com.contrastsecurity.sarif.Region;
import com.contrastsecurity.sarif.ReportingConfiguration;
import com.contrastsecurity.sarif.ReportingDescriptor;
import com.contrastsecurity.sarif.Result;
import com.contrastsecurity.sarif.Run;
import com.contrastsecurity.sarif.SarifSchema210;
import com.contrastsecurity.sarif.Tool;
import com.contrastsecurity.sarif.ToolComponent;
import lombok.val;
import software.amazon.awssdk.services.codegurureviewer.model.RecommendationSummary;

/**
 * Utility class to convert CodeGuru Recommendations to SARIF
 */
public final class SarifConverter {

    private SarifConverter() {
        // do not instantiate
    }


    /**
     * Convert CodeGuru Reviewer recommendations into SARIF format.
     *
     * @param recommendations CodeGuru Reviewer recommendations.
     * @return Sarif report object.
     * @throws IOException If conversion fails.
     */
    public static SarifSchema210 createSarifReport(final List<RecommendationSummary> recommendations)
        throws IOException {
        val docUrl = "https://docs.aws.amazon.com/codeguru/latest/reviewer-ug/how-codeguru-reviewer-works.html";

        val rulesMap = createSarifRuleDescriptions(recommendations);
        val driver = new ToolComponent().withName("CodeGuru Reviewer Scanner")
                                        .withInformationUri(URI.create(docUrl))
                                        .withRules(new HashSet<>(rulesMap.values()));

        val results = recommendations.stream().map(SarifConverter::convertToSarif)
                                     .collect(Collectors.toList());

        val run = new Run().withTool(new Tool().withDriver(driver)).withResults(results);

        return new SarifSchema210()
            .withVersion(SarifSchema210.Version._2_1_0)
            .with$schema(URI.create("http://json.schemastore.org/sarif-2.1.0-rtm.4"))
            .withRuns(Arrays.asList(run));

    }

    private static Map<String, ReportingDescriptor> createSarifRuleDescriptions(
        final List<RecommendationSummary> recommendations) {
        val rulesMap = new HashMap<String, ReportingDescriptor>();
        for (val recommendation : recommendations) {
            val metaData = recommendation.ruleMetadata();
            if (metaData != null && !rulesMap.containsKey(metaData.ruleId())) {
                val properties = new PropertyBag().withTags(new HashSet<>(metaData.ruleTags()));
                MultiformatMessageString foo;
                val descriptor = new ReportingDescriptor()
                    .withName(metaData.ruleName())
                    .withId(metaData.ruleId())
                    .withShortDescription(new MultiformatMessageString().withText(metaData.ruleName()))
                    .withFullDescription(new MultiformatMessageString().withText(metaData.shortDescription()))
                    .withHelp(new MultiformatMessageString().withText(metaData.longDescription()))
                    .withProperties(properties);
                if (recommendation.severityAsString() != null) {
                    val level = ReportingConfiguration.Level.fromValue(getSarifSeverity(recommendation));
                    descriptor.setDefaultConfiguration(new ReportingConfiguration().withLevel(level));
                }
                rulesMap.put(metaData.ruleId(), descriptor);
            }
        }
        return rulesMap;
    }

    private static Result convertToSarif(final RecommendationSummary recommendation) {
        List<Location> locations = Arrays.asList(getSarifLocation(recommendation));
        return new Result().withRuleId(recommendation.ruleMetadata().ruleId())
                           .withLevel(Result.Level.fromValue(getSarifSeverity(recommendation)))
                           .withMessage(new Message().withMarkdown(recommendation.description()))
                           .withLocations(locations);
    }

    private static Location getSarifLocation(final RecommendationSummary recommendation) {
        val loc = new PhysicalLocation()
            .withArtifactLocation(new ArtifactLocation().withUri(recommendation.filePath()))
            .withRegion(new Region().withStartLine(recommendation.startLine())
                                    .withEndLine(recommendation.endLine()));
        return new Location()
            .withPhysicalLocation(loc);
    }

    private static String getSarifSeverity(RecommendationSummary recommendation) {
        if (recommendation.severity() == null) {
            return Result.Level.NONE.value(); // can happen for legacy rules
        }
        switch (recommendation.severity()) {
            case INFO:
            case LOW:
                return Result.Level.NOTE.value();
            case MEDIUM:
            case HIGH:
                return Result.Level.WARNING.value();
            case CRITICAL:
                return Result.Level.ERROR.value();
            default:
                return Result.Level.NONE.value();
        }
    }
}
