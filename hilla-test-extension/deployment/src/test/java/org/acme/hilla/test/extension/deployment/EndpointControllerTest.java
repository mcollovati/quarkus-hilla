package org.acme.hilla.test.extension.deployment;

import java.util.LinkedHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.acme.hilla.test.extension.deployment.endpoints.TestEndpoint;
import org.hamcrest.CoreMatchers;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;

class EndpointControllerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(TestEndpoint.class));

    @Test
    void invokeEndpoint_singleSimpleParameter() {
        String msg = "A text message";
        givenEndpointRequest("TestEndpoint", "echo",
                params -> params.accept("message", msg)).then().assertThat()
                .statusCode(200).and().body(equalTo("\"" + msg + "\""));
    }

    @Test
    void invokeEndpoint_singleComplexParameter() {
        String msg = "A text message";
        TestEndpoint.Pojo pojo = new TestEndpoint.Pojo(10, msg);
        givenEndpointRequest("TestEndpoint", "pojo",
                params -> params.accept("pojo", pojo)).then().assertThat()
                .statusCode(200).and().body("number", equalTo(100)).and()
                .body("text", equalTo(msg + msg));
    }

    @Test
    void invokeEndpoint_multipleParameters() {
        givenEndpointRequest("TestEndpoint", "calculate", params -> {
            params.accept("operator", "+");
            params.accept("a", 10);
            params.accept("b", 20);
        }).then().assertThat().statusCode(200).and().body(equalTo("30"));
    }

    @Test
    void invokeEndpoint_wrongParametersOrder_badRequest() {
        givenEndpointRequest("TestEndpoint", "calculate", params -> {
            params.accept("a", 10);
            params.accept("operator", "+");
            params.accept("b", 20);
        }).then().assertThat().statusCode(400).and()
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
        givenEndpointRequest("TestEndpoint", "calculate", params -> {
            params.accept("operator", "+");
        }).then().assertThat().statusCode(400).and().body("message",
                CoreMatchers.allOf(
                        containsString("Incorrect number of parameters"),
                        containsString("'TestEndpoint'"),
                        containsString("'calculate'"),
                        containsString("expected: 3, got: 1")));
    }

    @Test
    void invokeEndpoint_wrongEndpointName_notFound() {
        givenEndpointRequest("NotExistingTestEndpoint", "calculate", params -> {
            params.accept("operator", "+");
        }).then().assertThat().statusCode(404);
    }

    @Test
    void invokeEndpoint_wrongMethodName_notFound() {
        givenEndpointRequest("TestEndpoint", "notExistingMethod", params -> {
            params.accept("operator", "+");
        }).then().assertThat().statusCode(404);
    }

    @Test
    void invokeEndpoint_emptyMethodName_notFound() {
        givenEndpointRequest("TestEndpoint", "", params -> {
            params.accept("operator", "+");
        }).then().assertThat().statusCode(404);
    }

    @Test
    void invokeEndpoint_missingMethodName_notFound() {
        RestAssured.given().contentType(ContentType.JSON)
                .cookie("csrfToken", "CSRF_TOKEN")
                .header("X-CSRF-Token", "CSRF_TOKEN").basePath("/connect")
                .when().post("TestEndpoint").then().assertThat()
                .statusCode(404);
    }

    private static Response givenEndpointRequest(String endpointName,
            String methodName,
            Consumer<BiConsumer<String, Object>> parameters) {
        LinkedHashMap<String, Object> orderedParams = new LinkedHashMap<>();
        parameters.accept(orderedParams::put);
        return RestAssured.given().contentType(ContentType.JSON)
                .cookie("csrfToken", "CSRF_TOKEN")
                .header("X-CSRF-Token", "CSRF_TOKEN").body(orderedParams)
                .basePath("/connect").when()
                .post("{endpointName}/{methodName}", endpointName, methodName);
    }

}
