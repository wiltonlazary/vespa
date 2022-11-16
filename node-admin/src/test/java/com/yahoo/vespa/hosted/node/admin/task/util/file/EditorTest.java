// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.file;

import com.yahoo.vespa.hosted.node.admin.component.TaskContext;
import com.yahoo.vespa.test.file.TestFileSystem;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.file.FileSystem;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class EditorTest {
    private final FileSystem fileSystem = TestFileSystem.create();
    private final UnixPath path = new UnixPath(fileSystem.getPath("/file"));

    @Test
    void testEdit() {
        path.writeUtf8File(joinLines("first", "second", "third"));

        LineEditor lineEditor = mock(LineEditor.class);
        when(lineEditor.edit(any())).thenReturn(
                LineEdit.none(), // don't edit the first line
                LineEdit.remove(), // remove the second
                LineEdit.replaceWith("replacement")); // replace the third

        Editor editor = new Editor(path.toPath(), lineEditor);
        TaskContext context = mock(TaskContext.class);

        assertTrue(editor.converge(context));

        verify(lineEditor, times(3)).edit(any());

        // Verify the system modification message
        ArgumentCaptor<String> modificationMessage = ArgumentCaptor.forClass(String.class);
        verify(context).recordSystemModification(any(), modificationMessage.capture());
        assertEquals(
                "Patching file /file:\n-second\n-third\n+replacement\n",
                modificationMessage.getValue());

        // Verify the new contents of the file:
        assertEquals(joinLines("first", "replacement"), path.readUtf8File());
    }

    @Test
    void testInsert() {
        path.writeUtf8File(joinLines("second", "eight", "fifth", "seventh"));

        LineEditor lineEditor = mock(LineEditor.class);
        when(lineEditor.edit(any())).thenReturn(
                LineEdit.insertBefore("first"), // insert first, and keep the second line
                LineEdit.replaceWith("third", "fourth"), // remove eight, and replace with third and fourth instead
                LineEdit.none(), // Keep fifth
                LineEdit.insert(List.of("sixth"), // insert sixth before seventh
                        List.of("eight"))); // add eight after seventh

        Editor editor = new Editor(path.toPath(), lineEditor);
        TaskContext context = mock(TaskContext.class);

        assertTrue(editor.converge(context));

        // Verify the system modification message
        ArgumentCaptor<String> modificationMessage = ArgumentCaptor.forClass(String.class);
        verify(context).recordSystemModification(any(), modificationMessage.capture());
        assertEquals(
                "Patching file /file:\n" +
                        "+first\n" +
                        "-eight\n" +
                        "+third\n" +
                        "+fourth\n" +
                        "+sixth\n" +
                        "+eight\n",
                modificationMessage.getValue());

        // Verify the new contents of the file:
        assertEquals(joinLines("first", "second", "third", "fourth", "fifth", "sixth", "seventh", "eight"),
                path.readUtf8File());
    }

    @Test
    void noop() {
        path.writeUtf8File("line\n");

        LineEditor lineEditor = mock(LineEditor.class);
        when(lineEditor.edit(any())).thenReturn(LineEdit.none());

        Editor editor = new Editor(path.toPath(), lineEditor);
        TaskContext context = mock(TaskContext.class);

        assertFalse(editor.converge(context));

        verify(lineEditor, times(1)).edit(any());

        // Verify the system modification message
        verify(context, times(0)).recordSystemModification(any(), any());

        // Verify same contents
        assertEquals("line\n", path.readUtf8File());
    }

    @Test
    void testMissingFile() {
        LineEditor lineEditor = mock(LineEditor.class);
        when(lineEditor.onComplete()).thenReturn(List.of("line"));

        TaskContext context = mock(TaskContext.class);
        var editor = new Editor(path.toPath(), lineEditor);
        editor.converge(context);

        assertEquals("line\n", path.readUtf8File());
    }

    private static String joinLines(String... lines) {
        return String.join("\n", lines) + "\n";
    }
}