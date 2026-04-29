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
package com.cloud.agent.api;

public class FtctlSyncProfileCommand extends Command {

    private String vmName;
    private String mode;
    private String peerUri;
    private String profileName;
    private String backendMode;
    private String targetStorageScope;
    private String targetStoragePoolId;
    private String targetStoragePoolName;
    private String secondaryVmName;
    private String fencingPolicy;
    private String secondaryTargetDir;
    private String remoteNbdExportAddr;
    private String xcoloProxyEndpoint;
    private String xcoloNbdEndpoint;
    private String xcoloMigrateUri;
    private String fencingIpmiPrimaryHost;
    private String fencingIpmiPrimaryPort;
    private String fencingIpmiPrimaryUser;
    private String fencingIpmiPrimaryPassword;
    private String fencingIpmiPrimaryInterface;
    private String fencingIpmiSecondaryHost;
    private String fencingIpmiSecondaryPort;
    private String fencingIpmiSecondaryUser;
    private String fencingIpmiSecondaryPassword;
    private String fencingIpmiSecondaryInterface;

    public FtctlSyncProfileCommand() {
    }

    public FtctlSyncProfileCommand(String vmName, String mode, String peerUri) {
        this.vmName = vmName;
        this.mode = mode;
        this.peerUri = peerUri;
    }

    public String getVmName() {
        return vmName;
    }

    public String getMode() {
        return mode;
    }

    public String getPeerUri() {
        return peerUri;
    }

    public String getProfileName() {
        return profileName;
    }

    public void setProfileName(String profileName) {
        this.profileName = profileName;
    }

    public String getBackendMode() {
        return backendMode;
    }

    public void setBackendMode(String backendMode) {
        this.backendMode = backendMode;
    }

    public String getTargetStorageScope() {
        return targetStorageScope;
    }

    public void setTargetStorageScope(String targetStorageScope) {
        this.targetStorageScope = targetStorageScope;
    }

    public String getTargetStoragePoolId() {
        return targetStoragePoolId;
    }

    public void setTargetStoragePoolId(String targetStoragePoolId) {
        this.targetStoragePoolId = targetStoragePoolId;
    }

    public String getTargetStoragePoolName() {
        return targetStoragePoolName;
    }

    public void setTargetStoragePoolName(String targetStoragePoolName) {
        this.targetStoragePoolName = targetStoragePoolName;
    }

    public String getSecondaryVmName() {
        return secondaryVmName;
    }

    public void setSecondaryVmName(String secondaryVmName) {
        this.secondaryVmName = secondaryVmName;
    }

    public String getFencingPolicy() {
        return fencingPolicy;
    }

    public void setFencingPolicy(String fencingPolicy) {
        this.fencingPolicy = fencingPolicy;
    }

    public String getSecondaryTargetDir() {
        return secondaryTargetDir;
    }

    public void setSecondaryTargetDir(String secondaryTargetDir) {
        this.secondaryTargetDir = secondaryTargetDir;
    }

    public String getRemoteNbdExportAddr() {
        return remoteNbdExportAddr;
    }

    public void setRemoteNbdExportAddr(String remoteNbdExportAddr) {
        this.remoteNbdExportAddr = remoteNbdExportAddr;
    }

    public String getXcoloProxyEndpoint() {
        return xcoloProxyEndpoint;
    }

    public void setXcoloProxyEndpoint(String xcoloProxyEndpoint) {
        this.xcoloProxyEndpoint = xcoloProxyEndpoint;
    }

    public String getXcoloNbdEndpoint() {
        return xcoloNbdEndpoint;
    }

    public void setXcoloNbdEndpoint(String xcoloNbdEndpoint) {
        this.xcoloNbdEndpoint = xcoloNbdEndpoint;
    }

    public String getXcoloMigrateUri() {
        return xcoloMigrateUri;
    }

    public void setXcoloMigrateUri(String xcoloMigrateUri) {
        this.xcoloMigrateUri = xcoloMigrateUri;
    }

    public String getFencingIpmiPrimaryHost() {
        return fencingIpmiPrimaryHost;
    }

    public void setFencingIpmiPrimaryHost(String fencingIpmiPrimaryHost) {
        this.fencingIpmiPrimaryHost = fencingIpmiPrimaryHost;
    }

    public String getFencingIpmiPrimaryPort() {
        return fencingIpmiPrimaryPort;
    }

    public void setFencingIpmiPrimaryPort(String fencingIpmiPrimaryPort) {
        this.fencingIpmiPrimaryPort = fencingIpmiPrimaryPort;
    }

    public String getFencingIpmiPrimaryUser() {
        return fencingIpmiPrimaryUser;
    }

    public void setFencingIpmiPrimaryUser(String fencingIpmiPrimaryUser) {
        this.fencingIpmiPrimaryUser = fencingIpmiPrimaryUser;
    }

    public String getFencingIpmiPrimaryPassword() {
        return fencingIpmiPrimaryPassword;
    }

    public void setFencingIpmiPrimaryPassword(String fencingIpmiPrimaryPassword) {
        this.fencingIpmiPrimaryPassword = fencingIpmiPrimaryPassword;
    }

    public String getFencingIpmiPrimaryInterface() {
        return fencingIpmiPrimaryInterface;
    }

    public void setFencingIpmiPrimaryInterface(String fencingIpmiPrimaryInterface) {
        this.fencingIpmiPrimaryInterface = fencingIpmiPrimaryInterface;
    }

    public String getFencingIpmiSecondaryHost() {
        return fencingIpmiSecondaryHost;
    }

    public void setFencingIpmiSecondaryHost(String fencingIpmiSecondaryHost) {
        this.fencingIpmiSecondaryHost = fencingIpmiSecondaryHost;
    }

    public String getFencingIpmiSecondaryPort() {
        return fencingIpmiSecondaryPort;
    }

    public void setFencingIpmiSecondaryPort(String fencingIpmiSecondaryPort) {
        this.fencingIpmiSecondaryPort = fencingIpmiSecondaryPort;
    }

    public String getFencingIpmiSecondaryUser() {
        return fencingIpmiSecondaryUser;
    }

    public void setFencingIpmiSecondaryUser(String fencingIpmiSecondaryUser) {
        this.fencingIpmiSecondaryUser = fencingIpmiSecondaryUser;
    }

    public String getFencingIpmiSecondaryPassword() {
        return fencingIpmiSecondaryPassword;
    }

    public void setFencingIpmiSecondaryPassword(String fencingIpmiSecondaryPassword) {
        this.fencingIpmiSecondaryPassword = fencingIpmiSecondaryPassword;
    }

    public String getFencingIpmiSecondaryInterface() {
        return fencingIpmiSecondaryInterface;
    }

    public void setFencingIpmiSecondaryInterface(String fencingIpmiSecondaryInterface) {
        this.fencingIpmiSecondaryInterface = fencingIpmiSecondaryInterface;
    }

    @Override
    public boolean executeInSequence() {
        return false;
    }
}
