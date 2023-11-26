/*
 * Copyright 2023 Marco Collovati, Dario Götze
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.mcollovati.quarkus.hilla.deployment;

import java.util.LinkedHashMap;
import java.util.function.UnaryOperator;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

public final class TestUtils {

    static final User ANONYMOUS = new User(null, null);
    static final User ADMIN = new User("admin", "admin");
    static final User USER = new User("user", "user");
    static final User GUEST = new User("guest", "guest");

    static Response givenEndpointRequest(String endpointName, String methodName) {
        return givenEndpointRequest(endpointName, methodName, new Parameters(), UnaryOperator.identity());
    }

    static Response givenEndpointRequest(
            String endpointName, String methodName, UnaryOperator<RequestSpecification> customizer) {
        return givenEndpointRequest(endpointName, methodName, new Parameters(), customizer);
    }

    static Response givenEndpointRequest(String endpointName, String methodName, Parameters parameters) {
        return givenEndpointRequest(endpointName, methodName, parameters, UnaryOperator.identity());
    }

    static Response givenEndpointRequest(
            String endpointName,
            String methodName,
            Parameters parameters,
            UnaryOperator<RequestSpecification> customizer) {
        RequestSpecification specs = RestAssured.given()
                .contentType(ContentType.JSON)
                .cookie("csrfToken", "CSRF_TOKEN")
                .header("X-CSRF-Token", "CSRF_TOKEN")
                .body(parameters.params)
                .basePath("/connect");
        specs = customizer.apply(specs);
        return specs.when().post("{endpointName}/{methodName}", endpointName, methodName);
    }

    public static class Parameters {

        private final LinkedHashMap<String, Object> params = new LinkedHashMap<>();

        public Parameters add(String name, Object value) {
            params.put(name, value);
            return this;
        }

        public static Parameters param(String name, Object value) {
            Parameters parameters = new Parameters();
            parameters.add(name, value);
            return parameters;
        }
    }

    static final class User {
        final String username;
        final String pwd;

        User(String username, String pwd) {
            this.username = username;
            this.pwd = pwd;
        }
    }
}
