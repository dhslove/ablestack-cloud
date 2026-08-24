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
package com.cloud.hypervisor.kvm.resource.wrapper;

import org.junit.Assert;
import org.junit.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class LibvirtFtctlDrActionCommandWrapperTest {

    @Test
    public void statusQuerySuccessDoesNotAcceptFailedRun() {
        JsonObject payload = new JsonParser().parse("{\"result\":\"ok\",\"accepted\":false,"
                + "\"state\":\"ERROR\",\"error_code\":\"DR_RECOVERY_FAILED\"}").getAsJsonObject();

        Assert.assertTrue(LibvirtFtctlDrActionCommandWrapper.isSemanticFailureStatus(payload));
        Assert.assertFalse(LibvirtFtctlDrActionCommandWrapper.isAcceptedStatus(0, payload));
    }

    @Test
    public void statusQueryAcceptsHealthyRunningRun() {
        JsonObject payload = new JsonParser().parse("{\"result\":\"ok\",\"accepted\":true,"
                + "\"state\":\"SYNCING\",\"error_code\":\"\"}").getAsJsonObject();

        Assert.assertFalse(LibvirtFtctlDrActionCommandWrapper.isSemanticFailureStatus(payload));
        Assert.assertTrue(LibvirtFtctlDrActionCommandWrapper.isAcceptedStatus(0, payload));
    }
}
