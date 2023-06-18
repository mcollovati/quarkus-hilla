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

import static com.github.mcollovati.quarkus.hilla.deployment.TestUtils.givenEndpointRequest;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;

import com.github.mcollovati.quarkus.hilla.deployment.TestUtils.Parameters;
import com.github.mcollovati.quarkus.hilla.deployment.endpoints.TestEndpoint;
import dev.hilla.exception.EndpointValidationException;
import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.hamcrest.CoreMatchers;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class EndpointControllerTest {

    private static final String ENDPOINT_NAME = TestEndpoint.class.getSimpleName();

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class).addClasses(TestUtils.class, TestEndpoint.class));

    @Test
    void invokeEndpoint_singleSimpleParameter() {
        String msg = "A text message";
        givenEndpointRequest(ENDPOINT_NAME, "echo", Parameters.param("message", msg))
                .then()
                .assertThat()
                .statusCode(200)
                .and()
                .body(equalTo("\"" + msg + "\""));
    }

    @Test
    void invokeEndpoint_singleComplexParameter() {
        String msg = "A text message -----------------------";
        TestEndpoint.Pojo pojo = new TestEndpoint.Pojo(10, msg);
        givenEndpointRequest(ENDPOINT_NAME, "pojo", Parameters.param("pojo", pojo))
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
                        ENDPOINT_NAME,
                        "calculate",
                        Parameters.param("operator", "+").add("a", 10).add("b", 20))
                .then()
                .assertThat()
                .statusCode(200)
                .and()
                .body(equalTo("30"));
    }

    @Test
    void invokeEndpoint_wrongParametersOrder_badRequest() {
        givenEndpointRequest(
                        ENDPOINT_NAME,
                        "calculate",
                        Parameters.param("a", 10).add("operator", "+").add("b", 20))
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
                                containsString("'TestEndpoint'"),
                                containsString("'calculate'")))
                .body("validationErrorData[0].parameterName", equalTo("operator"));
    }

    @Test
    void invokeEndpoint_wrongNumberOfParameters_badRequest() {
        givenEndpointRequest(ENDPOINT_NAME, "calculate", Parameters.param("operator", "+"))
                .then()
                .assertThat()
                .statusCode(400)
                .and()
                .body(
                        "message",
                        CoreMatchers.allOf(
                                containsString("Incorrect number of parameters"),
                                containsString("'TestEndpoint'"),
                                containsString("'calculate'"),
                                containsString("expected: 3, got: 1")));
    }

    @Test
    void invokeEndpoint_wrongEndpointName_notFound() {
        givenEndpointRequest("NotExistingTestEndpoint", "calculate", Parameters.param("operator", "+"))
                .then()
                .assertThat()
                .statusCode(404);
    }

    @Test
    void invokeEndpoint_wrongMethodName_notFound() {
        givenEndpointRequest(ENDPOINT_NAME, "notExistingMethod", Parameters.param("operator", "+"))
                .then()
                .assertThat()
                .statusCode(404);
    }

    @Test
    void invokeEndpoint_emptyMethodName_notFound() {
        givenEndpointRequest(ENDPOINT_NAME, "", Parameters.param("operator", "+"))
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
                .basePath("/connect")
                .when()
                .post(ENDPOINT_NAME)
                .then()
                .assertThat()
                .statusCode(404);
    }
}
