package com.testingai.reviewer.tool;

import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.Tool;
import com.testingai.reviewer.model.RawFinding;
import net.sourceforge.pmd.PMDConfiguration;
import net.sourceforge.pmd.PmdAnalysis;
import net.sourceforge.pmd.lang.LanguageRegistry;
import net.sourceforge.pmd.lang.rule.RuleSet;
import net.sourceforge.pmd.lang.rule.RuleSetLoader;
import net.sourceforge.pmd.reporting.Report;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Component
public class PmdTool {

    private static final Logger log = LoggerFactory.getLogger(PmdTool.class);

    public Tool definition() {
        return Tool.builder()
                .name("run_pmd")
                .description("Run PMD static analysis on the changed Java files and return findings.")
                .inputSchema(Tool.InputSchema.builder()
                        .type(JsonValue.from("object"))
                        .putAdditionalProperty("properties", JsonValue.from(Map.of(
                                "diff", Map.of("type", "string", "description", "The unified diff of the pull request"))))
                        .putAdditionalProperty("required", JsonValue.from(List.of("diff")))
                        .build())
                .build();
    }

    public List<RawFinding> analyse(Path tempDir) {
        try {
            PMDConfiguration config = new PMDConfiguration();
            config.setDefaultLanguageVersion(
                    LanguageRegistry.PMD.getLanguageById("java").getDefaultVersion());
            config.addInputPath(tempDir);
            config.setIgnoreIncrementalAnalysis(true);

            RuleSetLoader loader = new RuleSetLoader();
            RuleSet ruleSet = loader.loadFromResource("pmd/pmd-ruleset.xml");

            try (PmdAnalysis pmd = PmdAnalysis.create(config)) {
                pmd.addRuleSet(ruleSet);
                Report report = pmd.performAnalysisAndCollectReport();
                return report.getViolations().stream()
                        .map(v -> new RawFinding(
                                v.getFileId().getAbsolutePath(),
                                "pmd",
                                v.getRule().getName(),
                                v.getDescription(),
                                v.getBeginLine()))
                        .toList();
            }
        } catch (Exception e) {
            log.warn("PMD analysis failed", e);
            return List.of();
        }
    }
}
