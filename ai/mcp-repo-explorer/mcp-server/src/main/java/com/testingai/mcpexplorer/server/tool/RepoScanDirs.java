package com.testingai.mcpexplorer.server.tool;

import java.util.Set;

final class RepoScanDirs {
    static final Set<String> SKIP = Set.of(".git", "target", "node_modules", ".claude", "docs");

    private RepoScanDirs() {
    }
}
