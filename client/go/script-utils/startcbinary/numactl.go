// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package startcbinary

import (
	"fmt"
	"strconv"
	"strings"

	"github.com/vespa-engine/vespa/client/go/trace"
	"github.com/vespa-engine/vespa/client/go/util"
)

func (p *ProgSpec) configureNumaCtl() {
	p.shouldUseNumaCtl = false
	p.numaSocket = -1
	if p.getenv(ENV_VESPA_NO_NUMACTL) != "" {
		return
	}
	backticks := util.BackTicksIgnoreStderr
	out, err := backticks.Run("numactl", "--hardware")
	trace.Debug("numactl --hardware says:", out)
	if err != nil {
		trace.Trace("numactl error:", err)
		return
	}
	outfoo, errfoo := backticks.Run("numactl", "--interleave", "all", "echo", "foo")
	if errfoo != nil {
		trace.Trace("cannot run with numactl:", errfoo)
		return
	}
	if outfoo != "foo\n" {
		trace.Trace("bad numactl output:", outfoo)
		return
	}
	p.shouldUseNumaCtl = true
	if affinity := p.getenv(ENV_VESPA_AFFINITY_CPU_SOCKET); affinity != "" {
		wantSocket, _ := strconv.Atoi(affinity)
		trace.Debug("want socket:", wantSocket)
		parts := strings.Fields(out)
		for idx := 0; idx+2 < len(parts); idx++ {
			if parts[idx] == "available:" && parts[idx+2] == "nodes" {
				numSockets, _ := strconv.Atoi(parts[idx+1])
				trace.Debug("numSockets:", numSockets)
				if numSockets > 1 {
					p.numaSocket = (wantSocket % numSockets)
					return
				}
			}
		}
	}
}

func (p *ProgSpec) numaCtlBinary() string {
	return "numactl"
}

func (p *ProgSpec) prependNumaCtl(args []string) []string {
	result := make([]string, 0, 5+len(args))
	result = append(result, "numactl")
	if p.numaSocket >= 0 {
		result = append(result, fmt.Sprintf("--cpunodebind=%d", p.numaSocket))
		result = append(result, fmt.Sprintf("--membind=%d", p.numaSocket))
	} else {
		result = append(result, "--interleave")
		result = append(result, "all")
	}
	for _, arg := range args {
		result = append(result, arg)
	}
	return result
}
