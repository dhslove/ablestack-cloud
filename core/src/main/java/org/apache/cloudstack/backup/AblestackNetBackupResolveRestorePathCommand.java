//
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
//

package org.apache.cloudstack.backup;

import com.cloud.agent.api.Command;

import java.util.List;

public class AblestackNetBackupResolveRestorePathCommand extends Command {
    private String backupId;
    private List<String> candidatePaths;
    private List<String> requiredFiles;
    private Integer discoveryWindowSeconds;
    private boolean requireSingleRestorePathInVmRoot;
    private boolean requireSingleCandidateRestorePath;
    private List<String> allowedRestorePathsInVmRoot;

    protected AblestackNetBackupResolveRestorePathCommand() {
        super();
    }

    public AblestackNetBackupResolveRestorePathCommand(final String backupId, final List<String> candidatePaths, final Integer discoveryWindowSeconds) {
        this(backupId, candidatePaths, null, discoveryWindowSeconds);
    }

    public AblestackNetBackupResolveRestorePathCommand(final String backupId, final List<String> candidatePaths,
            final List<String> requiredFiles, final Integer discoveryWindowSeconds) {
        this(backupId, candidatePaths, requiredFiles, discoveryWindowSeconds, false);
    }

    public AblestackNetBackupResolveRestorePathCommand(final String backupId, final List<String> candidatePaths,
            final List<String> requiredFiles, final Integer discoveryWindowSeconds, final boolean requireSingleRestorePathInVmRoot) {
        this(backupId, candidatePaths, requiredFiles, discoveryWindowSeconds, requireSingleRestorePathInVmRoot, null);
    }

    public AblestackNetBackupResolveRestorePathCommand(final String backupId, final List<String> candidatePaths,
            final List<String> requiredFiles, final Integer discoveryWindowSeconds, final boolean requireSingleRestorePathInVmRoot,
            final List<String> allowedRestorePathsInVmRoot) {
        this(backupId, candidatePaths, requiredFiles, discoveryWindowSeconds, requireSingleRestorePathInVmRoot, false,
                allowedRestorePathsInVmRoot);
    }

    public AblestackNetBackupResolveRestorePathCommand(final String backupId, final List<String> candidatePaths,
            final List<String> requiredFiles, final Integer discoveryWindowSeconds, final boolean requireSingleRestorePathInVmRoot,
            final boolean requireSingleCandidateRestorePath, final List<String> allowedRestorePathsInVmRoot) {
        this.backupId = backupId;
        this.candidatePaths = candidatePaths;
        this.requiredFiles = requiredFiles;
        this.discoveryWindowSeconds = discoveryWindowSeconds;
        this.requireSingleRestorePathInVmRoot = requireSingleRestorePathInVmRoot;
        this.requireSingleCandidateRestorePath = requireSingleCandidateRestorePath;
        this.allowedRestorePathsInVmRoot = allowedRestorePathsInVmRoot;
    }

    public String getBackupId() {
        return backupId;
    }

    public List<String> getCandidatePaths() {
        return candidatePaths;
    }

    public List<String> getRequiredFiles() {
        return requiredFiles;
    }

    public Integer getDiscoveryWindowSeconds() {
        return discoveryWindowSeconds == null ? 0 : discoveryWindowSeconds;
    }

    public boolean isRequireSingleRestorePathInVmRoot() {
        return requireSingleRestorePathInVmRoot;
    }

    public boolean isRequireSingleCandidateRestorePath() {
        return requireSingleCandidateRestorePath;
    }

    public List<String> getAllowedRestorePathsInVmRoot() {
        return allowedRestorePathsInVmRoot;
    }

    @Override
    public boolean executeInSequence() {
        return true;
    }
}
