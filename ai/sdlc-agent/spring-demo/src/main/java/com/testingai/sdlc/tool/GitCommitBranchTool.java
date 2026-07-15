package com.testingai.sdlc.tool;

import com.testingai.sdlc.sandbox.SandboxRepo;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;

public final class GitCommitBranchTool {

    private static final PersonIdent AGENT_IDENT = new PersonIdent("SDLC Agent", "sdlc-agent@example.com");

    private GitCommitBranchTool() {
    }

    public static String commitBranch(SandboxRepo sandbox, String branchName, String message) {
        try {
            sandbox.git().checkout().setCreateBranch(true).setName(branchName).call();
            sandbox.git().add().addFilepattern(".").call();
            RevCommit commit = sandbox.git().commit().setMessage(message).setAuthor(AGENT_IDENT)
                    .setCommitter(AGENT_IDENT).call();
            return "{\"branch\": \"" + branchName.replace("\"", "'") + "\", \"commitSha\": \"" + commit.getName()
                    + "\"}";
        } catch (Exception e) {
            return "{\"error\": \"" + e.getMessage().replace("\"", "'") + "\"}";
        }
    }
}
