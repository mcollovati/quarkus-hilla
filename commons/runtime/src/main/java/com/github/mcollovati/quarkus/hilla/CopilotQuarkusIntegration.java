/*
 * Copyright 2026 Marco Collovati, Dario Götze
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.mcollovati.quarkus.hilla;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.ConversationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.context.SessionScoped;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.inject.Singleton;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.vaadin.flow.server.VaadinContext;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.auth.AnnotatedViewAccessChecker;
import com.vaadin.flow.server.auth.NavigationAccessControl;
import com.vaadin.hilla.BrowserCallable;
import com.vaadin.hilla.Endpoint;
import com.vaadin.hilla.EndpointRegistry;
import io.smallrye.config.SmallRyeConfig;
import org.eclipse.microprofile.config.ConfigProvider;

/**
 * Reflection target for Vaadin Copilot's Spring bridge in Quarkus applications.
 */
public final class CopilotQuarkusIntegration {

    private static final String SPRING_BRIDGE = "com.vaadin.copilot.SpringBridge";
    private static final String SERVICE_METHOD_INFO = SPRING_BRIDGE + "$ServiceMethodInfo";
    private static final String VERSION_INFO = SPRING_BRIDGE + "$VersionInfo";

    private static final List<String> HARD_EXCLUDED_PACKAGES = List.of(
            "com.vaadin.",
            "com.github.mcollovati.quarkus.hilla.",
            "io.quarkus.",
            "io.smallrye.",
            "io.vertx.",
            "jakarta.",
            "javax.",
            "org.jboss.",
            "org.eclipse.microprofile.",
            "org.springframework.");

    private static final Set<String> DEFAULT_SCOPE_KEYS = Set.of(
            "application", "singleton", "dependent", "vaadin-service", "vaadin-session", "vaadin-ui", "vaadin-route");

    private CopilotQuarkusIntegration() {}

    public static Object callSpring(String methodName, Object... args) {
        return call(methodName, args);
    }

    public static Object callSpringSecurity(String methodName, Object... args) {
        return call(methodName, args);
    }

    public static Object callSpringData(String methodName, Object... args) {
        return call(methodName, args);
    }

    public static boolean isAvailable(VaadinContext context) {
        return beanManager().isPresent();
    }

    public static Object getWebApplicationContext(VaadinContext context) {
        return beanManager().map(QuarkusApplicationContext::new).orElse(null);
    }

    public static String getPropertyValue(VaadinContext context, String propertyName) {
        try {
            return ConfigProvider.getConfig()
                    .getOptionalValue(propertyName, String.class)
                    .orElse(null);
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static Class<?> getApplicationClass(VaadinContext context) {
        CopilotApplicationMetadata metadata = CopilotApplicationMetadata.load();
        Optional<Class<?>> configuredApplicationClass =
                metadata.applicationClassName().flatMap(CopilotQuarkusIntegration::loadClass);
        if (configuredApplicationClass.isPresent()) {
            return configuredApplicationClass.get();
        }
        return getEndpointClasses().stream()
                .findFirst()
                .or(() -> discoveredBeanClasses().stream()
                        .filter(metadata::isApplicationClass)
                        .findFirst())
                .orElse(CopilotQuarkusIntegration.class);
    }

    public static boolean isViewSecurityEnabled(VaadinContext context) {
        return bean(NavigationAccessControl.class)
                .filter(NavigationAccessControl::isEnabled)
                .filter(accessControl -> accessControl.hasAccessChecker(AnnotatedViewAccessChecker.class))
                .isPresent();
    }

    public static boolean isSpringSecurityEnabled(VaadinContext context) {
        return false;
    }

    public static void setActiveSpringSecurityUser(String username, VaadinSession session) {
        // Spring Security user switching has no Quarkus equivalent in this integration.
    }

    public static List<?> getEndpoints(VaadinContext context) {
        return endpointRegistry()
                .map(registry -> registry.getEndpoints().values().stream()
                        .flatMap(endpointData -> {
                            Object endpointObject = endpointData.getEndpointObject();
                            Class<?> endpointClass = userClass(endpointObject);
                            if (endpointClass == null || isVaadinInternal(endpointClass)) {
                                return List.of().stream();
                            }
                            return endpointData.getMethods().values().stream()
                                    .sorted(methodComparator())
                                    .map(method -> serviceMethodInfo(endpointClass, method));
                        })
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList()))
                .orElseGet(List::of);
    }

    public static List<?> getFlowUIServices(VaadinContext context) {
        Optional<BeanManager> beanManager = beanManager();
        if (beanManager.isEmpty()) {
            return List.of();
        }

        FlowServicesSettings settings = flowServicesSettings();
        CopilotApplicationMetadata metadata = CopilotApplicationMetadata.load();
        Set<String> endpointClassNames =
                getEndpointClasses().stream().map(Class::getName).collect(Collectors.toSet());

        Set<Class<?>> beanClasses = discoveredBeanClasses();
        Set<Class<?>> selectedClasses = new LinkedHashSet<>();
        for (Class<?> beanClass : beanClasses) {
            if (isExplicitlyIncluded(beanClass, settings)) {
                selectedClasses.add(beanClass);
            }
            if (settings.discovery() != CopilotConfiguration.Discovery.NONE
                    && isAutomaticCandidate(beanManager.get(), beanClass, metadata, settings)) {
                selectedClasses.add(beanClass);
            }
        }

        return selectedClasses.stream()
                .filter(beanClass -> !isHardExcluded(beanClass))
                .filter(beanClass -> !isExplicitlyExcluded(beanClass, settings))
                .filter(beanClass -> !endpointClassNames.contains(beanClass.getName()))
                .filter(beanClass -> !isHillaEndpoint(beanClass))
                .flatMap(beanClass ->
                        publicServiceMethods(beanClass).map(method -> serviceMethodInfo(beanClass, method)))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public static Object getVersionInfo() {
        return springBridgeRecord(
                VERSION_INFO,
                new Class<?>[] {String.class, String.class},
                "Quarkus Hilla",
                QuarkusHillaExtension.getVersion().orElse(""));
    }

    public static boolean getJpaDatasourceInitialization(VaadinContext context) {
        return false;
    }

    public static List<Class<?>> getJpaEntityClasses(VaadinContext context) {
        return List.of();
    }

    public static Optional<?> getH2Info(VaadinContext context) {
        return Optional.empty();
    }

    private static Object call(String methodName, Object... args) {
        return switch (methodName) {
            case "getWebApplicationContext" -> getWebApplicationContext((VaadinContext) args[0]);
            case "getPropertyValue" -> getPropertyValue((VaadinContext) args[0], (String) args[1]);
            case "getApplicationClass" -> getApplicationClass((VaadinContext) args[0]);
            case "isViewSecurityEnabled" -> isViewSecurityEnabled((VaadinContext) args[0]);
            case "getJpaDatasourceInitialization" -> getJpaDatasourceInitialization((VaadinContext) args[0]);
            case "getEndpoints" -> getEndpoints((VaadinContext) args[0]);
            case "getFlowUIServices" -> getFlowUIServices((VaadinContext) args[0]);
            case "getVersionInfo" -> getVersionInfo();
            case "isSpringSecurityEnabled" -> isSpringSecurityEnabled((VaadinContext) args[0]);
            case "setActiveSpringSecurityUser" -> {
                setActiveSpringSecurityUser((String) args[0], (VaadinSession) args[1]);
                yield null;
            }
            case "getJpaEntityClasses" -> getJpaEntityClasses((VaadinContext) args[0]);
            case "getH2Info" -> getH2Info((VaadinContext) args[0]);
            default -> throw new IllegalArgumentException("Unsupported Copilot bridge method " + methodName);
        };
    }

    private static boolean isAutomaticCandidate(
            BeanManager beanManager,
            Class<?> beanClass,
            CopilotApplicationMetadata metadata,
            FlowServicesSettings settings) {
        if (isHardExcluded(beanClass) || isExplicitlyExcluded(beanClass, settings)) {
            return false;
        }
        if (settings.packageMode() == CopilotConfiguration.PackageMode.APPLICATION
                && !metadata.isApplicationClass(beanClass)) {
            return false;
        }
        return switch (settings.discovery()) {
            case NONE -> false;
            case ALL -> true;
            case SERVICES -> isServiceLikeBean(beanManager, beanClass, settings);
        };
    }

    private static boolean isServiceLikeBean(
            BeanManager beanManager, Class<?> beanClass, FlowServicesSettings settings) {
        if (hasAnnotationNamed(beanClass, "org.springframework.stereotype.Service")) {
            return true;
        }
        return beansForClass(beanManager, beanClass).stream()
                .map(Bean::getScope)
                .map(CopilotQuarkusIntegration::scopeKey)
                .anyMatch(settings.includeScopeKeys()::contains);
    }

    private static Set<Class<?>> discoveredBeanClasses() {
        return beanManager()
                .map(beanManager -> beanManager.getBeans(Object.class, new AnyLiteral()).stream()
                        .map(Bean::getBeanClass)
                        .filter(Objects::nonNull)
                        .map(SpringReplacements::classUtils_getUserClass)
                        .filter(CopilotQuarkusIntegration::isDiscoverableClass)
                        .sorted(Comparator.comparing(Class::getName))
                        .collect(Collectors.toCollection(LinkedHashSet::new)))
                .orElseGet(LinkedHashSet::new);
    }

    private static Set<Bean<?>> beansForClass(BeanManager beanManager, Class<?> beanClass) {
        return beanManager.getBeans(Object.class, new AnyLiteral()).stream()
                .filter(bean -> SpringReplacements.classUtils_getUserClass(bean.getBeanClass())
                        .equals(beanClass))
                .collect(Collectors.toSet());
    }

    private static Optional<EndpointRegistry> endpointRegistry() {
        return bean(EndpointRegistry.class);
    }

    private static Set<Class<?>> getEndpointClasses() {
        return endpointRegistry()
                .map(registry -> registry.getEndpoints().values().stream()
                        .map(EndpointRegistry.VaadinEndpointData::getEndpointObject)
                        .map(CopilotQuarkusIntegration::userClass)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toCollection(LinkedHashSet::new)))
                .orElseGet(LinkedHashSet::new);
    }

    private static <T> Optional<T> bean(Class<T> beanType) {
        return beanManager().flatMap(beanManager -> {
            Set<Bean<?>> beans = beanManager.getBeans(beanType, new AnyLiteral());
            if (beans.isEmpty()) {
                return Optional.empty();
            }
            Bean<?> bean = beanManager.resolve(beans);
            CreationalContext<?> context = beanManager.createCreationalContext(bean);
            return Optional.of(beanType.cast(beanManager.getReference(bean, beanType, context)));
        });
    }

    private static Optional<BeanManager> beanManager() {
        try {
            return Optional.of(CDI.current().getBeanManager());
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private static Class<?> userClass(Object object) {
        if (object == null) {
            return null;
        }
        return SpringReplacements.classUtils_getUserClass(object);
    }

    private static boolean isDiscoverableClass(Class<?> type) {
        return type != null
                && type != Object.class
                && !type.isPrimitive()
                && !type.isArray()
                && !type.isAnnotation()
                && !type.isEnum()
                && !type.isAnonymousClass()
                && !type.isLocalClass();
    }

    private static boolean isHillaEndpoint(Class<?> type) {
        return type.isAnnotationPresent(BrowserCallable.class) || type.isAnnotationPresent(Endpoint.class);
    }

    private static boolean isVaadinInternal(Class<?> type) {
        return type.getName().startsWith("com.vaadin.");
    }

    private static boolean isHardExcluded(Class<?> type) {
        String className = type.getName();
        return HARD_EXCLUDED_PACKAGES.stream().anyMatch(className::startsWith);
    }

    private static boolean isExplicitlyIncluded(Class<?> type, FlowServicesSettings settings) {
        return settings.includeClasses().contains(type.getName()) || matchesPackage(type, settings.includePackages());
    }

    private static boolean isExplicitlyExcluded(Class<?> type, FlowServicesSettings settings) {
        return settings.excludeClasses().contains(type.getName()) || matchesPackage(type, settings.excludePackages());
    }

    private static boolean matchesPackage(Class<?> type, Set<String> packagePrefixes) {
        String className = type.getName();
        String packageName = packageName(type);
        return packagePrefixes.stream()
                .map(String::trim)
                .filter(prefix -> !prefix.isEmpty())
                .anyMatch(prefix -> packageName.equals(prefix)
                        || packageName.startsWith(prefix + ".")
                        || className.startsWith(prefix + "."));
    }

    private static String packageName(Class<?> type) {
        Package typePackage = type.getPackage();
        if (typePackage != null) {
            return typePackage.getName();
        }
        String className = type.getName();
        int separator = className.lastIndexOf('.');
        return separator > 0 ? className.substring(0, separator) : "";
    }

    private static java.util.stream.Stream<Method> publicServiceMethods(Class<?> beanClass) {
        return Arrays.stream(beanClass.getMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> !Modifier.isStatic(method.getModifiers()))
                .filter(method -> !method.isBridge())
                .filter(method -> !method.isSynthetic())
                .filter(method -> method.getDeclaringClass() != Object.class)
                .sorted(methodComparator());
    }

    private static Comparator<Method> methodComparator() {
        return Comparator.comparing(Method::getName)
                .thenComparing(method -> Arrays.toString(method.getParameterTypes()));
    }

    private static Object serviceMethodInfo(Class<?> serviceClass, Method serviceMethod) {
        return springBridgeRecord(
                SERVICE_METHOD_INFO, new Class<?>[] {Class.class, Method.class}, serviceClass, serviceMethod);
    }

    private static Object springBridgeRecord(String className, Class<?>[] parameterTypes, Object... args) {
        try {
            Class<?> recordClass = SpringReplacements.class_forName(className);
            Constructor<?> constructor = recordClass.getConstructor(parameterTypes);
            return constructor.newInstance(args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to create Copilot bridge record " + className, e);
        }
    }

    private static Optional<Class<?>> loadClass(String className) {
        try {
            return Optional.of(SpringReplacements.class_forName(className));
        } catch (ClassNotFoundException e) {
            return Optional.empty();
        }
    }

    private static boolean hasAnnotationNamed(Class<?> type, String annotationName) {
        return Arrays.stream(type.getAnnotations())
                .map(Annotation::annotationType)
                .anyMatch(annotationType -> annotationType.getName().equals(annotationName));
    }

    private static String scopeKey(Class<? extends Annotation> scope) {
        String scopeName = scope.getName();
        if (scope == ApplicationScoped.class) {
            return "application";
        }
        if (scope == Singleton.class) {
            return "singleton";
        }
        if (scope == Dependent.class) {
            return "dependent";
        }
        if (scope == RequestScoped.class) {
            return "request";
        }
        if (scope == SessionScoped.class) {
            return "session";
        }
        if (scope == ConversationScoped.class) {
            return "conversation";
        }
        return switch (scopeName) {
            case "com.vaadin.quarkus.annotation.VaadinServiceScoped" -> "vaadin-service";
            case "com.vaadin.quarkus.annotation.VaadinSessionScoped" -> "vaadin-session";
            case "com.vaadin.quarkus.annotation.UIScoped", "com.vaadin.quarkus.annotation.NormalUIScoped" ->
                "vaadin-ui";
            case "com.vaadin.quarkus.annotation.RouteScoped", "com.vaadin.quarkus.annotation.NormalRouteScoped" ->
                "vaadin-route";
            default -> scopeName;
        };
    }

    private static FlowServicesSettings flowServicesSettings() {
        try {
            CopilotConfiguration config = ConfigProvider.getConfig()
                    .unwrap(SmallRyeConfig.class)
                    .getConfigMapping(CopilotConfiguration.class);
            return new FlowServicesSettings(
                    config.discovery(),
                    config.packageMode(),
                    normalizeScopeKeys(config.includeScopes()),
                    copy(config.includePackages()),
                    copy(config.excludePackages()),
                    copy(config.includeClasses()),
                    copy(config.excludeClasses()));
        } catch (RuntimeException e) {
            return new FlowServicesSettings(
                    CopilotConfiguration.Discovery.SERVICES,
                    CopilotConfiguration.PackageMode.APPLICATION,
                    DEFAULT_SCOPE_KEYS,
                    Set.of(),
                    Set.of(),
                    Set.of(),
                    Set.of());
        }
    }

    private static Set<String> normalizeScopeKeys(Set<String> scopeKeys) {
        if (scopeKeys == null || scopeKeys.isEmpty()) {
            return Set.of();
        }
        return scopeKeys.stream()
                .map(scopeKey -> scopeKey.trim().toLowerCase(Locale.ROOT).replace('_', '-'))
                .filter(scopeKey -> !scopeKey.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    private static Set<String> copy(Optional<Set<String>> values) {
        return values.orElseGet(Set::of).stream()
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    private record FlowServicesSettings(
            CopilotConfiguration.Discovery discovery,
            CopilotConfiguration.PackageMode packageMode,
            Set<String> includeScopeKeys,
            Set<String> includePackages,
            Set<String> excludePackages,
            Set<String> includeClasses,
            Set<String> excludeClasses) {}
}
