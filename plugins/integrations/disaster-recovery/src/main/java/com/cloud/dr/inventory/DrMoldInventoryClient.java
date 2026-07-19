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

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.cloud.dr.DrResolvedSiteCredential;
import com.cloud.dr.DrSiteCredentialVO;
import com.cloud.dr.health.DrSiteProbeSupport;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class DrMoldInventoryClient {
    private static final int CONNECT_TIMEOUT_MS = 10000;
    private static final int READ_TIMEOUT_MS = 15000;
    private static final String COMMAND_LIST_ZONES = "listZones";
    private static final String COMMAND_LIST_VMWARE_DCS = "listVmwareDcs";
    private static final String COMMAND_LIST_VMS = "listVirtualMachines";

    public List<DrInventoryOption> listZones(DrResolvedSiteCredential credential) {
        JsonObject response = execute(credential, COMMAND_LIST_ZONES, null);
        JsonObject payload = getObjectIgnoreCase(response, "listzonesresponse");
        return toOptions(getArrayIgnoreCase(payload, "zone"), "ZONE");
    }

    public List<DrInventoryOption> listVmwareDatacenters(DrResolvedSiteCredential credential, String zoneExternalId, Long zoneId) {
        Map<String, String> params = new LinkedHashMap<String, String>();
        if (StringUtils.isNotBlank(zoneExternalId)) {
            params.put("zoneid", StringUtils.trim(zoneExternalId));
        } else if (zoneId != null) {
            params.put("zoneid", String.valueOf(zoneId));
        }
        JsonObject response = execute(credential, COMMAND_LIST_VMWARE_DCS, params);
        JsonObject payload = getObjectIgnoreCase(response, "listvmwaredcsresponse");
        JsonArray items = getArrayIgnoreCase(payload, "VMwareDC");
        if (items == null || items.size() == 0) {
            items = getArrayIgnoreCase(payload, "vmwaredc");
        }
        return toOptions(items, "VMWARE_DATACENTER");
    }

    public List<DrInventoryOption> listVirtualMachines(DrResolvedSiteCredential credential, String keyword, String zoneExternalId, Long zoneId) {
        Map<String, String> params = new LinkedHashMap<String, String>();
        params.put("listall", "true");
        params.put("details", "min");
        if (StringUtils.isNotBlank(keyword)) {
            params.put("keyword", StringUtils.trim(keyword));
        }
        if (StringUtils.isNotBlank(zoneExternalId)) {
            params.put("zoneid", StringUtils.trim(zoneExternalId));
        } else if (zoneId != null) {
            params.put("zoneid", String.valueOf(zoneId));
        }
        JsonObject response = execute(credential, COMMAND_LIST_VMS, params);
        JsonObject payload = getObjectIgnoreCase(response, "listvirtualmachinesresponse");
        return toVirtualMachineOptions(getArrayIgnoreCase(payload, "virtualmachine"));
    }

    private JsonObject execute(DrResolvedSiteCredential credential, String command, Map<String, String> additionalParams) {
        try {
            DrSiteCredentialVO credentialVo = credential.getCredential();
            String endpoint = StringUtils.trimToNull(credentialVo.getEndpoint());
            String apiEndpoint = normalizeMoldApiEndpoint(endpoint);
            JsonObject secret = credential.getSecretPayload();
            String apiKey = getSecret(secret, "apiKey");
            String secretKey = getSecret(secret, "secretKey");
            if (StringUtils.isAnyBlank(apiEndpoint, apiKey, secretKey)) {
                throw new InventoryException(0, "Mold API endpoint, API key, and secret key are required");
            }

            Map<String, String> params = new LinkedHashMap<String, String>();
            params.put("command", command);
            params.put("response", "json");
            params.put("apiKey", apiKey);
            if (additionalParams != null) {
                params.putAll(additionalParams);
            }
            String query = DrSiteProbeSupport.buildQuery(params);
            String signature = DrSiteProbeSupport.signCloudStackRequest(params, secretKey);
            String requestUrl = apiEndpoint + "?" + query + "&signature=" + signature;
            HttpURLConnection connection = DrSiteProbeSupport.openConnection(requestUrl, "GET", credentialVo.getTlsVerify(), CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS);
            connection.setRequestProperty("Accept", "application/json");
            int responseCode = connection.getResponseCode();
            String body = DrSiteProbeSupport.readBody(connection);
            if (responseCode < 200 || responseCode >= 300) {
                throw new InventoryException(responseCode, "Mold API returned HTTP " + responseCode);
            }
            JsonElement parsed = JsonParser.parseString(StringUtils.defaultString(body, "{}"));
            return parsed != null && parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
        } catch (InventoryException e) {
            throw e;
        } catch (Exception e) {
            throw new InventoryException(0, "Mold API inventory request failed: " + e.getClass().getSimpleName());
        }
    }

    private String normalizeMoldApiEndpoint(String endpoint) throws Exception {
        String normalized = DrSiteProbeSupport.normalizeEndpoint(endpoint, "http");
        String lower = StringUtils.lowerCase(normalized);
        if (StringUtils.contains(lower, "/client/api")) {
            return normalized;
        }
        return DrSiteProbeSupport.appendPath(normalized, "/client/api");
    }

    private List<DrInventoryOption> toOptions(JsonArray items, String type) {
        List<DrInventoryOption> options = new ArrayList<DrInventoryOption>();
        if (items == null) {
            return options;
        }
        for (JsonElement item : items) {
            if (item == null || !item.isJsonObject()) {
                continue;
            }
            JsonObject object = item.getAsJsonObject();
            DrInventoryOption option = new DrInventoryOption();
            option.setType(type);
            String externalId = firstString(object, "id", "uuid", "externalid");
            String localId = firstNumericString(object, "internalid", "dbid", "dbId", "databaseid");
            option.setId(StringUtils.defaultIfBlank(externalId, firstString(object, "name")));
            option.setName(firstString(object, "name", "displayname", "description", "id"));
            option.setDescription(firstString(object, "description", "displaytext", "vcenter", "path"));
            option.setValue(StringUtils.defaultIfBlank(externalId, localId));
            option.setExternalId(externalId);
            option.setLocalId(localId);
            option.setSelectable(StringUtils.isNotBlank(option.getValue()));
            if (StringUtils.isNotBlank(externalId)) {
                option.putDetail("externalId", externalId);
            }
            if (StringUtils.isNotBlank(localId)) {
                option.putDetail("localId", localId);
            }
            String vcenter = firstString(object, "vcenter", "vcentername", "host");
            if (StringUtils.isNotBlank(vcenter)) {
                option.putDetail("vcenter", vcenter);
            }
            options.add(option);
        }
        return options;
    }

    private List<DrInventoryOption> toVirtualMachineOptions(JsonArray items) {
        List<DrInventoryOption> options = new ArrayList<DrInventoryOption>();
        if (items == null) {
            return options;
        }
        for (JsonElement item : items) {
            if (item == null || !item.isJsonObject()) {
                continue;
            }
            JsonObject object = item.getAsJsonObject();
            String externalId = firstString(object, "id", "uuid");
            String instanceName = firstString(object, "instancename");
            String name = firstString(object, "displayname", "name", "id");
            DrInventoryOption option = new DrInventoryOption();
            option.setType("SOURCE_WORKLOAD");
            option.setId(StringUtils.defaultIfBlank(externalId, instanceName));
            option.setName(name);
            option.setDescription(firstString(object, "displaytext", "zonename", "host"));
            option.setValue(StringUtils.defaultIfBlank(externalId, instanceName));
            option.setExternalId(externalId);
            option.setExternalRef(StringUtils.defaultIfBlank(externalId, instanceName));
            option.setReferenceType("EXTERNAL_REF");
            option.setState(firstString(object, "state"));
            option.setHypervisorType(firstString(object, "hypervisor", "hypervisortype"));
            option.setSelectable(StringUtils.isNotBlank(option.getValue()));
            putDetailIfNotBlank(option, "externalId", externalId);
            putDetailIfNotBlank(option, "instanceName", instanceName);
            putDetailIfNotBlank(option, "name", firstString(object, "name"));
            putDetailIfNotBlank(option, "displayName", firstString(object, "displayname"));
            putDetailIfNotBlank(option, "zoneName", firstString(object, "zonename"));
            putDetailIfNotBlank(option, "hostName", firstString(object, "hostname", "host"));
            putDetailIfNotBlank(option, "account", firstString(object, "account"));
            putDetailIfNotBlank(option, "domain", firstString(object, "domain"));
            options.add(option);
        }
        return options;
    }

    private void putDetailIfNotBlank(DrInventoryOption option, String key, String value) {
        if (StringUtils.isNotBlank(value)) {
            option.putDetail(key, value);
        }
    }

    private JsonObject getObjectIgnoreCase(JsonObject object, String key) {
        JsonElement element = getElementIgnoreCase(object, key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
    }

    private JsonArray getArrayIgnoreCase(JsonObject object, String key) {
        JsonElement element = getElementIgnoreCase(object, key);
        if (element == null) {
            return new JsonArray();
        }
        if (element.isJsonArray()) {
            return element.getAsJsonArray();
        }
        JsonArray array = new JsonArray();
        if (element.isJsonObject()) {
            array.add(element);
        }
        return array;
    }

    private JsonElement getElementIgnoreCase(JsonObject object, String key) {
        if (object == null || StringUtils.isBlank(key)) {
            return null;
        }
        if (object.has(key)) {
            return object.get(key);
        }
        String lowerKey = key.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (StringUtils.equals(entry.getKey().toLowerCase(Locale.ROOT), lowerKey)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String firstString(JsonObject object, String... keys) {
        for (String key : keys) {
            JsonElement element = getElementIgnoreCase(object, key);
            if (element != null && !element.isJsonNull()) {
                String value = StringUtils.trimToNull(element.getAsString());
                if (value != null) {
                    return value;
                }
            }
        }
        return null;
    }

    private String firstNumericString(JsonObject object, String... keys) {
        for (String key : keys) {
            String value = firstString(object, key);
            if (StringUtils.isNotBlank(value) && StringUtils.isNumeric(value)) {
                return value;
            }
        }
        return null;
    }

    private String getSecret(JsonObject secret, String key) {
        JsonElement element = secret != null ? getElementIgnoreCase(secret, key) : null;
        return element != null && !element.isJsonNull() ? StringUtils.trimToNull(element.getAsString()) : null;
    }

    public static class InventoryException extends RuntimeException {
        private final int responseCode;

        public InventoryException(int responseCode, String message) {
            super(message);
            this.responseCode = responseCode;
        }

        public int getResponseCode() {
            return responseCode;
        }
    }
}
