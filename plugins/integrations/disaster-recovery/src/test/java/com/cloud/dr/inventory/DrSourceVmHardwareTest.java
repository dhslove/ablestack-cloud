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
package com.cloud.dr.inventory;

import java.util.Date;

import org.junit.Assert;
import org.junit.Test;

public class DrSourceVmHardwareTest {
    @Test
    public void fingerprintIgnoresObservationTime() {
        DrSourceVmHardware first = hardware(true, new Date(1L));
        DrSourceVmHardware second = hardware(true, new Date(2L));

        Assert.assertEquals(first.getFingerprint(), second.getFingerprint());
    }

    @Test
    public void fingerprintChangesWhenSecureBootChanges() {
        DrSourceVmHardware secure = hardware(true, new Date(1L));
        DrSourceVmHardware legacy = hardware(false, new Date(1L));

        Assert.assertNotEquals(secure.getFingerprint(), legacy.getFingerprint());
    }

    private DrSourceVmHardware hardware(boolean secureBoot, Date observedAt) {
        DrSourceVmHardware hardware = new DrSourceVmHardware();
        hardware.setSourceVmRef("vm-4486");
        hardware.setFirmware("EFI");
        hardware.setSecureBootEnabled(secureBoot);
        hardware.setGuestId("rockylinux_64Guest");
        hardware.setCpuCount(2);
        hardware.setMemoryMiB(4096L);
        hardware.setRootDiskController("scsi");
        hardware.setDataDiskController("scsi");
        hardware.setObservedAt(observedAt);
        hardware.setInventorySource("VCENTER_GOVC_AGENT");
        hardware.seal();
        return hardware;
    }
}
