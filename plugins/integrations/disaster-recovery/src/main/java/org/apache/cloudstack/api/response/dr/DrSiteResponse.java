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
package org.apache.cloudstack.api.response.dr;

import java.util.Date;

import org.apache.cloudstack.api.BaseResponse;
import org.apache.cloudstack.api.EntityReference;

import com.cloud.dr.DrSiteVO;
import com.cloud.serializer.Param;
import com.google.gson.annotations.SerializedName;

@EntityReference(value = DrSiteVO.class)
public class DrSiteResponse extends BaseResponse {
    @SerializedName("id")
    @Param(description = "the DR site ID")
    private String id;

    @SerializedName("name")
    @Param(description = "the DR site name")
    private String name;

    @SerializedName("description")
    @Param(description = "the DR site description")
    private String description;

    @SerializedName("sitetype")
    @Param(description = "the DR site type")
    private String siteType;

    @SerializedName("hypervisortype")
    @Param(description = "the hypervisor type")
    private String hypervisorType;

    @SerializedName("endpoint")
    @Param(description = "the endpoint URL or address")
    private String endpoint;

    @SerializedName("credentialref")
    @Param(description = "the masked credential reference")
    private String credentialRef;

    @SerializedName("zoneid")
    @Param(description = "the local CloudStack zone ID")
    private Long zoneId;

    @SerializedName("vmwaredcid")
    @Param(description = "the VMware datacenter ID")
    private Long vmwareDatacenterId;

    @SerializedName("state")
    @Param(description = "the administrative state")
    private String state;

    @SerializedName("healthstate")
    @Param(description = "the site health state")
    private String healthState;

    @SerializedName("capabilities")
    @Param(description = "the site capabilities JSON")
    private String capabilitiesJson;

    @SerializedName("lastchecked")
    @Param(description = "the last check time")
    private Date lastChecked;

    @SerializedName("created")
    @Param(description = "the creation date")
    private Date created;

    @SerializedName("removed")
    @Param(description = "the removal date")
    private Date removed;

    public void setId(String id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setSiteType(String siteType) {
        this.siteType = siteType;
    }

    public void setHypervisorType(String hypervisorType) {
        this.hypervisorType = hypervisorType;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public void setCredentialRef(String credentialRef) {
        this.credentialRef = credentialRef;
    }

    public void setZoneId(Long zoneId) {
        this.zoneId = zoneId;
    }

    public void setVmwareDatacenterId(Long vmwareDatacenterId) {
        this.vmwareDatacenterId = vmwareDatacenterId;
    }

    public void setState(String state) {
        this.state = state;
    }

    public void setHealthState(String healthState) {
        this.healthState = healthState;
    }

    public void setCapabilitiesJson(String capabilitiesJson) {
        this.capabilitiesJson = capabilitiesJson;
    }

    public void setLastChecked(Date lastChecked) {
        this.lastChecked = lastChecked;
    }

    public void setCreated(Date created) {
        this.created = created;
    }

    public void setRemoved(Date removed) {
        this.removed = removed;
    }
}
