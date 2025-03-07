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

package com.google.cloud.broker.apps.brokerserver.validation;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import com.google.cloud.broker.settings.AppSettings;
import com.google.cloud.broker.settings.SettingsOverride;
import com.typesafe.config.ConfigFactory;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.*;

public class ProxyUserValidationTest {

    private static final String ALICE = "alice@EXAMPLE.COM";
    private static final String BOB = "bob@EXAMPLE.COM";
    private static final String CHARLIE = "charlie@EXAMPLE.COM";
    private static final String HIVE = "hive/testhost@EXAMPLE.COM";
    private static final String PRESTO = "presto/testhost@EXAMPLE.COM";
    private static final String SPARK = "spark/testhost@EXAMPLE.COM";
    private static final String OOZIE = "oozie/testhost@EXAMPLE.COM";
    private static final String STORM = "storm/testhost@EXAMPLE.COM";
    private static final String SOLR = "solr/testhost@EXAMPLE.COM";
    private static final String GROUP_DATASCIENCE = "datascience";
    private static final String GROUP_FINANCE = "finance";
    private static final String GSUITE_DOMAIN = System.getProperty("gsuite-domain");
    private static final Object proxyUsers = ConfigFactory.parseString(
    AppSettings.PROXY_USERS + "=[" +
            "{" +
                "proxy=\"" + PRESTO + "\"," +
                "users=[\"*\"]" +
            "}," +
            "{" +
                "proxy=\"" + STORM + "\"," +
                "users=[\"alice@" + GSUITE_DOMAIN + "\", \"bob@" + GSUITE_DOMAIN + "\"]" +
            "}," +
            "{" +
                "proxy=\"" + OOZIE + "\"," +
                "groups=[\"*\"]" +
            "}," +
            "{" +
                "proxy=\"" + HIVE + "\"," +
                "groups=[\"" + GROUP_DATASCIENCE + "@" + GSUITE_DOMAIN + "\"]" +
            "}," +
            "{" +
                "proxy=\"" + SOLR + "\"," +
                "groups=[\"" + GROUP_FINANCE + "@" + GSUITE_DOMAIN + "\"]" +
            "}," +
        "]"
        ).getAnyRef(AppSettings.PROXY_USERS);
    private static final Object userMappingRules = ConfigFactory.parseString(
    "rules=[" +
            "{" +
                "if: \"true\"," +
                "then: \"primary + '@" + GSUITE_DOMAIN + "'\"" +
            "}," +
        "]"
        ).getAnyRef("rules");

    @ClassRule
    public static SettingsOverride settingsOverride = new SettingsOverride(Map.of(
        AppSettings.PROXY_USERS, proxyUsers,
        AppSettings.PROVIDER_BACKEND, "com.google.cloud.broker.apps.brokerserver.accesstokens.providers.ServiceAccountProvider",
        AppSettings.DATABASE_BACKEND, "com.google.cloud.broker.database.backends.DummyDatabaseBackend",
        AppSettings.ENCRYPTION_BACKEND, "com.google.cloud.broker.encryption.backends.DummyEncryptionBackend",
        AppSettings.USER_MAPPING_RULES, userMappingRules
    ));

    @Test
    public void testRequireProperty() {
        GrpcRequestValidation.validateParameterNotEmpty("my-param", "Request must provide `%s`");
        try {
            GrpcRequestValidation.validateParameterNotEmpty("my-param", "");
            fail();
        } catch (StatusRuntimeException e) {
            assertEquals(Status.INVALID_ARGUMENT.getCode(), e.getStatus().getCode());
            assertEquals("Request must provide `my-param`", e.getStatus().getDescription());
        }
    }

    @Test
    public void testValidateImpersonatorInvalid() {
        String[] users = {ALICE, BOB, CHARLIE};
        for (String user : users) {
            try {
                ProxyUserValidation.validateImpersonator(SPARK, user);
                fail();
            } catch (StatusRuntimeException e) {
                assertEquals(Status.PERMISSION_DENIED.getCode(), e.getStatus().getCode());
                assertEquals(String.format("Impersonation of `%s` by `spark/testhost@EXAMPLE.COM` is not allowed", user), e.getStatus().getDescription());
            }
        }
    }

    @Test
    public void testValidateImpersonatorByUserAllowlist() {
        // Wildcard
        ProxyUserValidation.validateImpersonator(PRESTO, ALICE);
        ProxyUserValidation.validateImpersonator(PRESTO, BOB);
        ProxyUserValidation.validateImpersonator(PRESTO, CHARLIE);

        // Specific users
        ProxyUserValidation.validateImpersonator(STORM, ALICE);
        ProxyUserValidation.validateImpersonator(STORM, BOB);
        try {
            ProxyUserValidation.validateImpersonator(STORM, CHARLIE);
            fail();
        } catch (StatusRuntimeException e) {
            assertEquals(Status.PERMISSION_DENIED.getCode(), e.getStatus().getCode());
            assertEquals("Impersonation of `charlie@EXAMPLE.COM` by `storm/testhost@EXAMPLE.COM` is not allowed", e.getStatus().getDescription());
        }
    }

    @Test
    public void testValidateImpersonatorByGroupAllowlist() {
        // Wildcard
        ProxyUserValidation.validateImpersonator(OOZIE, ALICE);
        ProxyUserValidation.validateImpersonator(OOZIE, BOB);
        ProxyUserValidation.validateImpersonator(OOZIE, CHARLIE);

        // Specific group that only Alice is member of
        ProxyUserValidation.validateImpersonator(HIVE, ALICE);
        String[] someUsers = {BOB, CHARLIE};
        for (String user : someUsers) {
            try {
                ProxyUserValidation.validateImpersonator(HIVE, user);
                fail();
            } catch (StatusRuntimeException e) {
                assertEquals(Status.PERMISSION_DENIED.getCode(), e.getStatus().getCode());
                assertEquals(String.format("Impersonation of `%s` by `hive/testhost@EXAMPLE.COM` is not allowed", user), e.getStatus().getDescription());
            }
        }

        // Specific group that no one is member of
        String[] allUsers = {ALICE, BOB, CHARLIE};
        for (String user : allUsers) {
            try {
                ProxyUserValidation.validateImpersonator(SOLR, user);
                fail();
            } catch (StatusRuntimeException e) {
                assertEquals(Status.PERMISSION_DENIED.getCode(), e.getStatus().getCode());
                assertEquals(String.format("Impersonation of `%s` by `solr/testhost@EXAMPLE.COM` is not allowed", user), e.getStatus().getDescription());
            }
        }
    }

}