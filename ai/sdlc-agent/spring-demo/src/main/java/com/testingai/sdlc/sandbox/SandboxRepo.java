package com.testingai.sdlc.sandbox;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;

public class SandboxRepo {

    private static final PersonIdent AGENT_IDENT = new PersonIdent("SDLC Agent", "sdlc-agent@example.com");
    private static final List<String> TEMPLATE_FILES = List.of(
            "src/main/java/com/example/checkout/DiscountService.java",
            "src/main/java/com/example/checkout/CheckoutController.java");

    private final Path root;
    private final Git git;
    private final ObjectId initialCommitId;

    private SandboxRepo(Path root, Git git, ObjectId initialCommitId) {
        this.root = root;
        this.git = git;
        this.initialCommitId = initialCommitId;
    }

    public static SandboxRepo create() {
        try {
            Path root = Files.createTempDirectory("sdlc-sandbox-");
            copyTemplateFiles(root);
            Git git = Git.init().setDirectory(root.toFile()).setInitialBranch("main").call();
            git.add().addFilepattern(".").call();
            RevCommit commit = git.commit().setMessage("Initial commit").setAuthor(AGENT_IDENT)
                    .setCommitter(AGENT_IDENT).call();
            return new SandboxRepo(root, git, commit.getId());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create sandbox repo", e);
        }
    }

    private static void copyTemplateFiles(Path root) throws IOException {
        for (String relativePath : TEMPLATE_FILES) {
            Path target = root.resolve(relativePath);
            Files.createDirectories(target.getParent());
            try (InputStream in = SandboxRepo.class.getClassLoader()
                    .getResourceAsStream("sandbox-repo-template/" + relativePath)) {
                if (in == null) {
                    throw new IOException("Template resource not found: " + relativePath);
                }
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    public Path root() {
        return root;
    }

    public Git git() {
        return git;
    }

    public String diffAgainstInitialCommit() {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
                DiffFormatter formatter = new DiffFormatter(out)) {
            formatter.setRepository(git.getRepository());
            ObjectId head = git.getRepository().resolve("HEAD");
            formatter.format(initialCommitId, head);
            return out.toString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public String currentBranch() {
        try {
            return git.getRepository().getBranch();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public String currentCommitSha() {
        try {
            ObjectId head = git.getRepository().resolve("HEAD");
            return head != null ? head.getName() : null;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public boolean hasCommitted() {
        return !initialCommitId.getName().equals(currentCommitSha());
    }

    public void cleanup() {
        git.close();
        deleteRecursively(root);
    }

    private static void deleteRecursively(Path path) {
        try (var walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException ignored) {
                    // best-effort cleanup
                }
            });
        } catch (IOException ignored) {
            // best-effort cleanup
        }
    }
}
