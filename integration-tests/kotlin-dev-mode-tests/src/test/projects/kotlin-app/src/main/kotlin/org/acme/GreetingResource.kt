package org.acme

import jakarta.ws.rs.GET
import jakarta.ws.rs.Path

@Path("/greeting")
class GreetingResource {
    @GET
    fun greeting() = "initial"
}
