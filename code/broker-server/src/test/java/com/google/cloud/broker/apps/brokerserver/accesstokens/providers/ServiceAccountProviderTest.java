// Copyright 2020 Google LLC
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
// http://www.apache.org/licenses/LICENSE-2.0
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.cloud.broker.apps.brokerserver.accesstokens.providers;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import com.google.common.base.CharMatcher;
import com.typesafe.config.ConfigFactory;
import org.junit.*;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import com.google.cloud.broker.settings.SettingsOverride;
import com.google.cloud.broker.usermapping.KerberosUserMapper;
import com.google.cloud.broker.settings.AppSettings;
import com.google.cloud.broker.apps.brokerserver.accesstokens.AccessToken;


public class ServiceAccountProviderTest {

    private static final List<String> SCOPES = List.of("https://www.googleapis.com/auth/devstorage.read_write");
    private static final String projectId = AppSettings.getInstance().getString(AppSettings.GCP_PROJECT);
    private static final Object rules = ConfigFactory.parseString(
    "rules=[" +
            "{" +
                "if: \"true\"," +
                "then: \"primary + '-shadow@" + projectId + ".iam.gserviceaccount.com'\"" +
            "}," +
        "]"
        ).getAnyRef("rules");

    @ClassRule
    public static SettingsOverride settingsOverride = new SettingsOverride(Map.of(
        AppSettings.USER_MAPPING_RULES, rules
    ));

    @Test
    public void testSuccess() {
        ServiceAccountProvider provider = new ServiceAccountProvider();
        KerberosUserMapper mapper = new KerberosUserMapper();
        AccessToken accessToken = provider.getAccessToken(mapper.map("alice@EXAMPLE.COM"), SCOPES);
        assertTrue(accessToken.getValue().startsWith("ya29."));
        assertEquals(1024, accessToken.getValue().getBytes(StandardCharsets.UTF_8).length);
        assertTrue(accessToken.getExpiresAt() > 0);
    }

    @Test
    public void testUnauthorized() {
        ServiceAccountProvider provider = new ServiceAccountProvider();
        KerberosUserMapper mapper = new KerberosUserMapper();
        try {
            provider.getAccessToken(mapper.map("bob@EXAMPLE.COM"), SCOPES);
            fail("StatusRuntimeException not thrown");
        } catch (StatusRuntimeException e) {
            assertEquals(Status.PERMISSION_DENIED.getCode(), e.getStatus().getCode());
        }
    }

}
