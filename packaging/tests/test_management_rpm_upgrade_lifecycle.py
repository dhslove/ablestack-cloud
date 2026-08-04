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

import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


class ManagementRpmUpgradeLifecycleTest(unittest.TestCase):

    def test_management_spec_preserves_update_state(self):
        for spec_path, service in [
                ("packaging/centos8/cloud.spec", "mold"),
                ("packaging/centos7/cloud.spec", "cloudstack-management")]:
            source = (ROOT / spec_path).read_text()
            with self.subTest(spec=spec_path):
                self.assertIn("management_upgrade_state=/run/cloudstack-management-rpm-upgrade", source)
                self.assertIn(f"systemctl is-active --quiet {service}", source)
                self.assertIn(f"systemctl is-enabled --quiet {service}", source)
                self.assertIn('if [ "$1" == "0" ]', source)
                self.assertIn('if [ "$1" == "1" ]', source)
                self.assertIn(f"systemctl start {service}", source)

    def test_diplo_storage_created_columns_converge(self):
        source = (ROOT / "engine/schema/src/main/resources/META-INF/db/schema-Diplo-After.sql").read_text()
        for table in [
                "storage_service_instance",
                "storage_service_protocol",
                "storage_file_share",
                "storage_block_target",
                "storage_access_rule",
                "storage_identity_domain"]:
            with self.subTest(table=table):
                self.assertEqual(source.count(f"'cloud.{table}', 'created', 'created'"), 1)

    def test_branch_release_requires_one_kvm_systemvm_image(self):
        source = (ROOT / ".github/workflows/branch-dev-release.yml").read_text()
        self.assertIn("build-systemvm-kvm:", source)
        self.assertIn("templates: kvm", source)
        self.assertIn("Expected exactly one KVM System VM qcow2.bz2 image", source)
        self.assertNotIn("build-systemvm-vmware", source)


if __name__ == "__main__":
    unittest.main()
