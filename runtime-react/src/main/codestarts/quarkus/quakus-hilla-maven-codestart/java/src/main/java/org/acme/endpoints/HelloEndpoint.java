package org.acme.endpoints;

import com.vaadin.flow.server.auth.AnonymousAllowed;
import dev.hilla.Endpoint;

@Endpoint
@AnonymousAllowed
public class HelloEndpoint {

    /**
     * A simple endpoint method that returns a greeting
     * to a person whose name is given as a parameter.
     * <p>
     * Both the parameter and the return value are
     * automatically considered to be Nonnull, due to
     * existence of <code>package-info.java</code>
     * in the same package that defines a
     * <code>@org.springframework.lang.NonNullApi</code>
     * for the current package.
     * <p>
     * Note that you can override the default Nonnull
     * behavior by annotating the parameter with
     * <code>@dev.hilla.Nullable</code>.
     *
     * @param name that assumed to be nonnull
     * @return a nonnull greeting
     */
    public String sayHello(String name) {
        if (name.isEmpty()) {
            return "Hello stranger";
        } else {
            return "Hello " + name;
        }
    }
}
