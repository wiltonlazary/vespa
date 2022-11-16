// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package jvm

import (
	"github.com/vespa-engine/vespa/client/go/defaults"
)

func (opts *Options) AddCommonJdkProperties() {
	tmpDir := defaults.UnderVespaHome("var/tmp")
	libDir := defaults.UnderVespaHome("lib64")
	secOvr := defaults.UnderVespaHome("conf/vespa/java.security.override")
	opts.fixSpec.FixDir(tmpDir)
	opts.AddOption("-Djava.io.tmpdir=" + tmpDir)
	opts.AddOption("-Djava.library.path=" + libDir)
	opts.AddOption("-Djava.security.properties=" + secOvr)
	opts.AddOption("-Djava.awt.headless=true")
	opts.AddOption("-Dsun.rmi.dgc.client.gcInterval=3600000")
	opts.AddOption("-Dsun.net.client.defaultConnectTimeout=5000")
	opts.AddOption("-Dsun.net.client.defaultReadTimeout=60000")
	opts.AddOption("-Djavax.net.ssl.keyStoreType=JKS")
	opts.AddOption("-Djdk.tls.rejectClientInitiatedRenegotiation=true")
	opts.AddOption("-Dfile.encoding=UTF-8")
	opts.AddOption("-Dorg.apache.commons.logging.Log=org.apache.commons.logging.impl.Jdk14Logger")
}
