package com.testingai.sdlc.tool;

import com.anthropic.core.JsonValue;
import com.fasterxml.jackson.core.type.TypeReference;
import com.testingai.sdlc.sandbox.SandboxRepo;

import java.util.Map;

public class FixToolExecutor {

    private final SandboxRepo sandbox;

    public FixToolExecutor(SandboxRepo sandbox) {
        this.sandbox = sandbox;
    }

    public String execute(String toolName, JsonValue input) {
        try {
            Map<String, Object> fields = input.convert(new TypeReference<Map<String, Object>>() {
            });
            if (fields == null) {
                return "{\"error\": \"Tool input must be a JSON object\"}";
            }
            return switch (toolName) {
                case "read_file" -> ReadFileTool.read(sandbox.root(), requireString(toolName, fields, "path"));
                case "list_files" -> ListFilesTool.list(sandbox.root(), requireString(toolName, fields, "dir"));
                case "write_file" -> WriteFileTool.write(sandbox.root(), requireString(toolName, fields, "path"),
                        requireString(toolName, fields, "content"));
                case "git_commit_branch" -> GitCommitBranchTool.commitBranch(sandbox,
                        requireString(toolName, fields, "branchName"), requireString(toolName, fields, "message"));
                default -> "{\"error\": \"Unknown tool: " + toolName + "\"}";
            };
        } catch (MissingFieldException e) {
            return "{\"error\": \"" + e.toolName + ": missing required field '" + e.field + "'\"}";
        } catch (Exception e) {
            return "{\"error\": \"FixToolExecutor error: " + e.getMessage().replace("\"", "'") + "\"}";
        }
    }

    private String requireString(String toolName, Map<String, Object> fields, String key) {
        Object value = fields.get(key);
        if (value == null) {
            throw new MissingFieldException(toolName, key);
        }
        return value.toString();
    }

    private static final class MissingFieldException extends RuntimeException {
        private final String toolName;
        private final String field;

        MissingFieldException(String toolName, String field) {
            this.toolName = toolName;
            this.field = field;
        }
    }
}
