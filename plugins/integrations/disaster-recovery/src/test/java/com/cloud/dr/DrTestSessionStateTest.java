// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements. See the NOTICE file
// distributed with this work for additional information.
package com.cloud.dr;

import org.junit.Assert;
import org.junit.Test;

public class DrTestSessionStateTest {
    @Test
    public void engineArtifactsReadyDoesNotDowngradeActiveCloudSession() {
        Assert.assertEquals(DrTestSessionState.ACTIVE,
                DrTestSessionState.projectEngineState(DrTestSessionState.ACTIVE, "TEST_ARTIFACTS_READY"));
    }

    @Test
    public void engineErrorDoesNotInvalidateRunningCloudTestEnvironment() {
        Assert.assertEquals(DrTestSessionState.ACTIVE,
                DrTestSessionState.projectEngineState(DrTestSessionState.ACTIVE, "ERROR"));
    }

    @Test
    public void artifactsReadyAdvancesRequestedSession() {
        Assert.assertEquals(DrTestSessionState.ARTIFACTS_READY,
                DrTestSessionState.projectEngineState(DrTestSessionState.REQUESTED, "TEST_ARTIFACTS_READY"));
    }

    @Test
    public void artifactsReadyDoesNotDowngradeCloudVmCreation() {
        Assert.assertEquals(DrTestSessionState.CLOUD_VM_CREATING,
                DrTestSessionState.projectEngineState(DrTestSessionState.CLOUD_VM_CREATING, "TEST_ARTIFACTS_READY"));
    }
}
