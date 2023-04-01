package org.acme.hilla.test.extension;

import javax.annotation.PreDestroy;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.function.Consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.hilla.push.PushEndpoint;
import dev.hilla.push.PushMessageHandler;
import io.quarkus.runtime.ShutdownEvent;
import org.atmosphere.client.TrackMessageSizeInterceptor;
import org.atmosphere.config.service.AtmosphereHandlerService;
import org.atmosphere.cpr.AtmosphereResource;
import org.atmosphere.interceptor.AtmosphereResourceLifecycleInterceptor;
import org.atmosphere.interceptor.SuspendTrackerInterceptor;
import org.atmosphere.util.SimpleBroadcaster;

@AtmosphereHandlerService(path = "/HILLA/push", broadcaster = SimpleBroadcaster.class, interceptors = {
        AtmosphereResourceLifecycleInterceptor.class,
        TrackMessageSizeInterceptor.class, SuspendTrackerInterceptor.class})
public class QuarkusPushEndpoint extends PushEndpoint {

    // Default constructor for Atmosphere
    public QuarkusPushEndpoint() {
    }

    QuarkusPushEndpoint(ObjectMapper objectMapper, PushMessageHandler pushMessageHandler) {
        initInjector(objectMapper, pushMessageHandler);
    }


    @Override
    public void onRequest(AtmosphereResource resource) throws IOException {
        INJECTOR.accept(this);
        super.onRequest(resource);
    }

    private static Consumer<PushEndpoint> INJECTOR;

    private void initInjector(ObjectMapper objectMapper, PushMessageHandler pushMessageHandler) {
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(PushEndpoint.class, MethodHandles.lookup());
            VarHandle objectMapperHandle = lookup.findVarHandle(PushEndpoint.class, "objectMapper", ObjectMapper.class);
            VarHandle pushMessageHandlerHandle = lookup.findVarHandle(PushEndpoint.class, "pushMessageHandler", PushMessageHandler.class);
            INJECTOR = pushEndpoint -> {
                objectMapperHandle.compareAndSet(pushEndpoint, null, objectMapper);
                pushMessageHandlerHandle.compareAndSet(pushEndpoint, null, pushMessageHandler);
            };
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}