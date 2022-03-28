package com.amazonaws.gurureviewercli.adapter;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
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
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import software.amazon.awssdk.services.codegurureviewer.model.RecommendationSummary;

import com.amazonaws.gurureviewercli.model.ScanMetaData;
import com.amazonaws.gurureviewercli.util.JsonUtil;
import com.amazonaws.gurureviewercli.util.Log;

/**
 * Util to save Guru recommendations to disk and convert them to HTML.
 */
public final class ResultsAdapter {

    public static void saveResults(final Path outputDir,
                                   final List<RecommendationSummary> results,
                                   final ScanMetaData scanMetaData) throws IOException {
        val jsonFile = outputDir.resolve("recommendations.json");
        JsonUtil.storeRecommendations(results, jsonFile);
        Log.info("Recommendations in Json format written to to:%n%s", jsonFile.normalize().toUri());
        val sarifFile = outputDir.resolve("recommendations.sarif.json");
        JsonUtil.writeSarif(createSarifReport(results), sarifFile);
        Log.info("Recommendations in SARIF format written to to:%n%s", sarifFile.normalize().toUri());

        createHtmlReport(outputDir, scanMetaData, results);
    }

    private static void createHtmlReport(final Path outputDir,
                                         final ScanMetaData scanMetaData,
                                         final List<RecommendationSummary> recommendations) throws IOException {

        int validFindings = 0;
        // sort by file name and line number
        sortByFileName(recommendations);

        Parser parser = Parser.builder().build();
        HtmlRenderer renderer = HtmlRenderer.builder().build();

        val htmlFile = outputDir.resolve("codeguru-report.html");
        try (OutputStreamWriter writer =
                 new OutputStreamWriter(new FileOutputStream(htmlFile.toFile()), StandardCharsets.UTF_8)) {

            writer.write("<!DOCTYPE html>\n<html lang=\"en\">\n");
            writer.write("<body>\n");
            writer.write("<h2>CodeGuru Reviewer Recommendations</h2>\n");
            val awsUrlPrfix = "https://console.aws.amazon.com/codeguru/reviewer";
            val associationUrl = String.format("%s?region=%s#/ciworkflows/associationdetails/%s",
                                               awsUrlPrfix, scanMetaData.getRegion(), scanMetaData.getAssociationArn());
            val scanUrl = String.format("%s?region=%s#/codereviews/details/%s",
                                        awsUrlPrfix, scanMetaData.getRegion(), scanMetaData.getCodeReviewArn());

            writer.write(renderer.render(parser.parse(String.format("**CodeGuru Repository ARN**: [%s](%s)%n",
                                                                    scanMetaData.getAssociationArn(),
                                                                    associationUrl))));
            writer.write(renderer.render(parser.parse(String.format("**CodeGuru Scan ARN**: [%s](%s)%n",
                                                                    scanMetaData.getCodeReviewArn(),
                                                                    scanUrl))));
            writer.write("\n<br/><hr style=\"width:90%\"><br/>\n");

            for (val recommendation : recommendations) {
                val filePath = scanMetaData.getRepositoryRoot().resolve(recommendation.filePath()).toAbsolutePath();
                if (filePath == null || !filePath.toFile().isFile()) {
                    if (filePath != null && !(filePath.endsWith(".") || filePath.endsWith("/"))) {
                        Log.warn("Dropping finding because file not found on disk: %s", filePath);
                    }
                    continue;
                }
                validFindings++;
                String lineMsg;
                if (!recommendation.startLine().equals(recommendation.endLine())
                    && recommendation.endLine() != null) {
                    lineMsg = String.format("### In: [%s](%s) L%d %n",
                                            filePath, filePath.toUri(),
                                            recommendation.startLine());
                } else {
                    lineMsg = String.format("### In: [%s](%s) L%d - L%d %n",
                                            filePath, filePath.toUri(),
                                            recommendation.startLine(),
                                            recommendation.endLine());
                }

                Node document = parser.parse(String.format("### In: [%s](%s) L%d %n",
                                                           filePath, filePath.toUri(),
                                                           recommendation.startLine()));
                writer.write(renderer.render(document));

                document = parser.parse("**Issue:** " + recommendation.description());
                writer.write(renderer.render(document));

                writer.write(String.format("<p><strong>Severity:</strong> %s<p/>", recommendation.severity()));

                if (recommendation.ruleMetadata() != null && recommendation.ruleMetadata().ruleId() != null) {
                    val manifest = recommendation.ruleMetadata();
                    writer.write(String.format("<p><strong>Rule ID:</strong> %s<p/>", manifest.ruleId()));
                    writer.write(String.format("<p><strong>Rule Name:</strong> %s<p/>", manifest.ruleName()));
                    document = parser.parse("**Description:** " + manifest.longDescription());
                    writer.write(renderer.render(document));
                    if (manifest.ruleTags() != null && !manifest.ruleTags().isEmpty()) {
                        val mdList = manifest.ruleTags().stream()
                                             .map(s -> String.format("- %s%n", s))
                                             .collect(Collectors.joining());
                        document = parser.parse("**Tags:**\n" + mdList);
                        writer.write(renderer.render(document));
                    }
                }
                writer.write("\n<hr style=\"width:80%\">\n");
            }
            writer.write("</body>\n");
            writer.write("</html>\n");
        }
        Log.info("Report with %d recommendations written to:%n%s", validFindings, htmlFile.normalize().toUri());
    }

    private static SarifSchema210 createSarifReport(final List<RecommendationSummary> recommendations)
        throws IOException {
        val docUrl = "https://docs.aws.amazon.com/codeguru/latest/reviewer-ug/how-codeguru-reviewer-works.html";

        val rulesMap = createSarifRuleDescriptions(recommendations);
        val driver = new ToolComponent().withName("CodeGuru Reviewer Scanner")
                                        .withInformationUri(URI.create(docUrl))
                                        .withRules(new HashSet<>(rulesMap.values()));

        val results = recommendations.stream().map(ResultsAdapter::convertToSarif)
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
                return Result.Level.NONE.value();
            case LOW:
                return Result.Level.NONE.value();
            case MEDIUM:
                return Result.Level.NONE.value();
            case HIGH:
                return Result.Level.WARNING.value();
            case CRITICAL:
                return Result.Level.ERROR.value();
            default:
                return Result.Level.NONE.value();
        }
    }

    private static void sortByFileName(final List<RecommendationSummary> recommendations) {
        Collections.sort(recommendations, (o1, o2) -> {
            int pathComp = o1.filePath().compareTo(o2.filePath());
            if (pathComp == 0) {
                return o1.startLine().compareTo(o2.startLine());
            }
            return pathComp;
        });
    }

    private ResultsAdapter() {
        // do not instantiate
    }
}
