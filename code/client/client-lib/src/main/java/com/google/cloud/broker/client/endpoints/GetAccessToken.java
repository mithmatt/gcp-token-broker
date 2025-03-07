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

package com.google.cloud.broker.client.endpoints;

import java.util.Date;

import com.google.auth.oauth2.AccessToken;

import com.google.cloud.broker.client.connect.BrokerGateway;
import com.google.cloud.broker.client.connect.BrokerServerInfo;

// Classes dynamically generated by protobuf-maven-plugin:
import com.google.cloud.broker.apps.brokerserver.protobuf.GetAccessTokenRequest;
import com.google.cloud.broker.apps.brokerserver.protobuf.GetAccessTokenResponse;

public class GetAccessToken {

    public static AccessToken submitDirectAuth(BrokerServerInfo serverInfo, String owner, Iterable<String> scopes, String target) {
        BrokerGateway gateway = new BrokerGateway(serverInfo);
        try {
            gateway.setSPNEGOToken();
        } catch (Exception e) {
            throw new RuntimeException(
                String.format(
                    "Error while getting SPNEGO token for owner=`%s`, scopes=`%s`, target=`%s`",
                    owner, scopes, target
                ), e
            );
        }
        GetAccessTokenRequest request = GetAccessTokenRequest.newBuilder()
            .addAllScopes(scopes)
            .setOwner(owner)
            .setTarget(target)
            .build();
        GetAccessTokenResponse response = gateway.getStub().getAccessToken(request);
        gateway.getManagedChannel().shutdown();
        String tokenString = response.getAccessToken();
        long expiresAt = response.getExpiresAt();
        return new AccessToken(tokenString, new Date(expiresAt));
    }

    public static AccessToken submitDelegatedAuth(BrokerServerInfo serverInfo, String sessionToken) {
        BrokerGateway gateway = new BrokerGateway(serverInfo);
        gateway.setSessionToken(sessionToken);
        GetAccessTokenRequest request = GetAccessTokenRequest.newBuilder().build();
        GetAccessTokenResponse response = gateway.getStub().getAccessToken(request);
        gateway.getManagedChannel().shutdown();
        String tokenString = response.getAccessToken();
        long expiresAt = response.getExpiresAt();
        return new AccessToken(tokenString, new Date(expiresAt));
    }

}
