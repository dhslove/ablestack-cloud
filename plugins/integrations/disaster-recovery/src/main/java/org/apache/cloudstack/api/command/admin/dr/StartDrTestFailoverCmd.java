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
package org.apache.cloudstack.api.command.admin.dr;

import javax.inject.Inject;

import org.apache.cloudstack.acl.RoleType;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiErrorCode;
import org.apache.cloudstack.api.ResponseObject;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.response.dr.DrRunResponse;
import org.apache.commons.lang3.StringUtils;

import com.cloud.dr.DrRestorePointVO;
import com.cloud.dr.dao.DrRestorePointDao;
import com.cloud.dr.cluster.DisasterRecoveryClusterEventTypes;
import com.google.gson.JsonObject;

@APICommand(name = StartDrTestFailoverCmd.APINAME, description = "Start a DR test failover run", responseObject = DrRunResponse.class,
        responseView = ResponseObject.ResponseView.Full, authorized = {RoleType.Admin})
public class StartDrTestFailoverCmd extends AbstractDrPlanActionCmd {
    public static final String APINAME = "startDrTestFailover";

    @Inject
    private DrRestorePointDao drRestorePointDao;

    @Override
    protected String getRunType() {
        return "TEST_FAILOVER";
    }

    @Override
    protected String getApiName() {
        return APINAME;
    }

    @Override
    protected void addRequestProperties(JsonObject request) {
        DrRestorePointVO restorePoint = resolveRestorePoint();
        if (restorePoint == null) {
            return;
        }
        if (StringUtils.isBlank(restorePoint.getSourceSnapshotRef())) {
            throw new ServerApiException(ApiErrorCode.PARAM_ERROR, "Selected DR restore point does not have an engine restore point reference");
        }
        addProperty(request, "restorePointRef", restorePoint.getSourceSnapshotRef());
        addProperty(request, "restorePointType", restorePoint.getRestorePointType());
        addProperty(request, "restorePointTargetReadyRpoSeconds", restorePoint.getTargetReadyRpoSeconds());
    }

    private DrRestorePointVO resolveRestorePoint() {
        Long restorePointId = getRestorePointId();
        if (restorePointId != null) {
            DrRestorePointVO restorePoint = drRestorePointDao.findById(restorePointId);
            if (restorePoint == null || restorePoint.getRemoved() != null || restorePoint.getPlanId() != getPlanId()) {
                throw new ServerApiException(ApiErrorCode.PARAM_ERROR, "DR restore point is not valid for the selected plan");
            }
            return restorePoint;
        }
        return drRestorePointDao.findLatestTargetReadyByPlanId(getPlanId());
    }

    @Override
    public String getEventType() {
        return DisasterRecoveryClusterEventTypes.EVENT_DR_PLAN_TEST_FAILOVER;
    }

    @Override
    public String getEventDescription() {
        return "Starting DR test failover for plan " + getPlanId();
    }
}
