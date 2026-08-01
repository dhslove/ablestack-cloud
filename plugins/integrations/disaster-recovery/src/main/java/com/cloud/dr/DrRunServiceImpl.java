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
package com.cloud.dr;

import java.util.Date;
import java.util.List;

import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;

import com.cloud.dr.dao.DrRunDao;
import com.cloud.dr.dao.DrRunStepDao;
import com.cloud.dr.orchestrator.DrOrchestrator;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.utils.component.ManagerBase;

public class DrRunServiceImpl extends ManagerBase implements DrRunService {
    @Inject
    private DrRunDao drRunDao;
    @Inject
    private DrRunStepDao drRunStepDao;
    @Inject
    private DrOrchestrator drOrchestrator;

    @Override
    public DrRunVO startRun(long planId, String runType, String idempotencyKey, Long requestedByUserId, Long asyncJobId) {
        return startRun(planId, runType, idempotencyKey, requestedByUserId, asyncJobId, null);
    }

    @Override
    public DrRunVO startRun(long planId, String runType, String idempotencyKey, Long requestedByUserId, Long asyncJobId, String requestJson) {
        DrRunVO run = drOrchestrator.createRun(planId, runType, idempotencyKey, requestedByUserId, asyncJobId, requestJson);
        return drOrchestrator.executeRun(run.getId());
    }

    @Override
    public DrRunVO getRun(long runId) {
        return requireRun(runId);
    }

    @Override
    public List<DrRunVO> listRuns(long planId) {
        return drRunDao.listByPlanId(planId);
    }

    @Override
    public DrRunVO findRunByIdempotencyKey(long planId, String idempotencyKey) {
        if (StringUtils.isBlank(idempotencyKey)) {
            return null;
        }
        return drRunDao.findByPlanIdAndIdempotencyKey(planId, idempotencyKey);
    }

    @Override
    public List<DrRunStepVO> listRunSteps(long runId) {
        requireRun(runId);
        return drRunStepDao.listActiveByRunId(runId);
    }

    @Override
    public DrRunVO cancelRun(long runId) {
        DrRunVO run = requireRun(runId);
        if (run.getCompleted() != null) {
            return run;
        }
        if (StringUtils.equals(DrConstants.RUN_STATE_QUEUED, run.getState())) {
            run.setState(DrConstants.RUN_STATE_CANCELED);
            run.setCompleted(new Date());
        } else {
            run.setState(DrConstants.RUN_STATE_CANCEL_REQUESTED);
        }
        run.markUpdated();
        drRunDao.update(runId, run);
        drOrchestrator.recordEvent(run.getPlanId(), run.getId(), DrConstants.EVENT_RUN_CANCELED, DrConstants.EVENT_SEVERITY_WARN,
                DrConstants.EVENT_SOURCE_CLOUD, "DR run cancel requested", null);
        return drRunDao.findById(runId);
    }

    private DrRunVO requireRun(long runId) {
        DrRunVO run = drRunDao.findById(runId);
        if (run == null || run.getRemoved() != null) {
            throw new InvalidParameterValueException(DrConstants.ERROR_RUN_NOT_FOUND + ": " + runId);
        }
        return run;
    }
}
