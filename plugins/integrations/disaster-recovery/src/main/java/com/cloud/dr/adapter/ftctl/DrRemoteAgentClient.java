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

import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;

import com.cloud.agent.api.Answer;
import com.cloud.agent.api.Command;
import com.cloud.dr.DrConstants;
import com.cloud.dr.DrPlanVO;
import com.cloud.dr.DrResolvedSiteCredential;
import com.cloud.dr.DrSiteCredentialService;
import com.cloud.dr.DrSiteVO;
import com.cloud.dr.dao.DrSiteDao;
import com.cloud.dr.inventory.DrMoldInventoryClient;
import com.cloud.host.HostVO;
import com.cloud.utils.exception.CloudRuntimeException;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class DrRemoteAgentClient {
    private static final Gson GSON = new Gson();

    @Inject private DrSiteDao drSiteDao;
    @Inject private DrSiteCredentialService drSiteCredentialService;
    @Inject private DrMoldInventoryClient drMoldInventoryClient;

    public boolean isRemoteKvmSource(DrPlanVO plan) {
        if (plan == null || !StringUtils.equalsIgnoreCase(plan.getDirection(), DrConstants.DIRECTION_KVM_TO_KVM)
                || plan.getSourceVmId() != null || StringUtils.isBlank(plan.getSourceExternalRef())) {
            return false;
        }
        DrSiteVO sourceSite = drSiteDao.findById(plan.getSourceSiteId());
        if (sourceSite == null) {
            return false;
        }
        DrResolvedSiteCredential credential = null;
        try {
            credential = drSiteCredentialService.resolveCredential(sourceSite);
            return credential != null && credential.getCredential() != null
                    && StringUtils.equalsIgnoreCase(credential.getCredential().getCredentialType(), DrConstants.CREDENTIAL_TYPE_MOLD_API);
        } finally {
            if (credential != null) {
                credential.close();
            }
        }
    }

    public <T extends Answer> T execute(DrPlanVO plan, String commandType, Command command,
            String workerHostUuid, Class<T> answerType) {
        if (StringUtils.isBlank(workerHostUuid)) {
            throw new CloudRuntimeException("Remote DR source worker host UUID is required");
        }
        DrSiteVO sourceSite = drSiteDao.findById(plan.getSourceSiteId());
        DrResolvedSiteCredential credential = drSiteCredentialService.resolveCredential(sourceSite);
        if (credential == null || !credential.hasSecrets()) {
            throw new CloudRuntimeException("Remote DR source site credentials are unavailable");
        }
        try {
            JsonObject response = drMoldInventoryClient.executeSiteAgentCommand(credential, commandType,
                    GSON.toJson(command), workerHostUuid);
            String answerJson = firstString(response, "answerjson");
            if (StringUtils.isBlank(answerJson)) {
                throw new CloudRuntimeException(StringUtils.defaultIfBlank(firstString(response, "details"),
                        "Remote DR source site returned no typed Agent answer"));
            }
            return GSON.fromJson(answerJson, answerType);
        } finally {
            credential.close();
        }
    }

    public void prepareTransport(DrPlanVO plan, HostVO targetHost, String targetDirectory) {
        if (plan == null || targetHost == null || StringUtils.isAnyBlank(targetHost.getUuid(), targetHost.getPrivateIpAddress())) {
            throw new CloudRuntimeException("Remote DR target worker host UUID and address are required");
        }
        DrSiteVO sourceSite = drSiteDao.findById(plan.getSourceSiteId());
        DrSiteVO targetSite = drSiteDao.findById(plan.getTargetSiteId());
        DrResolvedSiteCredential sourceCredential = null;
        DrResolvedSiteCredential targetCredential = null;
        try {
            sourceCredential = drSiteCredentialService.resolveCredential(sourceSite);
            targetCredential = drSiteCredentialService.resolveCredential(targetSite);
            if (sourceCredential == null || !sourceCredential.hasSecrets()
                    || targetCredential == null || !targetCredential.hasSecrets()) {
                throw new CloudRuntimeException("Both DR site Mold API credentials are required for remote transport preparation");
            }
            drMoldInventoryClient.prepareRemoteSshAccess(sourceCredential, targetCredential,
                    plan.getSourceExternalRef(), targetHost.getUuid(), targetHost.getPrivateIpAddress(),
                    targetDirectory, targetHost.getPrivateIpAddress());
        } finally {
            if (sourceCredential != null) {
                sourceCredential.close();
            }
            if (targetCredential != null) {
                targetCredential.close();
            }
        }
    }

    private String firstString(JsonObject object, String key) {
        if (object == null || StringUtils.isBlank(key)) {
            return null;
        }
        for (String candidate : object.keySet()) {
            if (!StringUtils.equalsIgnoreCase(candidate, key)) {
                continue;
            }
            JsonElement value = object.get(candidate);
            return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
        }
        return null;
    }
}
