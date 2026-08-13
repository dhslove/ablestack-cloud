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

package com.cloud.hypervisor.kvm.resource.wrapper;

import com.cloud.agent.api.Answer;
import com.cloud.hypervisor.kvm.resource.LibvirtComputingResource;
import com.cloud.resource.CommandWrapper;
import com.cloud.resource.ResourceWrapper;
import org.apache.cloudstack.backup.AblestackNetBackupResolveRestorePathCommand;
import org.apache.cloudstack.backup.BackupAnswer;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@ResourceWrapper(handles = AblestackNetBackupResolveRestorePathCommand.class)
public class LibvirtAblestackNetBackupResolveRestorePathCommandWrapper extends
        CommandWrapper<AblestackNetBackupResolveRestorePathCommand, Answer, LibvirtComputingResource> {
    private static final Pattern TIMESTAMP_DIR_PATTERN =
            Pattern.compile("^\\d{4}\\.\\d{2}\\.\\d{2}\\.\\d{2}\\.\\d{2}\\.\\d{2}\\.\\d+$");
    private static final long RESTORE_PATH_DISCOVERY_INTERVAL_MS = 5000L;

    @Override
    public Answer execute(final AblestackNetBackupResolveRestorePathCommand command,
            final LibvirtComputingResource serverResource) {
        if (StringUtils.isBlank(command.getBackupId())) {
            return new BackupAnswer(command, false, "NetBackup backup ID is required to resolve restored path.");
        }
        if (CollectionUtils.isEmpty(command.getCandidatePaths())) {
            return new BackupAnswer(command, false, String.format(
                    "No candidate restore paths were provided for NetBackup backup ID [%s].", command.getBackupId()));
        }

        final long deadline = System.currentTimeMillis() + Math.max(0, command.getDiscoveryWindowSeconds()) * 1000L;
        final Map<String, RestoreFileState> previousRequiredFileStates = new HashMap<>();
        logger.info("Resolving NetBackup restore path on KVM. backupId=[{}], candidatePaths={}, requiredFiles={}, discoveryWindowSeconds=[{}]",
                command.getBackupId(), command.getCandidatePaths(), command.getRequiredFiles(), command.getDiscoveryWindowSeconds());
        do {
            if (CollectionUtils.isNotEmpty(command.getRequiredFiles())) {
                if (areRequiredRestoreSourcesReady(command.getCandidatePaths(), command.getRequiredFiles(), previousRequiredFileStates)) {
                    logger.info("Required NetBackup restore files are ready for backupId [{}]: paths={}, files={}",
                            command.getBackupId(), command.getCandidatePaths(), command.getRequiredFiles());
                    return new BackupAnswer(command, true, StringUtils.join(command.getCandidatePaths(), ","));
                }
            } else {
                final List<Path> validCandidatePaths = findValidRestoreCandidates(command.getCandidatePaths());
                if (CollectionUtils.isNotEmpty(validCandidatePaths)) {
                    if (command.isRequireSingleCandidateRestorePath() && validCandidatePaths.size() != 1) {
                        return new BackupAnswer(command, false, String.format(
                                "Expected exactly one valid NetBackup restored candidate path for backup ID [%s], but found [%s]: %s. "
                                        + "Mold cannot safely determine which VM restore path belongs to this NetBackup restore notify. "
                                        + "Please cleanup stale NetBackup restore paths before retrying.",
                                command.getBackupId(), validCandidatePaths.size(), validCandidatePaths));
                    }
                    final Path path = validCandidatePaths.get(0);
                    final String singleRestorePathValidationError = validateSingleRestorePathInVmRootIfRequired(command, path);
                    if (StringUtils.isNotBlank(singleRestorePathValidationError)) {
                        return new BackupAnswer(command, false, singleRestorePathValidationError);
                    }
                    logger.info("Selected NetBackup restore path for backupId [{}]: [{}]", command.getBackupId(), path);
                    return new BackupAnswer(command, true, path.toAbsolutePath().normalize().toString());
                }
            }

            if (System.currentTimeMillis() >= deadline) {
                break;
            }
            if (!sleepBeforeNextDiscoveryAttempt(command.getBackupId())) {
                break;
            }
        } while (true);

        final String failureDetail = CollectionUtils.isNotEmpty(command.getRequiredFiles())
                ? String.format("Required restore paths or files were not found. paths=%s, files=%s, missingPaths=%s, missingFiles=%s",
                        command.getCandidatePaths(), command.getRequiredFiles(),
                        getMissingCandidatePaths(command.getCandidatePaths()), getMissingRequiredFiles(command.getRequiredFiles()))
                : "No candidate restore path contained the required files.";
        return new BackupAnswer(command, false, String.format(
                "Unable to resolve restored path for NetBackup backup ID [%s]. %s",
                command.getBackupId(), failureDetail));
    }

    private List<Path> findValidRestoreCandidates(final List<String> candidatePaths) {
        final List<Path> validCandidatePaths = new ArrayList<>();
        if (CollectionUtils.isEmpty(candidatePaths)) {
            return validCandidatePaths;
        }
        for (final String candidatePath : candidatePaths) {
            if (StringUtils.isBlank(candidatePath)) {
                continue;
            }
            final Path path = Paths.get(candidatePath);
            if (isValidRestoreCandidate(path)) {
                validCandidatePaths.add(path.toAbsolutePath().normalize());
            }
        }
        return validCandidatePaths;
    }

    private boolean areRequiredRestoreSourcesReady(final List<String> candidatePaths, final List<String> requiredFiles,
            final Map<String, RestoreFileState> previousFileStates) {
        if (CollectionUtils.isEmpty(candidatePaths) || CollectionUtils.isEmpty(requiredFiles)) {
            return false;
        }
        boolean ready = true;
        for (final String candidatePath : candidatePaths) {
            if (StringUtils.isBlank(candidatePath) || !Files.isDirectory(Paths.get(candidatePath))) {
                ready = false;
            }
        }
        for (final String requiredFile : requiredFiles) {
            if (StringUtils.isBlank(requiredFile)) {
                ready = false;
                continue;
            }
            final Path requiredFilePath = Paths.get(requiredFile);
            if (!Files.isRegularFile(requiredFilePath)) {
                previousFileStates.remove(requiredFile);
                ready = false;
                continue;
            }
            try {
                final RestoreFileState currentState = RestoreFileState.from(requiredFilePath);
                final RestoreFileState previousState = previousFileStates.put(requiredFile, currentState);
                if (currentState.size <= 0 || previousState == null || !currentState.equals(previousState)) {
                    logger.debug("Waiting for NetBackup restored file [{}] to become stable. previousState={}, currentState={}",
                            requiredFile, previousState, currentState);
                    ready = false;
                }
            } catch (IOException e) {
                logger.debug("Failed to inspect NetBackup restored file [{}] while waiting for stability.", requiredFile, e);
                previousFileStates.remove(requiredFile);
                ready = false;
            }
        }
        return ready;
    }

    private String validateSingleRestorePathInVmRootIfRequired(final AblestackNetBackupResolveRestorePathCommand command,
            final Path selectedRestorePath) {
        if (!command.isRequireSingleRestorePathInVmRoot()) {
            return null;
        }
        final Path vmRestoreRoot = selectedRestorePath == null ? null : selectedRestorePath.toAbsolutePath().normalize().getParent();
        if (vmRestoreRoot == null || !Files.isDirectory(vmRestoreRoot)) {
            return String.format("Unable to validate NetBackup restore path uniqueness for backup ID [%s]. VM restore root was not found for selected path [%s].",
                    command.getBackupId(), selectedRestorePath);
        }

        final List<String> timestampRestorePaths = listTimestampRestorePaths(vmRestoreRoot);
        final List<String> allowedRestorePaths = normalizeRestorePaths(command.getAllowedRestorePathsInVmRoot());
        if (CollectionUtils.isNotEmpty(allowedRestorePaths)) {
            final List<String> unexpectedRestorePaths = timestampRestorePaths.stream()
                    .filter(path -> !allowedRestorePaths.contains(path))
                    .collect(java.util.stream.Collectors.toList());
            if (unexpectedRestorePaths.isEmpty()) {
                return null;
            }
            return String.format("Found unexpected NetBackup restored timestamp paths under VM restore root [%s] for backup ID [%s]: %s. "
                            + "Allowed restore paths for this NetBackup restore notify are: %s. Mold did not cleanup these paths automatically because it cannot safely determine which path is stale. "
                            + "Please cleanup stale NetBackup restore paths under [%s] before retrying.",
                    vmRestoreRoot, command.getBackupId(), unexpectedRestorePaths, allowedRestorePaths, vmRestoreRoot);
        }
        if (timestampRestorePaths.size() == 1) {
            return null;
        }
        return String.format("Expected exactly one NetBackup restored timestamp path under VM restore root [%s] for backup ID [%s], but found [%s]: %s. "
                        + "Mold did not cleanup these paths automatically because it cannot safely determine which path is stale. "
                        + "Please cleanup stale NetBackup restore paths under [%s] before retrying.",
                vmRestoreRoot, command.getBackupId(), timestampRestorePaths.size(), timestampRestorePaths, vmRestoreRoot);
    }

    private List<String> normalizeRestorePaths(final List<String> restorePaths) {
        if (CollectionUtils.isEmpty(restorePaths)) {
            return new ArrayList<>();
        }
        return restorePaths.stream()
                .filter(StringUtils::isNotBlank)
                .map(path -> Paths.get(path).toAbsolutePath().normalize().toString())
                .distinct()
                .collect(java.util.stream.Collectors.toList());
    }

    private List<String> listTimestampRestorePaths(final Path vmRestoreRoot) {
        final List<String> timestampRestorePaths = new ArrayList<>();
        try (Stream<Path> children = Files.list(vmRestoreRoot)) {
            children
                    .filter(Files::isDirectory)
                    .filter(child -> TIMESTAMP_DIR_PATTERN.matcher(child.getFileName().toString()).matches())
                    .map(child -> child.toAbsolutePath().normalize().toString())
                    .sorted()
                    .forEach(timestampRestorePaths::add);
        } catch (IOException e) {
            logger.debug("Failed to list NetBackup restore timestamp paths under [{}].", vmRestoreRoot, e);
        }
        return timestampRestorePaths;
    }

    private static final class RestoreFileState {
        private final long size;
        private final long modifiedTimeMillis;

        private RestoreFileState(final long size, final long modifiedTimeMillis) {
            this.size = size;
            this.modifiedTimeMillis = modifiedTimeMillis;
        }

        private static RestoreFileState from(final Path path) throws IOException {
            return new RestoreFileState(Files.size(path), Files.getLastModifiedTime(path).toMillis());
        }

        @Override
        public boolean equals(final Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof RestoreFileState)) {
                return false;
            }
            final RestoreFileState that = (RestoreFileState) other;
            return size == that.size && modifiedTimeMillis == that.modifiedTimeMillis;
        }

        @Override
        public int hashCode() {
            int result = Long.hashCode(size);
            result = 31 * result + Long.hashCode(modifiedTimeMillis);
            return result;
        }

        @Override
        public String toString() {
            return String.format("RestoreFileState{size=%d, modifiedTimeMillis=%d}", size, modifiedTimeMillis);
        }
    }

    private List<String> getMissingCandidatePaths(final List<String> candidatePaths) {
        final List<String> missingPaths = new ArrayList<>();
        if (CollectionUtils.isEmpty(candidatePaths)) {
            return missingPaths;
        }
        for (final String candidatePath : candidatePaths) {
            if (StringUtils.isBlank(candidatePath) || !Files.isDirectory(Paths.get(candidatePath))) {
                missingPaths.add(candidatePath);
            }
        }
        return missingPaths;
    }

    private List<String> getMissingRequiredFiles(final List<String> requiredFiles) {
        final List<String> missingFiles = new ArrayList<>();
        if (CollectionUtils.isEmpty(requiredFiles)) {
            return missingFiles;
        }
        for (final String requiredFile : requiredFiles) {
            if (StringUtils.isBlank(requiredFile) || !Files.isRegularFile(Paths.get(requiredFile))) {
                missingFiles.add(requiredFile);
            }
        }
        return missingFiles;
    }

    private boolean sleepBeforeNextDiscoveryAttempt(final String backupId) {
        try {
            Thread.sleep(RESTORE_PATH_DISCOVERY_INTERVAL_MS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Interrupted while waiting for NetBackup restore path discovery. backupId=[{}]", backupId);
            return false;
        }
    }

    private boolean isValidRestoreCandidate(final Path path) {
        if (path == null || !Files.isDirectory(path)
                || !TIMESTAMP_DIR_PATTERN.matcher(path.getFileName().toString()).matches()) {
            return false;
        }
        if (!Files.isRegularFile(path.resolve("domain-config.xml"))) {
            return false;
        }
        if (!Files.isRegularFile(path.resolve("domblklist.xml"))) {
            return false;
        }
        try (Stream<Path> children = Files.list(path)) {
            return children.anyMatch(child -> Files.isRegularFile(child) && isDiskArtifact(child.getFileName().toString()));
        } catch (IOException e) {
            logger.debug("Failed to inspect NetBackup restore candidate path [{}].", path, e);
            return false;
        }
    }

    private boolean isDiskArtifact(final String fileName) {
        return StringUtils.startsWith(fileName, "root.")
                || StringUtils.startsWith(fileName, "datadisk.")
                || StringUtils.endsWith(fileName, ".qcow2")
                || StringUtils.endsWith(fileName, ".raw")
                || StringUtils.endsWith(fileName, ".rbdiff");
    }
}
