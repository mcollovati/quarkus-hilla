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

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import com.vaadin.hilla.EndpointController;
import com.vaadin.hilla.exception.EndpointValidationException;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import org.assertj.core.api.Assertions;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.github.mcollovati.quarkus.hilla.deployment.endpoints.Pojo;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;

import static com.github.mcollovati.quarkus.hilla.deployment.TestUtils.givenEndpointRequest;

abstract class AbstractEndpointControllerTest {
    protected abstract String getEndpointName();

    @Test
    void invokeEndpoint_singleSimpleParameter() {
        String msg = "A text message";
        givenEndpointRequest(getEndpointPrefix(), getEndpointName(), "echo", TestUtils.Parameters.param("message", msg))
                .then()
                .assertThat()
                .statusCode(200)
                .and()
                .body(equalTo("\"" + msg + "\""));
    }

    @Test
    void invokeEndpoint_singleComplexParameter() {
        String msg = "A text message";
        Pojo pojo = new Pojo(10, msg);
        givenEndpointRequest(getEndpointPrefix(), getEndpointName(), "pojo", TestUtils.Parameters.param("pojo", pojo))
                .then()
                .assertThat()
                .statusCode(200)
                .and()
                .body("number", equalTo(100))
                .and()
                .body("text", equalTo(msg + msg));
    }

    @Test
    void invokeEndpoint_multipleParameters() {
        givenEndpointRequest(
                        getEndpointPrefix(),
                        getEndpointName(),
                        "calculate",
                        TestUtils.Parameters.param("operator", "+").add("a", 10).add("b", 20))
                .then()
                .assertThat()
                .statusCode(200)
                .and()
                .body(equalTo("30"));
    }

    @Test
    @Disabled("Order does not matter anymore")
    void invokeEndpoint_wrongParametersOrder_badRequest() {
        givenEndpointRequest(
                        getEndpointPrefix(),
                        getEndpointName(),
                        "calculate",
                        TestUtils.Parameters.param("a", 10).add("operator", "+").add("b", 20))
                .then()
                .assertThat()
                .statusCode(400)
                .and()
                .body("type", equalTo(EndpointValidationException.class.getName()))
                .and()
                .body(
                        "message",
                        CoreMatchers.allOf(
                                containsString("Validation error"),
                                containsString("'" + getEndpointName() + "'"),
                                containsString("'calculate'")))
                .body("validationErrorData[0].parameterName", equalTo("operator"));
    }

    @Test
    void invokeEndpoint_wrongNumberOfParameters_badRequest() {
        givenEndpointRequest(
                        getEndpointPrefix(),
                        getEndpointName(),
                        "calculate",
                        TestUtils.Parameters.param("operator", "+"))
                .then()
                .assertThat()
                .statusCode(400)
                .and()
                .body(
                        "message",
                        CoreMatchers.allOf(
                                containsString("Incorrect number of parameters"),
                                containsString("'" + getEndpointName() + "'"),
                                containsString("'calculate'"),
                                containsString("expected: 3, got: 1")));
    }

    @Test
    void invokeEndpoint_wrongEndpointName_notFound() {
        givenEndpointRequest(
                        getEndpointPrefix(),
                        "NotExistingTestEndpoint",
                        "calculate",
                        TestUtils.Parameters.param("operator", "+"))
                .then()
                .assertThat()
                .statusCode(404);
    }

    @Test
    void invokeEndpoint_wrongMethodName_notFound() {
        givenEndpointRequest(
                        getEndpointPrefix(),
                        getEndpointName(),
                        "notExistingMethod",
                        TestUtils.Parameters.param("operator", "+"))
                .then()
                .assertThat()
                .statusCode(404);
    }

    @Test
    void invokeEndpoint_emptyMethodName_notFound() {
        givenEndpointRequest(getEndpointPrefix(), getEndpointName(), "", TestUtils.Parameters.param("operator", "+"))
                .then()
                .assertThat()
                .statusCode(404);
    }

    @Test
    void invokeEndpoint_missingMethodName_notFound() {
        RestAssured.given()
                .contentType(ContentType.JSON)
                .cookie("csrfToken", "CSRF_TOKEN")
                .header("X-CSRF-Token", "CSRF_TOKEN")
                .basePath(getEndpointPrefix())
                .when()
                .post(getEndpointName())
                .then()
                .assertThat()
                .statusCode(404);
    }

    @Test
    void invokeEndpoint_multipart_fileTransfer() throws IOException {
        Path tempFile = Files.createTempFile("upload", "txt");
        Files.writeString(tempFile, "hello world");
        ExtractableResponse<Response> res = RestAssured.given()
                .contentType(ContentType.MULTIPART)
                .multiPart("/file", tempFile.toFile())
                .multiPart(
                        EndpointController.BODY_PART_NAME,
                        """
                        { "info": { "id": "UPLOAD-1", "date": "2025-02-02" } }
                        """)
                .cookie("csrfToken", "CSRF_TOKEN")
                .header("X-CSRF-Token", "CSRF_TOKEN")
                .basePath(getEndpointPrefix())
                .when()
                .post("UploadEndpoint/upload")
                .then()
                .assertThat()
                .statusCode(200)
                .body("info.id", equalTo("UPLOAD-1"))
                .body("info.date", equalTo("2025-02-02"))
                .extract();
        Path uploadedFile = Path.of(URI.create(res.body().path("uri")));
        Assertions.assertThat(uploadedFile).hasSameTextualContentAs(tempFile);
    }

    protected String getEndpointPrefix() {
        return TestUtils.DEFAULT_PREFIX;
    }
}
