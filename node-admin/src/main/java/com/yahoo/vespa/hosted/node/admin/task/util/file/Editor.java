// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.file;

import com.yahoo.vespa.hosted.node.admin.component.TaskContext;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Logger;

import static com.yahoo.vespa.hosted.node.admin.task.util.file.IOExceptionUtil.ifExists;
import static com.yahoo.yolean.Exceptions.uncheck;

/**
 * An editor meant to edit small line-based files like /etc/fstab.
 *
 * @author hakonhall
 */
public class Editor {
    private static final Logger logger = Logger.getLogger(Editor.class.getName());
    private static final Charset ENCODING = StandardCharsets.UTF_8;

    private static final int MAX_LENGTH = 500;

    private final Supplier<List<String>> supplier;
    private final Consumer<List<String>> consumer;
    private final String name;
    private final LineEditor editor;
    private int diffSize = 0;

    /**
     * Read the file which must be encoded in UTF-8, use the LineEditor to edit it,
     * and any modifications were done write it back and return true.
     */
    public Editor(Path path, LineEditor editor) {
        this(path.toString(),
                () -> ifExists(() -> Files.readAllLines(path, ENCODING)).orElseGet(List::of),
                (newLines) -> uncheck(() -> Files.write(path, newLines, ENCODING)),
                editor);
    }

    /**
     * @param name     The name of what is being edited - used in logging
     * @param supplier Supplies the editor with a list of lines to edit
     * @param consumer Consumes the lines to presist if any changes is detected
     * @param editor   The line operations to execute on the lines supplied
     */
    public Editor(String name,
                  Supplier<List<String>> supplier,
                  Consumer<List<String>> consumer,
                  LineEditor editor) {
        this.supplier = supplier;
        this.consumer = consumer;
        this.name = name;
        this.editor = editor;
    }

    public boolean edit(Consumer<String> logConsumer) {
        List<String> lines = supplier.get();
        List<String> newLines = new LinkedList<>();
        StringBuilder diff = new StringBuilder();
        boolean modified = false;

        for (String line : lines) {
            LineEdit edit = editor.edit(line);
            if (!edit.prependLines().isEmpty()) {
                modified = true;
                maybeAdd(diff, edit.prependLines());
                newLines.addAll(edit.prependLines());
            }

            switch (edit.getType()) {
                case REPLACE -> {
                    modified = true;
                    maybeRemove(diff, line);
                }
                case NONE -> newLines.add(line);
                default -> throw new IllegalArgumentException("Unknown EditType " + edit.getType());
            }

            if (!edit.appendLines().isEmpty()) {
                modified = true;
                maybeAdd(diff, edit.appendLines());
                newLines.addAll(edit.appendLines());
            }
        }

        List<String> linesToAppend = editor.onComplete();
        if (!linesToAppend.isEmpty()) {
            modified = true;
            newLines.addAll(linesToAppend);
            maybeAdd(diff, linesToAppend);
        }

        if (!modified) {
            return false;
        }

        String diffDescription = diffTooLarge() ? ": Diff too large (" + diffSize + ")" : ":\n" + diff;
        logConsumer.accept("Patching file " + name + diffDescription);
        consumer.accept(newLines);
        return true;
    }

    public boolean converge(TaskContext context) {
        return this.edit(line -> context.recordSystemModification(logger, line));
    }

    private void maybeAdd(StringBuilder diff, List<String> lines) {
        for (String line : lines) {
            // 2 for '+' and '\n'
            diffSize += 2 + line.length();
            if (!diffTooLarge()) {
                diff.append('+').append(line).append('\n');
            }
        }
    }

    private void maybeRemove(StringBuilder diff, String line) {
        // 2 for '-' and '\n'
        diffSize += 2 + line.length();
        if (!diffTooLarge()) {
            diff.append('-').append(line).append('\n');
        }
    }

    private boolean diffTooLarge() {
        return diffSize > MAX_LENGTH;
    }
}
