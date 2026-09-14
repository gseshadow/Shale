package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class CodexInstructionChainContractTest {
    private static final Path REPOSITORY_ROOT = Path.of("..");

    @Test
    void repositoryBootstrapRequiresTheAuthoritativeRulesBeforePlanningOrEditing() throws Exception {
        Path bootstrap = REPOSITORY_ROOT.resolve("AGENTS.md");
        assertTrue(Files.isRegularFile(bootstrap), "the repository root must provide a Codex bootstrap");

        String instructions = Files.readString(bootstrap);
        assertTrue(instructions.contains("Before planning or editing"));
        assertTrue(instructions.contains("read `architecture/codex-prompt-rules.md` completely"));
        assertTrue(instructions.contains("follow its routing instructions"));
    }

    @Test
    void authoritativeRulesRequireEndToEndTestImpactMaintenance() throws Exception {
        String rules = Files.readString(REPOSITORY_ROOT.resolve("architecture/codex-prompt-rules.md"));

        assertTrue(rules.contains("pre-edit test-impact search"));
        assertTrue(rules.contains("Existing obsolete tests must be updated"));
        assertTrue(rules.contains("Inspect neighboring tests for duplicated stale expectations"));
        assertTrue(rules.contains("run `python build/test-selection/select_tests.py"));
        assertTrue(rules.contains("directly modified tests first"));
        assertTrue(rules.contains("affected-area suite"));
        assertTrue(rules.contains("critical default with `mvn test`"));
        assertTrue(rules.contains("Relevance, not historical existence, determines routine test execution"));
        assertTrue(rules.contains("Never claim success for an unexecuted test"));
        assertTrue(rules.contains("Before completion: changed-file-to-test review"));
    }
}
