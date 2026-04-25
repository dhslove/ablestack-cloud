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

package org.apache.cloudstack.wallAlerts.model;

import java.util.Map;

public class AlertRuleDto {
    private String uid;
    private String title;
    private String durationFor;
    private Double threshold;
    private String operator;
    private String folderUid;
    private String ruleGroup;
    private String dashboardUrl;
    private Map<String, String> labels;
    private Map<String, String> annotations;

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDurationFor() { return durationFor; }
    public void setDurationFor(String durationFor) { this.durationFor = durationFor; }
    public Double getThreshold() { return threshold; }
    public void setThreshold(Double threshold) { this.threshold = threshold; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getFolderUid() { return folderUid; }
    public void setFolderUid(String folderUid) { this.folderUid = folderUid; }
    public String getRuleGroup() { return ruleGroup; }
    public void setRuleGroup(String ruleGroup) { this.ruleGroup = ruleGroup; }
    public String getDashboardUrl() { return dashboardUrl; }
    public void setDashboardUrl(String dashboardUrl) { this.dashboardUrl = dashboardUrl; }
    public Map<String, String> getLabels() { return labels; }
    public void setLabels(Map<String, String> labels) { this.labels = labels; }
    public Map<String, String> getAnnotations() { return annotations; }
    public void setAnnotations(Map<String, String> annotations) { this.annotations = annotations; }

}
