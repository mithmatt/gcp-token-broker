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

package com.google.cloud.broker.client.hadoop.fs;

import javax.security.auth.Subject;

import java.io.IOException;

import com.google.cloud.broker.client.utils.SpnegoUtilsTest;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import io.grpc.testing.GrpcCleanupRule;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.Text;
import com.google.cloud.hadoop.fs.gcs.GoogleHadoopFileSystem;

import static org.junit.Assert.*;
import org.apache.hadoop.security.UserGroupInformation;
import org.junit.*;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import com.google.cloud.broker.client.utils.GrpcUtils;
import com.google.cloud.broker.authentication.backends.FakeKDC;
import static com.google.cloud.broker.client.hadoop.fs.TestingTools.*;

// Classes dynamically generated by protobuf-maven-plugin:
import com.google.cloud.broker.apps.brokerserver.protobuf.GetSessionTokenRequest;
import com.google.cloud.broker.apps.brokerserver.protobuf.GetSessionTokenResponse;


@RunWith(PowerMockRunner.class)
@PowerMockIgnore({"com.sun.org.apache.xerces.*", "javax.xml.*", "javax.activation.*", "org.xml.*", "org.w3c.*", "javax.crypto.*", "javax.net.ssl.*", "javax.security.*", "org.ietf.jgss.*"})
@PrepareForTest({GrpcUtils.class})  // Classes to be mocked
public class BrokerTokenIdentifierTest {

    private static FakeKDC fakeKDC;

    @Rule
    public static final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();

    @BeforeClass
    public static void setUpClass() {
        TestingTools.initHadoop();
        fakeKDC = new FakeKDC(REALM);
        fakeKDC.start();
        fakeKDC.createPrincipal(ALICE);
        fakeKDC.createPrincipal(BROKER);
    }

    @AfterClass
    public static void tearDownClass() {
        if (fakeKDC != null) {
            fakeKDC.stop();
        }
    }

    public String getSessionToken(Configuration conf) {
        BrokerDelegationTokenBinding binding = new BrokerDelegationTokenBinding();
        Text service = new Text(MOCK_BUCKET);
        Text owner = new Text(ALICE);
        Text renewer = new Text(YARN);
        binding.bindToFileSystem(new GoogleHadoopFileSystem(), service);
        BrokerTokenIdentifier identifier = new BrokerTokenIdentifier(conf, owner, renewer, null, service);
        return identifier.getSessionToken();
    }

    private static class FakeServer extends TestingTools.FakeBrokerImpl {

        FakeServer(FakeKDC fakeKDC) {
            super(fakeKDC);
        }

        @Override
        public void getSessionToken(GetSessionTokenRequest request, StreamObserver<GetSessionTokenResponse> responseObserver) {
            try {
                String authenticatedUser = authenticateUser();
                GetSessionTokenResponse response = GetSessionTokenResponse.newBuilder()
                    .setSessionToken("FakeSessionToken/AuthenticatedUser=" + authenticatedUser + ";Owner=" + request.getOwner() + ";Target=" + request.getTarget())
                    .build();
                responseObserver.onNext(response);
                responseObserver.onCompleted();
            }
            catch (StatusRuntimeException e) {throw new RuntimeException(e);}
        }
    }

    @Test
    public void testGetSessionTokenProviderRefreshWhileNotLoggedIn() throws Exception {
        try {
            Configuration conf = TestingTools.getBrokerConfig();
            getSessionToken(conf);
            fail();
        } catch (Exception e) {
            assertEquals(RuntimeException.class, e.getClass());
            Assert.assertEquals(
                "Failed creating a SPNEGO token. Make sure that you have run kinit and that your Kerberos configuration is correct. See the full Kerberos error message: " + SpnegoUtilsTest.TGT_ERROR,
                e.getCause().getMessage()
            );
        }
    }

    @Test
    public void testGetSessionToken() throws IOException {
        TestingTools.startServer(new FakeServer(fakeKDC), grpcCleanup);
        Configuration conf = TestingTools.getBrokerConfig();
        Subject alice = fakeKDC.login(ALICE);
        UserGroupInformation.loginUserFromSubject(alice);
        String token = getSessionToken(conf);
        assertEquals("FakeSessionToken/AuthenticatedUser=" + ALICE + ";Owner=" + ALICE + ";Target=" + MOCK_BUCKET, token);
        UserGroupInformation.setLoginUser(null);
    }

    /**
     * Same as testGetSessionToken but with access boundary disabled
     */
    @Test
    public void testGetSessionTokenWithoutAccessBoundary() throws IOException {
        TestingTools.startServer(new FakeServer(fakeKDC), grpcCleanup);
        Configuration conf = TestingTools.getBrokerConfig();
        conf.set("gcp.token.broker.access.boundary.enabled", "false");
        Subject alice = fakeKDC.login(ALICE);
        UserGroupInformation.loginUserFromSubject(alice);
        String token = getSessionToken(conf);
        assertEquals("FakeSessionToken/AuthenticatedUser=" + ALICE + ";Owner=" + ALICE + ";Target=", token);
        UserGroupInformation.setLoginUser(null);
    }
}
