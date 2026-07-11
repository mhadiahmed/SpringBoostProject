package com.springboost.launcher;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The daemon is shared across projects, but different projects can depend on
 * different spring-boost versions (installed at different paths under
 * ~/.m2) -- if daemon identity were a single global file, a launcher for one
 * version could silently attach to a stale daemon from a different version.
 * currentIdentityKey() must be a stable function of the launch command so
 * different jars get different daemons.
 */
class DaemonPathsTest {

    @Test
    void currentIdentityKeyIsStableAcrossCalls() {
        assertEquals(DaemonPaths.currentIdentityKey(), DaemonPaths.currentIdentityKey());
    }

    @Test
    void keyedFilesLiveUnderHomeAndDifferKeysProduceDifferentFiles() {
        String keyA = "aaaaaaaaaaaaaaaa";
        String keyB = "bbbbbbbbbbbbbbbb";

        assertTrue(DaemonPaths.portFile(keyA).startsWith(DaemonPaths.HOME));
        assertNotEquals(DaemonPaths.portFile(keyA), DaemonPaths.portFile(keyB));
        assertNotEquals(DaemonPaths.lockFile(keyA), DaemonPaths.portFile(keyA));
        assertNotEquals(DaemonPaths.logFile(keyA), DaemonPaths.portFile(keyA));
    }
}
