// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class document::TestDocMan
 * \ingroup base
 *
 * \brief Utility for unit tests that need document manager and documents.
 *
 * This test class sets up a document manager, and defines a few document types
 * for use in testing.
 *
 * The following document types are defined by this manager
 * (add more when needed):
 *
 * testtype1
 *     headerval int (header variable)
 *     content string (body variable)
 */

#pragma once

#include "testdocrepo.h"
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/datatype/documenttype.h>

namespace document {

class Document;

class TestDocMan {
    static std::vector<char> _buffer;
    TestDocRepo _test_repo;
    std::shared_ptr<const DocumentTypeRepo> _repo;
    const DocumenttypesConfig *_typeCfg;

public:
    TestDocMan();
    ~TestDocMan();

    void setTypeRepo(const std::shared_ptr<const DocumentTypeRepo> &repo);

    const DocumentTypeRepo& getTypeRepo() const { return *_repo; }
    std::shared_ptr<const DocumentTypeRepo> getTypeRepoSP() const { return _repo; }
    const DocumenttypesConfig *getTypeConfig() const { return _typeCfg; }

    /** Create test document. */
    std::unique_ptr<Document> createDocument(
            const std::string& content = "This is the contents of "
                    "the test document.\nIt ain't much.\n",
            const std::string& uri = "id:test:testdoctype1::test",
            const std::string& type = "testdoctype1") const;

    /** Create random document from given seed. */
    std::unique_ptr<Document> createRandomDocument(
            int seed = 0, int maxContentSize = 0x80) const;
    /** Create random document from given seed belonging to given location */
    std::unique_ptr<Document> createRandomDocumentAtLocation(
            int location, int seed = 0, int maxContentSize = 0x80) const;
    std::unique_ptr<Document> createRandomDocumentAtLocation(
            int location, int seed, int minContentSize, int maxContentSize) const;
    /** Create random document of given type from given seed. */
    std::unique_ptr<Document> createRandomDocument(
            const std::string& type, int seed = 0,
            int maxContentSize = 0x80) const;
    static std::string generateRandomContent(uint32_t size);
};

} // document

