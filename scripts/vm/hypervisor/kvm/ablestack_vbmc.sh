#!/bin/bash

# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.

case "$1" in
    start)
        # open a port on the firewall
        firewall-cmd --permanent --zone=public --add-port=${3}/udp > /dev/null 2>&1
        firewall-cmd --reload > /dev/null 2>&1

        # 'vbmcd' Retrieve the PID of a process
        pid=$(pgrep -a vbmcd)

        # 'vbmcd' Run commands only when no process is running
        if [ -z "$pid" ]; then
            # run vbmc daemon
            vbmcd > /dev/null 2>&1
        fi

        vbmc add ${2} --port ${3} --username ablecloud --password Ablecloud1!
        vbmc start ${2}
        ;;
    delete)
        vbmc delete ${2}
        # close a port on the firewall
        firewall-cmd --permanent --zone=public --remove-port=${3}/udp > /dev/null 2>&1
        firewall-cmd --reload > /dev/null 2>&1
        ;;
    *)
        echo "ERROR"
        ;;
esac