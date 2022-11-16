// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/config/common/configsystem.h>
#include <vespa/defaults.h>
#include <vespa/fastos/file.h>
#include <unistd.h>
#include <filesystem>

using namespace config;

namespace {

const char *VESPA_HOME="VESPA_HOME";
char cwd[1_Ki];

}

TEST("require that bad home directory fails") {
    ASSERT_TRUE(nullptr != getcwd(cwd, sizeof(cwd)));
    ASSERT_EQUAL(0, setenv(VESPA_HOME, "/nowhere/near/", 1));
    vespa::Defaults::bootstrap("/nowhere/near/");
    ConfigSystem configSystem;
    ASSERT_FALSE(configSystem.isUp());
}

TEST("require that incorrect pid file type fails") {
    ASSERT_TRUE(nullptr != getcwd(cwd, sizeof(cwd)));
    std::filesystem::remove_all(std::filesystem::path("var"));
    std::filesystem::create_directories(std::filesystem::path("var/run/configproxy.pid"));

    ASSERT_EQUAL(0, setenv(VESPA_HOME, cwd, 1));
    vespa::Defaults::bootstrap(cwd);
    ConfigSystem configSystem;
    ASSERT_FALSE(configSystem.isUp());
    std::filesystem::remove_all(std::filesystem::path("var"));
}

TEST("require that correct pid file succeeds") {
    ASSERT_TRUE(nullptr != getcwd(cwd, sizeof(cwd)));
    std::filesystem::remove_all(std::filesystem::path("var"));
    std::filesystem::create_directories(std::filesystem::path("var/run"));
    FastOS_File pid_file("var/run/configproxy.pid");
    pid_file.OpenWriteOnlyTruncate();
    ASSERT_TRUE(pid_file.Close());

    ASSERT_EQUAL(0, setenv(VESPA_HOME, cwd, 1));
    vespa::Defaults::bootstrap(cwd);
    ConfigSystem configSystem;
    ASSERT_TRUE(configSystem.isUp());
    std::filesystem::remove_all(std::filesystem::path("var"));
}

TEST_MAIN() { TEST_RUN_ALL(); }
