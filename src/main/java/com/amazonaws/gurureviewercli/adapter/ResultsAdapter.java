package com.amazonaws.gurureviewercli.adapter;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import lombok.val;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import com.amazonaws.gurureviewercli.model.ScanMetaData;
import com.amazonaws.gurureviewercli.util.JsonUtil;
import com.amazonaws.gurureviewercli.util.Log;
import com.amazonaws.services.codegurureviewer.model.RecommendationSummary;

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
                val filePath = scanMetaData.getRepositoryRoot().resolve(recommendation.getFilePath()).toAbsolutePath();
                if (filePath == null || !filePath.toFile().isFile()) {
                    if (filePath != null && !(filePath.endsWith(".") || filePath.endsWith("/"))) {
                        Log.warn("Dropping finding because file not found on disk: %s", filePath);
                    }
                    continue;
                }
                validFindings++;
                String lineMsg;
                if (!recommendation.getStartLine().equals(recommendation.getEndLine())
                    && recommendation.getEndLine() != null) {
                    lineMsg = String.format("### In: [%s](%s) L%d %n",
                                            filePath, filePath.toUri(),
                                            recommendation.getStartLine());
                } else {
                    lineMsg = String.format("### In: [%s](%s) L%d - L%d %n",
                                            filePath, filePath.toUri(),
                                            recommendation.getStartLine(),
                                            recommendation.getEndLine());
                }

                Node document = parser.parse(String.format("### In: [%s](%s) L%d %n",
                                                           filePath, filePath.toUri(),
                                                           recommendation.getStartLine()));
                writer.write(renderer.render(document));

                document = parser.parse("**Issue:** " + recommendation.getDescription());
                writer.write(renderer.render(document));

                writer.write(String.format("<p><strong>Severity:</strong> %s<p/>", recommendation.getSeverity()));

                if (recommendation.getRuleMetadata() != null && recommendation.getRuleMetadata().getRuleId() != null) {
                    val manifest = recommendation.getRuleMetadata();
                    writer.write(String.format("<p><strong>Rule ID:</strong> %s<p/>", manifest.getRuleId()));
                    writer.write(String.format("<p><strong>Rule Name:</strong> %s<p/>", manifest.getRuleName()));
                    document = parser.parse("**Description:** " + manifest.getLongDescription());
                    writer.write(renderer.render(document));
                    if (manifest.getRuleTags() != null && !manifest.getRuleTags().isEmpty()) {
                        val mdList = manifest.getRuleTags().stream()
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

    private static void sortByFileName(final List<RecommendationSummary> recommendations) {
        Collections.sort(recommendations, (o1, o2) -> {
            int pathComp = o1.getFilePath().compareTo(o2.getFilePath());
            if (pathComp == 0) {
                return o1.getStartLine().compareTo(o2.getStartLine());
            }
            return pathComp;
        });
    }

    private ResultsAdapter() {
        // do not instantiate
    }
}
