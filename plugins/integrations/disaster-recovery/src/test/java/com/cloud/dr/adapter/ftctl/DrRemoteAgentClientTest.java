// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
package com.cloud.dr.adapter.ftctl;

import java.util.UUID;

import org.junit.Assert;
import org.junit.Test;

import com.cloud.agent.api.FtctlDrActionCommand;

public class DrRemoteAgentClientTest {

    @Test
    public void sourceTransitionRunUuidIsDeterministicAndSchemaSafe() {
        String planUuid = "7ec74483-8554-415d-ac56-f62f8b17fbd0";
        String parentRunUuid = "d64fdb24-9fb3-49ad-82de-2556db63698b";

        String first = DrRemoteAgentClient.sourceTransitionRunUuid(planUuid, parentRunUuid,
                FtctlDrActionCommand.Action.RESUME_SYNC);
        String second = DrRemoteAgentClient.sourceTransitionRunUuid(planUuid, parentRunUuid,
                FtctlDrActionCommand.Action.RESUME_SYNC);
        String pause = DrRemoteAgentClient.sourceTransitionRunUuid(planUuid, parentRunUuid,
                FtctlDrActionCommand.Action.PAUSE_SYNC);

        Assert.assertEquals(first, second);
        Assert.assertNotEquals(first, pause);
        Assert.assertEquals(36, first.length());
        Assert.assertEquals(first, UUID.fromString(first).toString());
    }
}
