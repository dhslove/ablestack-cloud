// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements. See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership. The ASF licenses this file
// to you under the Apache License, Version 2.0.
package com.cloud.dr;

import org.junit.Assert;
import org.junit.Test;

public class DrTestSessionStateTest {

    @Test
    public void failedSessionWithoutCleanupOrTargetDoesNotBlockNewTest() {
        DrTestSessionVO session = new DrTestSessionVO(38L, 104L, DrTestSessionState.FAILED);
        session.setCleanupRequired(false);

        Assert.assertFalse(DrTestSessionState.blocksNewTest(session));
        Assert.assertTrue(DrTestSessionState.canSoftCloseFailedSession(session, true));
    }

    @Test
    public void failedSessionWithCleanupObligationBlocksNewTest() {
        DrTestSessionVO session = new DrTestSessionVO(38L, 104L, DrTestSessionState.FAILED);
        session.setCleanupRequired(true);

        Assert.assertTrue(DrTestSessionState.blocksNewTest(session));
        Assert.assertFalse(DrTestSessionState.canSoftCloseFailedSession(session, true));
    }

    @Test
    public void failedSessionWithTargetVmBlocksNewTest() {
        DrTestSessionVO session = new DrTestSessionVO(38L, 104L, DrTestSessionState.FAILED);
        session.setCleanupRequired(false);
        session.setTargetVmId(259L);

        Assert.assertTrue(DrTestSessionState.blocksNewTest(session));
        Assert.assertFalse(DrTestSessionState.canSoftCloseFailedSession(session, true));
    }

    @Test
    public void cleanupFailureAlwaysBlocksNewTest() {
        DrTestSessionVO session = new DrTestSessionVO(38L, 104L, DrTestSessionState.CLEANUP_FAILED);
        session.setCleanupRequired(false);

        Assert.assertTrue(DrTestSessionState.blocksNewTest(session));
    }
}
