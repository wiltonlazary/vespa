// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.testutils;

import com.yahoo.vdslib.state.ClusterState;
import com.yahoo.vespa.clustercontroller.core.ClusterStateBundle;
import com.yahoo.vespa.clustercontroller.core.FakeTimer;
import com.yahoo.vespa.clustercontroller.core.listeners.SystemStateListener;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Old class used for waiting for something..
 * Deprecated.. Use the Waiter class instead
 */
public class StateWaiter implements SystemStateListener {

    private final FakeTimer timer;
    protected ClusterState current;

    public StateWaiter(FakeTimer timer) {
        this.timer = timer;
    }

    @Override
    public void handleNewPublishedState(ClusterStateBundle state) {
        synchronized(timer) {
            current = state.getBaselineClusterState();
            timer.notifyAll();
        }
    }

    @Override
    public void handleNewCandidateState(ClusterStateBundle states) {
        // Treat candidate states as if they were published for the tests that use
        // this (deprecated) waiter class.
        //
        // Since the tests using StateWaiter expects to observe _both_ versioned and
        // unversioned (candidate) states, we ignore candidate states iff they are
        // equal to the versioned state we have already observed. Otherwise, tests
        // waiting for a _versioned_ state risk never observing the version number
        // itself (only a candidate following it) and hang until they time out.
        synchronized (timer) {
            if (current != null) {
                ClusterState versionPatchedState = states.getBaselineClusterState().clone();
                versionPatchedState.setVersion(current.getVersion());
                if (versionPatchedState.equals(current)) {
                    return;
                }
            }
        }
        handleNewPublishedState(states);
    }

    public ClusterState getCurrentSystemState() {
        synchronized(timer) {
            return current;
        }
    }

    public void waitForState(String stateRegex, Duration timeout) {
        waitForState(stateRegex, timeout.toMillis(), 0);
    }

    /**
     * WARNING: If timeIntervalToProvokeRetry is set != 0 that means time will be set far into the future
     * and thus hit various unintended timeout periods. Only auto-step time if this is a non-issue.
     */
    public void waitForState(String stateRegex, long timeout, long timeIntervalToProvokeRetry) {
        Pattern p = Pattern.compile(stateRegex);
        long startTime = System.currentTimeMillis();
        final long endTime = startTime + timeout;
        int iteration = 0;
        while (true) {
            ClusterState currentClusterState;
            synchronized(timer) {
                currentClusterState = current;

                if (currentClusterState != null) {
                    Matcher m = p.matcher(currentClusterState.toString());

                    if (m.matches()) {
                        return;
                    }
                }
                try {
                    if (timeIntervalToProvokeRetry == 0) {
                        var waitTime = Math.max(1, endTime - startTime);
                        timer.wait(waitTime);
                    } else {
                        if (++iteration % 10 == 0) {
                            timer.advanceTime(timeIntervalToProvokeRetry);
                        }
                        timer.wait(10);
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            if (System.currentTimeMillis() >= endTime) {
                throw new IllegalStateException("Timeout. Did not find a state matching " + stateRegex + " within timeout of " + timeout + " milliseconds. Current state is " + currentClusterState);
            }
        }
    }

}
