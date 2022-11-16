// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package configserver

import (
	"github.com/vespa-engine/vespa/client/go/util"
	"github.com/vespa-engine/vespa/client/go/vespa"
)

func makeFixSpec() util.FixSpec {
	vespaUid, vespaGid := vespa.FindVespaUidAndGid()
	return util.FixSpec{
		UserId:   vespaUid,
		GroupId:  vespaGid,
		DirMode:  0755,
		FileMode: 0644,
	}
}

func fixDirsAndFiles(fixSpec util.FixSpec) {
	fixSpec.FixDir("conf/zookeeper")
	fixSpec.FixDir("var/zookeeper")
	fixSpec.FixDir("var/zookeeper/version-2")
	fixSpec.FixFile("conf/zookeeper/zookeeper.cfg")
	fixSpec.FixFile("var/zookeeper/myid")
}
