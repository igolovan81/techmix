package com.testingai.sdlc.tool;

import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.Tool;

import java.util.List;
import java.util.Map;

public final class FixToolDefinitions {

    private FixToolDefinitions() {
    }

    public static List<Tool> all() {
        return List.of(readFile(), listFiles(), writeFile(), gitCommitBranch());
    }

    public static Tool readFile() {
        return Tool.builder().name("read_file")
                .description("Read the full content of a file in the sandbox repository, given a path relative "
                        + "to the repo root.")
                .inputSchema(Tool.InputSchema.builder()
                        .properties(Tool.InputSchema.Properties.builder()
                                .putAdditionalProperty("path", JsonValue.from(Map.of("type", "string",
                                        "description", "File path relative to the sandbox repo root")))
                                .build())
                        .required(List.of("path")).putAdditionalProperty("additionalProperties", JsonValue.from(false))
                        .build())
                .build();
    }

    public static Tool listFiles() {
        return Tool.builder().name("list_files")
                .description("List file names in a directory of the sandbox repository, given a path relative "
                        + "to the repo root.")
                .inputSchema(Tool.InputSchema.builder()
                        .properties(Tool.InputSchema.Properties.builder()
                                .putAdditionalProperty("dir", JsonValue.from(Map.of("type", "string", "description",
                                        "Directory path relative to the sandbox repo root")))
                                .build())
                        .required(List.of("dir")).putAdditionalProperty("additionalProperties", JsonValue.from(false))
                        .build())
                .build();
    }

    public static Tool writeFile() {
        return Tool.builder().name("write_file")
                .description("Overwrite (or create) a file in the sandbox repository with new content, given a "
                        + "path relative to the repo root.")
                .inputSchema(Tool.InputSchema.builder()
                        .properties(Tool.InputSchema.Properties.builder()
                                .putAdditionalProperty("path", JsonValue.from(Map.of("type", "string", "description",
                                        "File path relative to the sandbox repo root")))
                                .putAdditionalProperty("content", JsonValue.from(Map.of("type", "string",
                                        "description", "The complete new content of the file")))
                                .build())
                        .required(List.of("path", "content"))
                        .putAdditionalProperty("additionalProperties", JsonValue.from(false)).build())
                .build();
    }

    public static Tool gitCommitBranch() {
        return Tool.builder().name("git_commit_branch")
                .description("Create a new branch off the current commit and commit all pending changes to it. "
                        + "Call this exactly once, after all fixes have been written.")
                .inputSchema(Tool.InputSchema.builder()
                        .properties(Tool.InputSchema.Properties.builder()
                                .putAdditionalProperty("branchName", JsonValue.from(Map.of("type", "string",
                                        "description", "The branch name to create, e.g. hotfix/DEMO-101")))
                                .putAdditionalProperty("message", JsonValue.from(Map.of("type", "string",
                                        "description", "The commit message")))
                                .build())
                        .required(List.of("branchName", "message"))
                        .putAdditionalProperty("additionalProperties", JsonValue.from(false)).build())
                .build();
    }
}
