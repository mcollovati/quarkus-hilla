package com.example.application.endpoints.helloworld;

import org.jboss.resteasy.reactive.RestResponse;

import javax.ws.rs.GET;
import javax.ws.rs.Path;

@Path("test")
public class TestResource {

    @GET
    public RestResponse<String> test() {
        return RestResponse.ok("test");
    }

}
