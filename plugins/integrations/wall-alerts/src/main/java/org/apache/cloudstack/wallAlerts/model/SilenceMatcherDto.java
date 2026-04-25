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

import com.fasterxml.jackson.annotation.JsonProperty;

public class SilenceMatcherDto {
    @JsonProperty("name")
    private String name;

    @JsonProperty("value")
    private String value;

    @JsonProperty("isRegex")
    private Boolean isRegex; // null이면 false 취급

    @JsonProperty("isEqual")
    private Boolean isEqual; // null이면 true 취급

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }

    public Boolean getIsRegex() {
        return isRegex;
    }

    public Boolean getIsEqual() {
        return isEqual;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public void setValue(final String value) {
        this.value = value;
    }

    public void setIsRegex(final Boolean isRegex) {
        this.isRegex = isRegex;
    }

    public void setIsEqual(final Boolean isEqual) {
        this.isEqual = isEqual;
    }
}
