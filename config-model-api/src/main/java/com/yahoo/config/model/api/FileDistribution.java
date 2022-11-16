// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.api;

import com.yahoo.config.FileReference;

import java.util.Set;

/**
 * Interface for models towards filedistribution.
 *
 * @author Ulf Lilleengen
 */
public interface FileDistribution {
    /**
     * Notifies client which file references to download. Used to start downloading early (while
     * preparing application package).
     *
     * @param hostName       host which should be notified about file references to download
     * @param port           port which should be used when notifying
     * @param fileReferences set of file references to start downloading
     */
    void startDownload(String hostName, int port, Set<FileReference> fileReferences);

}
