package org.acme.hilla.test.extension.deployment;

import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.acme.hilla.test.extension.deployment.endpoints.TestEndpoint;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.hamcrest.CoreMatchers.equalTo;

class EndpointControllerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(TestEndpoint.class));

    @Test
    void testInvokeEndpoints() {
        String msg = "A text message";
        givenEndpointRequest().body("{ \"message\": \"" + msg + "\" }").when()
                .post("/connect/TestEndpoint/echo").then().statusCode(200)
                .body(equalTo("\"" + msg + "\""));
    }

    private static RequestSpecification givenEndpointRequest() {
        return RestAssured.given().contentType(ContentType.JSON)
                .cookie("csrfToken", "CSRF_TOKEN")
                .header("X-CSRF-Token", "CSRF_TOKEN");
    }

}
