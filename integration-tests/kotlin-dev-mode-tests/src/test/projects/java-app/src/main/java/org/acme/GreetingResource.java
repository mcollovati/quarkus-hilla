package org.acme;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

@Path("/greeting")
public class GreetingResource {

    @GET
    public String greeting() {
        return "initial";
    }
}
