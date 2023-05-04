package org.acme.hilla.test.extension.deployment;

import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.acme.hilla.test.extension.deployment.TestUtils.Parameters;
import org.acme.hilla.test.extension.deployment.endpoints.TestEndpoint;
import org.hamcrest.CoreMatchers;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.acme.hilla.test.extension.deployment.TestUtils.givenEndpointRequest;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;

class EndpointControllerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(TestUtils.class, TestEndpoint.class));

    @Test
    void invokeEndpoint_singleSimpleParameter() {
        String msg = "A text message";
        givenEndpointRequest("TestEndpoint", "echo",
                Parameters.param("message", msg)).then().assertThat()
                .statusCode(200).and().body(equalTo("\"" + msg + "\""));
    }

    @Test
    void invokeEndpoint_singleComplexParameter() {
        String msg = "A text message";
        TestEndpoint.Pojo pojo = new TestEndpoint.Pojo(10, msg);
        givenEndpointRequest("TestEndpoint", "pojo",
                Parameters.param("pojo", pojo)).then().assertThat()
                .statusCode(200).and().body("number", equalTo(100)).and()
                .body("text", equalTo(msg + msg));
    }

    @Test
    void invokeEndpoint_multipleParameters() {
        givenEndpointRequest("TestEndpoint", "calculate",
                Parameters.param("operator", "+").add("a", 10).add("b", 20))
                .then().assertThat().statusCode(200).and().body(equalTo("30"));
    }

    @Test
    void invokeEndpoint_wrongParametersOrder_badRequest() {
        givenEndpointRequest("TestEndpoint", "calculate",
                Parameters.param("a", 10).add("operator", "+").add("b", 20))
                .then().assertThat().statusCode(400).and()
                .body("type", equalTo(
                        "dev.hilla.exception.EndpointValidationException"))
                .and()
                .body("message",
                        CoreMatchers.allOf(containsString("Validation error"),
                                containsString("'TestEndpoint'"),
                                containsString("'calculate'")))
                .body("validationErrorData[0].parameterName",
                        equalTo("operator"));
    }

    @Test
    void invokeEndpoint_wrongNumberOfParameters_badRequest() {
        givenEndpointRequest("TestEndpoint", "calculate",
                Parameters.param("operator", "+")).then().assertThat()
                .statusCode(400).and().body("message",
                        CoreMatchers.allOf(
                                containsString(
                                        "Incorrect number of parameters"),
                                containsString("'TestEndpoint'"),
                                containsString("'calculate'"),
                                containsString("expected: 3, got: 1")));
    }

    @Test
    void invokeEndpoint_wrongEndpointName_notFound() {
        givenEndpointRequest("NotExistingTestEndpoint", "calculate",
                Parameters.param("operator", "+")).then().assertThat()
                .statusCode(404);
    }

    @Test
    void invokeEndpoint_wrongMethodName_notFound() {
        givenEndpointRequest("TestEndpoint", "notExistingMethod",
                Parameters.param("operator", "+")).then().assertThat()
                .statusCode(404);
    }

    @Test
    void invokeEndpoint_emptyMethodName_notFound() {
        givenEndpointRequest("TestEndpoint", "",
                Parameters.param("operator", "+")).then().assertThat()
                .statusCode(404);
    }

    @Test
    void invokeEndpoint_missingMethodName_notFound() {
        RestAssured.given().contentType(ContentType.JSON)
                .cookie("csrfToken", "CSRF_TOKEN")
                .header("X-CSRF-Token", "CSRF_TOKEN").basePath("/connect")
                .when().post("TestEndpoint").then().assertThat()
                .statusCode(404);
    }

}
