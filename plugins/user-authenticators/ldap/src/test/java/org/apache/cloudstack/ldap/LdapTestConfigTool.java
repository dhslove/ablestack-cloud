/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.  The
 * ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.apache.cloudstack.ldap;

import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.framework.config.impl.ConfigDepotImpl;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

public class LdapTestConfigTool {
    private final Map<String, String> overrides = new HashMap<>();
    private final ConfigDepotImpl configDepot = mock(ConfigDepotImpl.class);

    public LdapTestConfigTool() {
        lenient().when(configDepot.getConfigStringValue(anyString(), any(ConfigKey.Scope.class), nullable(Long.class)))
                .thenAnswer(invocation -> overrides.get(invocation.getArgument(0)));
    }

    void overrideConfigValue(LdapConfiguration ldapConfiguration, final String configKeyName, final Object o) throws IllegalAccessException, NoSuchFieldException {
        Field configKey = LdapConfiguration.class.getDeclaredField(configKeyName);
        configKey.setAccessible(true);

        ConfigKey<?> key = (ConfigKey<?>) configKey.get(ldapConfiguration);
        ConfigKey.init(configDepot);
        overrides.put(key.key(), o == null ? null : String.valueOf(o));
    }

    void resetOverrides() {
        overrides.clear();
        ConfigKey.init(null);
    }
}
