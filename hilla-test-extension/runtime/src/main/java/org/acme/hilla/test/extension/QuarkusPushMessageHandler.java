package org.acme.hilla.test.extension;

import javax.servlet.ServletContext;
import java.lang.invoke.MethodHandles;
import java.util.UUID;

import dev.hilla.EndpointInvoker;
import dev.hilla.push.PushMessageHandler;

class QuarkusPushMessageHandler extends PushMessageHandler {

    private final String id = UUID.randomUUID().toString();

    /**
     * Creates the instance.
     *
     * @param endpointInvoker the endpoint invoker
     */
    public QuarkusPushMessageHandler(EndpointInvoker endpointInvoker, ServletContext servletContext) {
        super(endpointInvoker);
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(PushMessageHandler.class, MethodHandles.lookup());
            lookup.findVarHandle(PushMessageHandler.class, "servletContext", ServletContext.class)
                    .compareAndSet(this, null, servletContext);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
