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

import java.util.Map;

import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;

import com.cloud.agent.AgentManager;
import com.cloud.agent.api.Answer;
import com.cloud.agent.api.FtctlDrSourceHardwareAnswer;
import com.cloud.agent.api.FtctlDrSourceHardwareCommand;
import com.cloud.dr.DrConstants;
import com.cloud.dr.DrPlanReadinessValidator;
import com.cloud.dr.DrPlanVO;
import com.cloud.dr.DrResolvedSiteCredential;
import com.cloud.dr.DrSiteCredentialService;
import com.cloud.dr.DrSiteVO;
import com.cloud.dr.dao.DrSiteDao;
import com.cloud.exception.AgentUnavailableException;
import com.cloud.exception.OperationTimedoutException;
import com.cloud.host.HostVO;
import com.cloud.host.Status;
import com.cloud.host.dao.HostDao;
import com.cloud.utils.component.ManagerBase;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class DrSourceHardwareInventoryServiceImpl extends ManagerBase implements DrSourceHardwareInventoryService {
    @Inject
    private DrSiteDao drSiteDao;
    @Inject
    private DrSiteCredentialService drSiteCredentialService;
    @Inject
    private AgentManager agentManager;
    @Inject
    private HostDao hostDao;
    @Inject
    private DrMoldInventoryClient drMoldInventoryClient;

    @Override
    public DrSourceVmHardware resolve(DrPlanVO plan) {
        if (plan == null) {
            return null;
        }
        if (StringUtils.equalsIgnoreCase(plan.getDirection(), DrConstants.DIRECTION_KVM_TO_KVM)
                && plan.getSourceVmId() == null && StringUtils.isNotBlank(plan.getSourceExternalRef())) {
            return resolveRemoteMoldSource(plan);
        }
        if (!StringUtils.startsWithIgnoreCase(plan.getDirection(), "VMWARE_")) {
            return null;
        }
        if (StringUtils.isBlank(plan.getSourceExternalRef())) {
            return DrSourceVmHardware.unavailable(null,
                    DrPlanReadinessValidator.REASON_SOURCE_HARDWARE_INVENTORY_REQUIRED,
                    "VMware source VM reference is required");
        }
        DrSiteVO site = drSiteDao != null ? drSiteDao.findById(plan.getSourceSiteId()) : null;
        if (site == null || site.getRemoved() != null) {
            return DrSourceVmHardware.unavailable(plan.getSourceExternalRef(), DrConstants.ERROR_SITE_NOT_FOUND,
                    "VMware source site was not found");
        }
        Long workerHostId = firstNonNull(plan.getTargetWorkerHostId(), plan.getCoordinatorWorkerHostId(), plan.getSourceWorkerHostId());
        return resolve(site, plan.getSourceExternalRef(), workerHostId);
    }

    private DrSourceVmHardware resolveRemoteMoldSource(DrPlanVO plan) {
        DrSiteVO site = drSiteDao != null ? drSiteDao.findById(plan.getSourceSiteId()) : null;
        if (site == null || site.getRemoved() != null) {
            return DrSourceVmHardware.unavailable(plan.getSourceExternalRef(), DrConstants.ERROR_SITE_NOT_FOUND,
                    "ABLESTACK source site was not found");
        }
        DrResolvedSiteCredential credential = null;
        try {
            credential = drSiteCredentialService.resolveCredential(site);
            if (credential == null || !credential.hasSecrets()
                    || !StringUtils.equalsIgnoreCase(credential.getCredential().getCredentialType(), DrConstants.CREDENTIAL_TYPE_MOLD_API)) {
                return DrSourceVmHardware.unavailable(plan.getSourceExternalRef(),
                        DrPlanReadinessValidator.REASON_SOURCE_HARDWARE_INVENTORY_REQUIRED,
                        "ABLESTACK source site Mold API credential is required");
            }
            Map<String, String> values = drMoldInventoryClient.getVirtualMachineHardware(credential, plan.getSourceExternalRef());
            DrSourceVmHardware hardware = new DrSourceVmHardware();
            hardware.setSourceVmRef(plan.getSourceExternalRef());
            hardware.setSourceHostUuid(values.get("sourceHostUuid"));
            hardware.setSourceHostName(values.get("sourceHostName"));
            hardware.setInstanceName(values.get("instanceName"));
            hardware.setFirmware(firstNonBlank(values.get("uefi"), values.get("bootType"), values.get("bootMode"), "LEGACY"));
            hardware.setSecureBootEnabled(parseBoolean(values.get("secureBoot"), false));
            hardware.setGuestId(firstNonBlank(values.get("guestOsName"), values.get("guestOsId")));
            hardware.setCpuCount(parseInteger(values.get("cpuCount")));
            hardware.setMemoryMiB(parseLong(values.get("memoryMiB")));
            hardware.setInventorySource("MOLD_API");
            hardware.seal();
            return hardware;
        } catch (RuntimeException e) {
            return DrSourceVmHardware.unavailable(plan.getSourceExternalRef(),
                    DrPlanReadinessValidator.REASON_SOURCE_HARDWARE_INVENTORY_REQUIRED,
                    StringUtils.defaultIfBlank(e.getMessage(), "ABLESTACK source hardware inventory failed"));
        } finally {
            if (credential != null) {
                credential.close();
            }
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private Boolean parseBoolean(String value, boolean fallback) {
        return StringUtils.isBlank(value) ? fallback : Boolean.valueOf(value);
    }

    private Integer parseInteger(String value) {
        try { return StringUtils.isBlank(value) ? null : Integer.valueOf(value); } catch (NumberFormatException e) { return null; }
    }

    private Long parseLong(String value) {
        try { return StringUtils.isBlank(value) ? null : Long.valueOf(value); } catch (NumberFormatException e) { return null; }
    }

    @Override
    public DrSourceVmHardware resolve(DrSiteVO sourceSite, String sourceVmRef, Long workerHostId) {
        if (sourceSite == null || sourceSite.getRemoved() != null) {
            return DrSourceVmHardware.unavailable(sourceVmRef, DrConstants.ERROR_SITE_NOT_FOUND,
                    "VMware source site was not found");
        }
        if (workerHostId == null) {
            return DrSourceVmHardware.unavailable(sourceVmRef,
                    DrPlanReadinessValidator.REASON_SOURCE_HARDWARE_INVENTORY_REQUIRED,
                    "A KVM worker host is required for vCenter hardware inventory");
        }
        HostVO worker = hostDao != null ? hostDao.findById(workerHostId) : null;
        if (worker == null || worker.getStatus() != Status.Up) {
            return DrSourceVmHardware.unavailable(sourceVmRef,
                    DrPlanReadinessValidator.REASON_SOURCE_HARDWARE_INVENTORY_REQUIRED,
                    "The selected vCenter inventory worker host is unavailable");
        }
        DrResolvedSiteCredential credential = null;
        try {
            credential = drSiteCredentialService.resolveCredential(sourceSite);
            if (credential == null || !credential.hasSecrets()) {
                return DrSourceVmHardware.unavailable(sourceVmRef,
                        DrPlanReadinessValidator.REASON_SOURCE_HARDWARE_INVENTORY_REQUIRED,
                        "VMware source site credential is required");
            }
            String password = secret(credential.getSecretPayload(), "password");
            FtctlDrSourceHardwareCommand command = new FtctlDrSourceHardwareCommand(
                    credential.getCredential().getEndpoint(), credential.getCredential().getPrincipal(), password,
                    credential.getCredential().getTlsVerify(), sourceVmRef);
            Answer rawAnswer = agentManager.send(workerHostId, command);
            if (!(rawAnswer instanceof FtctlDrSourceHardwareAnswer) || !rawAnswer.getResult()) {
                return DrSourceVmHardware.unavailable(sourceVmRef,
                        DrPlanReadinessValidator.REASON_SOURCE_HARDWARE_INVENTORY_REQUIRED,
                        rawAnswer != null ? rawAnswer.getDetails() : "vCenter hardware inventory returned no answer");
            }
            FtctlDrSourceHardwareAnswer answer = (FtctlDrSourceHardwareAnswer) rawAnswer;
            DrSourceVmHardware hardware = new DrSourceVmHardware();
            hardware.setSourceVmRef(sourceVmRef);
            hardware.setFirmware(answer.getFirmware());
            hardware.setSecureBootEnabled(answer.getSecureBoot());
            hardware.setGuestId(answer.getGuestId());
            hardware.setCpuCount(answer.getCpuCount());
            hardware.setMemoryMiB(answer.getMemoryMiB());
            hardware.setRootDiskController(answer.getRootDiskController());
            hardware.setDataDiskController(answer.getDataDiskController());
            hardware.setInventorySource(answer.getInventorySource());
            hardware.seal();
            return hardware;
        } catch (AgentUnavailableException | OperationTimedoutException | RuntimeException e) {
            return DrSourceVmHardware.unavailable(sourceVmRef,
                    DrPlanReadinessValidator.REASON_SOURCE_HARDWARE_INVENTORY_REQUIRED,
                    StringUtils.defaultIfBlank(e.getMessage(), "VMware source hardware inventory failed"));
        } finally {
            if (credential != null) {
                credential.close();
            }
        }
    }

    private Long firstNonNull(Long... values) {
        if (values != null) {
            for (Long value : values) {
                if (value != null) {
                    return value;
                }
            }
        }
        return null;
    }

    private String secret(JsonObject payload, String key) {
        if (payload == null || !payload.has(key)) {
            return null;
        }
        JsonElement value = payload.get(key);
        return value != null && !value.isJsonNull() ? StringUtils.trimToNull(value.getAsString()) : null;
    }
}
