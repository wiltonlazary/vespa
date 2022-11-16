// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.node.admin.task.util.file;

import com.yahoo.vespa.hosted.node.admin.component.TaskContext;
import com.yahoo.vespa.test.file.TestFileSystem;
import org.junit.jupiter.api.Test;

import java.nio.file.FileSystem;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

public class TemplateTest {

    @Test
    void basic() {
        FileSystem fileSystem = TestFileSystem.create();
        Path templatePath = fileSystem.getPath("/example.vm");
        String templateContent = "a $x, $y b";
        new UnixPath(templatePath).writeUtf8File(templateContent);

        Path toPath = fileSystem.getPath("/example");
        TaskContext taskContext = mock(TaskContext.class);
        boolean converged = Template.at(templatePath)
                .set("x", "foo")
                .set("y", "bar")
                .getFileWriterTo(toPath)
                .converge(taskContext);

        assertTrue(converged);

        String actualContent = new UnixPath(toPath).readUtf8File();
        assertEquals("a foo, bar b", actualContent);
    }

}