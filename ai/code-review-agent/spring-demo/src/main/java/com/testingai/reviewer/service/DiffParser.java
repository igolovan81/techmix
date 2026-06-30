package com.testingai.reviewer.service;

import com.testingai.reviewer.model.ParsedDiff;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DiffParser {

    private static final Pattern HUNK_HEADER = Pattern.compile(
            "^@@ -\\d+(?:,\\d+)? \\+(\\d+)(?:,\\d+)? @@");

    public ParsedDiff parse(String diff) {
        Map<String, StringBuilder> contents = new HashMap<>();
        Map<String, Set<Integer>> changedLines = new HashMap<>();
        String currentFile = null;
        int currentLine = 0;
        int currentFileLineCount = 0;

        for (String line : diff.split("\n", -1)) {
            if (line.startsWith("+++ b/")) {
                String filename = line.substring(6);
                if (!filename.endsWith(".java")) {
                    currentFile = null;
                    continue;
                }
                currentFile = filename;
                contents.put(currentFile, new StringBuilder());
                changedLines.put(currentFile, new HashSet<>());
                currentLine = 0;
                currentFileLineCount = 0;
                continue;
            }
            if (line.startsWith("---") || line.startsWith("diff ") || line.startsWith("index ")) {
                continue;
            }
            if (currentFile == null) continue;

            Matcher hunkMatcher = HUNK_HEADER.matcher(line);
            if (hunkMatcher.find()) {
                int hunkStart = Integer.parseInt(hunkMatcher.group(1));
                StringBuilder buf = contents.get(currentFile);
                for (int i = currentFileLineCount; i < hunkStart - 1; i++) {
                    buf.append('\n');
                    currentFileLineCount++;
                }
                currentLine = hunkStart;
                continue;
            }

            if (line.startsWith("+") && !line.startsWith("+++")) {
                String content = line.substring(1);
                contents.get(currentFile).append(content).append('\n');
                changedLines.get(currentFile).add(currentLine);
                currentLine++;
                currentFileLineCount++;
            } else if (line.startsWith(" ")) {
                String content = line.substring(1);
                contents.get(currentFile).append(content).append('\n');
                currentLine++;
                currentFileLineCount++;
            }
            // lines starting with '-' are deleted — skip, don't advance currentLine
        }

        Map<String, String> fileContents = new HashMap<>();
        contents.forEach((file, sb) -> fileContents.put(file, sb.toString()));
        return new ParsedDiff(fileContents, changedLines);
    }
}
