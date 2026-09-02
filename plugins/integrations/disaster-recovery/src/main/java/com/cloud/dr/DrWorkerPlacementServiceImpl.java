// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements. See the NOTICE file distributed with this work.
package com.cloud.dr;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;

import com.cloud.dr.dao.DrResourceLeaseDao;
import com.cloud.dr.dao.DrSiteDao;
import com.cloud.host.Host;
import com.cloud.host.HostVO;
import com.cloud.host.Status;
import com.cloud.host.dao.HostDao;
import com.cloud.host.dao.HostDetailsDao;
import com.cloud.hypervisor.Hypervisor.HypervisorType;
import com.cloud.utils.component.ManagerBase;

/** Selects current workers without consulting Plan-persisted host bindings. */
public class DrWorkerPlacementServiceImpl extends ManagerBase implements DrWorkerPlacementService {
    private static final String HOST_PREFIX = "HOST:";

    @Inject private DrSiteDao drSiteDao;
    @Inject private HostDao hostDao;
    @Inject private HostDetailsDao hostDetailsDao;
    @Inject private DrResourceLeaseDao drResourceLeaseDao;

    @Override
    public Long resolveWorkerHostId(DrPlanVO plan, DrWorkerRole role) {
        return resolveWorkerHostId(plan, null, role);
    }

    @Override
    public Long resolveWorkerHostId(DrPlanVO plan, DrRunVO run, DrWorkerRole role) {
        Long leasedHostId = leasedHostId(run);
        if (leasedHostId != null && eligible(leasedHostId, plan, role)) {
            return leasedHostId;
        }
        List<HostVO> candidates = listEligibleWorkers(plan, role);
        if (candidates.isEmpty()) {
            return null;
        }
        int seed = plan != null && StringUtils.isNotBlank(plan.getUuid())
                ? plan.getUuid().hashCode() : plan != null ? Long.valueOf(plan.getId()).hashCode() : 0;
        return candidates.get(Math.floorMod(seed, candidates.size())).getId();
    }

    @Override
    public List<HostVO> listEligibleWorkers(DrPlanVO plan, DrWorkerRole role) {
        DrSiteVO site = siteFor(plan, role);
        if (site == null || site.getZoneId() == null || hostDao == null) {
            return Collections.emptyList();
        }
        List<HostVO> hosts = hostDao.listAllHostsUpByZoneAndHypervisor(site.getZoneId(), HypervisorType.KVM);
        if (hosts == null || hosts.isEmpty()) {
            return Collections.emptyList();
        }
        List<HostVO> eligible = new ArrayList<HostVO>();
        for (HostVO host : hosts) {
            if (host == null || host.getRemoved() != null || !eligibleForRole(host, role)) {
                continue;
            }
            eligible.add(host);
        }
        eligible.sort(Comparator.comparingLong(HostVO::getId));
        return eligible;
    }

    private DrSiteVO siteFor(DrPlanVO plan, DrWorkerRole role) {
        if (plan == null || drSiteDao == null) {
            return null;
        }
        long siteId = role == DrWorkerRole.SOURCE && plan.getSourceVmId() != null
                ? plan.getSourceSiteId() : plan.getTargetSiteId();
        return drSiteDao.findById(siteId);
    }

    private boolean eligible(Long hostId, DrPlanVO plan, DrWorkerRole role) {
        if (hostId == null || hostDao == null) {
            return false;
        }
        HostVO host = hostDao.findById(hostId);
        if (host == null || host.getRemoved() != null || host.getStatus() != Status.Up
                || host.getHypervisorType() != HypervisorType.KVM || !eligibleForRole(host, role)) {
            return false;
        }
        DrSiteVO site = siteFor(plan, role);
        return site != null && site.getZoneId() != null && host.getDataCenterId() == site.getZoneId().longValue();
    }

    private boolean eligibleForRole(HostVO host, DrWorkerRole role) {
        if (role != DrWorkerRole.VDDK_DATA_PLANE) {
            return true;
        }
        String libDir = detail(host.getId(), Host.HOST_VDDK_LIB_DIR);
        String supported = detail(host.getId(), Host.HOST_VDDK_SUPPORT);
        return StringUtils.isNotBlank(libDir)
                && (StringUtils.isBlank(supported) || Boolean.parseBoolean(supported));
    }

    private String detail(long hostId, String name) {
        com.cloud.host.DetailVO detail = hostDetailsDao != null ? hostDetailsDao.findDetail(hostId, name) : null;
        return detail != null ? StringUtils.trimToNull(detail.getValue()) : null;
    }

    private Long leasedHostId(DrRunVO run) {
        if (run == null || drResourceLeaseDao == null) {
            return null;
        }
        DrResourceLeaseVO lease = drResourceLeaseDao.findActiveByRunId(run.getId(), new Date());
        String key = lease != null ? lease.getResourceKey() : null;
        if (!StringUtils.startsWith(key, HOST_PREFIX)) {
            return null;
        }
        String value = StringUtils.substringBefore(StringUtils.substringAfter(key, HOST_PREFIX), ":");
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
