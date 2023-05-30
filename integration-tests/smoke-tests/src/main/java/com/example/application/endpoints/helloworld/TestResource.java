package com.example.application.endpoints.helloworld;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import org.jboss.resteasy.reactive.RestResponse;

@Path("test")
public class TestResource {

    @GET
    public RestResponse<String> test() {
        return RestResponse.ok("test");
    }
}
