package org.acme.hilla.test.extension;

import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;
import java.util.Set;

// TODO: find a way to configure application path based on configuration
@ApplicationPath("/connect")
public class HillaApplication extends Application {

    @Override
    public Set<Class<?>> getClasses() {
        return Set.of(QuarkusEndpointController.class);
    }
}
