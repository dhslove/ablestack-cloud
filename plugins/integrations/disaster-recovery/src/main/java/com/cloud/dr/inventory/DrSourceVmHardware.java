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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.google.gson.JsonObject;

public class DrSourceVmHardware {
    private String sourceVmRef;
    private String sourceHostUuid;
    private String sourceHostName;
    private String instanceName;
    private String firmware;
    private Boolean secureBootEnabled;
    private String guestId;
    private Integer cpuCount;
    private Long memoryMiB;
    private String rootDiskController;
    private String dataDiskController;
    private Date observedAt;
    private String inventorySource;
    private String fingerprint;
    private String errorCode;
    private String message;

    public static DrSourceVmHardware unavailable(String sourceVmRef, String errorCode, String message) {
        DrSourceVmHardware hardware = new DrSourceVmHardware();
        hardware.sourceVmRef = sourceVmRef;
        hardware.errorCode = errorCode;
        hardware.message = message;
        hardware.observedAt = new Date();
        return hardware;
    }

    public boolean isComplete() {
        return StringUtils.isNotBlank(firmware) && secureBootEnabled != null;
    }

    public void seal() {
        observedAt = observedAt != null ? observedAt : new Date();
        fingerprint = "sha256:" + sha256(canonicalJson().toString());
    }

    public JsonObject toJsonObject() {
        JsonObject object = canonicalJson();
        if (observedAt != null) {
            object.addProperty("observedAtEpochMs", observedAt.getTime());
        }
        if (StringUtils.isNotBlank(inventorySource)) {
            object.addProperty("inventorySource", inventorySource);
        }
        if (StringUtils.isNotBlank(fingerprint)) {
            object.addProperty("fingerprint", fingerprint);
        }
        if (StringUtils.isNotBlank(errorCode)) {
            object.addProperty("errorCode", errorCode);
        }
        if (StringUtils.isNotBlank(message)) {
            object.addProperty("message", message);
        }
        return object;
    }

    public Map<String, String> toDetails() {
        Map<String, String> details = new LinkedHashMap<String, String>();
        JsonObject json = toJsonObject();
        for (Map.Entry<String, com.google.gson.JsonElement> entry : json.entrySet()) {
            if (entry.getValue() != null && entry.getValue().isJsonPrimitive()) {
                details.put(entry.getKey(), entry.getValue().getAsString());
            }
        }
        return details;
    }

    private JsonObject canonicalJson() {
        JsonObject object = new JsonObject();
        add(object, "sourceVmRef", sourceVmRef);
        add(object, "sourceHostUuid", sourceHostUuid);
        add(object, "sourceHostName", sourceHostName);
        add(object, "instanceName", instanceName);
        add(object, "firmware", firmware);
        if (secureBootEnabled != null) {
            object.addProperty("secureBoot", secureBootEnabled);
        }
        add(object, "guestId", guestId);
        if (cpuCount != null) {
            object.addProperty("cpuCount", cpuCount);
        }
        if (memoryMiB != null) {
            object.addProperty("memoryMiB", memoryMiB);
        }
        add(object, "rootDiskController", rootDiskController);
        add(object, "dataDiskController", dataDiskController);
        return object;
    }

    private static void add(JsonObject object, String key, String value) {
        if (StringUtils.isNotBlank(value)) {
            object.addProperty(key, value);
        }
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                int valueByte = item & 0xff;
                hex.append(Character.forDigit(valueByte >>> 4, 16));
                hex.append(Character.forDigit(valueByte & 0x0f, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    public String getSourceVmRef() { return sourceVmRef; }
    public void setSourceVmRef(String sourceVmRef) { this.sourceVmRef = sourceVmRef; }
    public void setSourceHostUuid(String sourceHostUuid) { this.sourceHostUuid = sourceHostUuid; }
    public void setSourceHostName(String sourceHostName) { this.sourceHostName = sourceHostName; }
    public void setInstanceName(String instanceName) { this.instanceName = instanceName; }
    public String getFirmware() { return firmware; }
    public void setFirmware(String firmware) { this.firmware = firmware; }
    public Boolean getSecureBootEnabled() { return secureBootEnabled; }
    public void setSecureBootEnabled(Boolean secureBootEnabled) { this.secureBootEnabled = secureBootEnabled; }
    public String getGuestId() { return guestId; }
    public void setGuestId(String guestId) { this.guestId = guestId; }
    public Integer getCpuCount() { return cpuCount; }
    public void setCpuCount(Integer cpuCount) { this.cpuCount = cpuCount; }
    public Long getMemoryMiB() { return memoryMiB; }
    public void setMemoryMiB(Long memoryMiB) { this.memoryMiB = memoryMiB; }
    public String getRootDiskController() { return rootDiskController; }
    public void setRootDiskController(String rootDiskController) { this.rootDiskController = rootDiskController; }
    public String getDataDiskController() { return dataDiskController; }
    public void setDataDiskController(String dataDiskController) { this.dataDiskController = dataDiskController; }
    public Date getObservedAt() { return observedAt; }
    public void setObservedAt(Date observedAt) { this.observedAt = observedAt; }
    public String getInventorySource() { return inventorySource; }
    public void setInventorySource(String inventorySource) { this.inventorySource = inventorySource; }
    public String getFingerprint() { return fingerprint; }
    public String getErrorCode() { return errorCode; }
    public String getMessage() { return message; }
}
