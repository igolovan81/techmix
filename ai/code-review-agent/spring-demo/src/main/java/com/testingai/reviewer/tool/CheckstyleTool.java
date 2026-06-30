package com.testingai.reviewer.tool;

import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.Tool;
import com.puppycrawl.tools.checkstyle.Checker;
import com.puppycrawl.tools.checkstyle.ConfigurationLoader;
import com.puppycrawl.tools.checkstyle.PropertiesExpander;
import com.puppycrawl.tools.checkstyle.api.AuditEvent;
import com.puppycrawl.tools.checkstyle.api.AuditListener;
import com.puppycrawl.tools.checkstyle.api.CheckstyleException;
import com.puppycrawl.tools.checkstyle.api.Configuration;
import com.testingai.reviewer.model.RawFinding;
import org.springframework.stereotype.Component;
import org.xml.sax.InputSource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;

@Component
public class CheckstyleTool {

    public Tool definition() {
        return Tool.builder()
                .name("run_checkstyle")
                .description("Run Checkstyle static analysis on Java files extracted from the diff. Returns JSON array of findings on changed lines.")
                .inputSchema(Tool.InputSchema.builder()
                        .type(JsonValue.from("object"))
                        .putAdditionalProperty("properties", JsonValue.from(Map.of(
                                "diff", Map.of("type", "string",
                                        "description", "The unified diff of the pull request"))))
                        .putAdditionalProperty("required", JsonValue.from(List.of("diff")))
                        .build())
                .build();
    }

    public List<RawFinding> analyse(Path tempDir) {
        List<File> javaFiles = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(tempDir)) {
            paths.filter(p -> p.toString().endsWith(".java"))
                 .forEach(p -> javaFiles.add(p.toFile()));
        } catch (IOException e) {
            return List.of();
        }
        if (javaFiles.isEmpty()) {
            return List.of();
        }

        try (InputStream stream = getClass().getResourceAsStream("/checkstyle/checkstyle.xml")) {
            if (stream == null) {
                return List.of();
            }
            Configuration config = ConfigurationLoader.loadConfiguration(
                    new InputSource(stream),
                    new PropertiesExpander(new Properties()),
                    ConfigurationLoader.IgnoredModulesOptions.OMIT);

            List<AuditEvent> events = new ArrayList<>();
            Checker checker = new Checker();
            checker.setModuleClassLoader(Thread.currentThread().getContextClassLoader());
            checker.configure(config);
            checker.addListener(new AuditListener() {
                @Override public void auditStarted(AuditEvent e) {}
                @Override public void auditFinished(AuditEvent e) {}
                @Override public void fileStarted(AuditEvent e) {}
                @Override public void fileFinished(AuditEvent e) {}
                @Override public void addError(AuditEvent e) { events.add(e); }
                @Override public void addException(AuditEvent e, Throwable t) {}
            });
            try {
                checker.process(javaFiles);
            } finally {
                checker.destroy();
            }

            return events.stream()
                    .map(e -> new RawFinding(e.getFileName(), "checkstyle",
                            e.getSourceName(), e.getMessage(), e.getLine()))
                    .toList();
        } catch (CheckstyleException | IOException e) {
            return List.of();
        }
    }
}
