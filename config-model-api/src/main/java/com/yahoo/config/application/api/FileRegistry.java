// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.application.api;

import com.yahoo.config.FileReference;

import java.nio.ByteBuffer;
import java.util.List;

/**
 * @author Tony Vaagenes
 */
public interface FileRegistry {

    FileReference addFile(String relativePath);
    FileReference addUri(String uri);
    FileReference addBlob(String name, ByteBuffer blob);
    default FileReference addApplicationPackage() { return addFile(""); }
    List<Entry> export();

    class Entry {

        public final String relativePath;
        public final FileReference reference;

        public Entry(String relativePath, FileReference reference) {
            this.relativePath = relativePath;
            this.reference = reference;
        }
    }

}
