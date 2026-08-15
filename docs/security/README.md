# Security

Quarkus-Hilla connects Quarkus authentication with Vaadin Flow and Hilla
authorization. Quarkus authenticates applicable HTTP requests and creates the
`SecurityIdentity`; Quarkus-Hilla uses that identity for framework-specific
route and endpoint access checks.

## Responsibility boundaries

| Responsibility | Owner |
| --- | --- |
| Credentials, login challenges, tokens, sessions, identity creation, and role mapping | Quarkus and the configured authentication extension |
| `quarkus.http.auth.permission.*` and custom HTTP policies | Quarkus Security |
| Flow route annotations | Vaadin Flow with the Quarkus `SecurityIdentity` bridge |
| Hilla generated route metadata and layout hierarchy | Quarkus-Hilla |
| `@BrowserCallable` annotations | Hilla with the Quarkus `SecurityIdentity` bridge |

Quarkus-Hilla does not implement authentication protocols. For example, with
OIDC, Quarkus handles provider discovery, redirects, token validation, session
cookies, refresh, and logout. Quarkus-Hilla only consumes the resulting
identity and roles.

## Supported authentication mechanisms

Quarkus-Hilla currently supports Quarkus Form, Basic, OIDC, and JWT
authentication.

OIDC is the recommended integration for applications that need an external
identity provider. Requirements such as single sign-on, social login,
multi-factor authentication, or passkeys can be implemented by that provider
while the application remains a standard OIDC application.

Direct support for other Quarkus authentication mechanisms, including OAuth2
opaque tokens, mutual TLS, WebAuthn, Kerberos, and custom authentication
mechanisms, is currently not planned. If direct support for another mechanism
is required, open an issue describing the mechanism and the concrete
application scenario. We will evaluate and prioritize the integration based
on actual demand.

This limitation concerns automatic activation of the Quarkus-Hilla security
bridge. It does not mean that Quarkus itself lacks these mechanisms.

## Access annotations

When a supported authentication mechanism is active, Quarkus-Hilla applies
the current `SecurityIdentity` to these access rules:

| Annotation | Access |
| --- | --- |
| `@AnonymousAllowed` | Authentication is not required. |
| `@PermitAll` | An authenticated identity is required. |
| `@RolesAllowed` | An authenticated identity with at least one declared role is required. |
| `@DenyAll` | Access is denied. |

Flow routes without an access annotation are denied by default when navigation
security is enabled. Browser-callable methods follow Hilla's annotation
security and remain protected on the server. A browser-callable method without
an access annotation is denied by default. Frontend visibility is never an
authorization boundary.

## Flow and Hilla routes

Flow navigation uses Vaadin's `NavigationAccessControl` with principal and
roles supplied by Quarkus `SecurityIdentity`.

For Hilla file-based routes generated from `src/main/frontend/views`, declare
access in the exported `ViewConfig`:

```tsx
export const config: ViewConfig = {
  loginRequired: true,
  rolesAllowed: ['ADMIN'],
};
```

Quarkus-Hilla reads the generated route manifest and evaluates leaf route
metadata together with every generated parent layout. This prevents a public
child route from bypassing a protected layout. Route matching follows the
ranking used by the bundled React Router for static, parameterized, optional,
and wildcard segments. If multiple routes have the same best rank, every
matching route must permit access.

Request paths are canonicalized with Quarkus HTTP security rules before
public-resource and route checks. When an encoded path can be interpreted
differently by Quarkus and the client router, both interpretations are
evaluated and the more restrictive result wins.

Route metadata protects initial access and framework navigation. It does not
replace server-side authorization for data or operations. Secure those through
annotated browser-callable methods or Quarkus HTTP authorization.

Custom `routes.tsx` definitions are frontend-owned and cannot be evaluated
reliably by the server. In particular, security attached only to a custom
React Router layout is not part of the generated Hilla route manifest. Secure
all data and operations through annotated browser-callable methods or Quarkus
HTTP authorization. Client-side route guards are useful for navigation and
visibility, but they are not server-side authorization.

## Route manifest availability

Production builds that use generated Hilla file routes must contain a valid
route manifest. Missing or invalid expected metadata fails application startup
instead of running with incomplete authorization data.

In development mode, frontend tooling can create the manifest after the Java
application has started. While route metadata is incomplete, the global Hilla
policy denies every request that was not already recognized as a framework
request, public endpoint, Flow route, or permitted resource. This can
temporarily include application paths that are not generated Hilla routes.
Discovery is retried after a short backoff. A previously complete snapshot is
discarded if a later refresh sees incomplete data, preventing stale
authorization decisions.

Applications using only a custom React Router do not require the generated
manifest. Those routes remain outside server-side route evaluation as
described above.

## Quarkus HTTP authorization

Quarkus HTTP permissions and custom `HttpSecurityPolicy` implementations still
apply to real HTTP requests. Quarkus-Hilla does not replay these policies for
an internal Flow or Hilla router transition because such navigation does not
produce a target HTTP request.

Consequently, route annotations or generated Hilla route metadata must protect
framework-internal navigation. Use Quarkus path policies for REST resources,
static resources, and other HTTP endpoints.

## Authentication-specific notes

### Form

Quarkus-Hilla supplies its form authentication mechanism and uses Quarkus form
configuration for login handling. Form-specific behavior is not enabled for
other authentication mechanisms.

### Basic

Quarkus handles the `Authorization: Basic` challenge and credentials.
Quarkus-Hilla applies the resulting identity and roles to Flow, generated Hilla
routes, and browser-callable endpoints. Basic authentication has no application
login page and browsers can cache credentials, so OIDC or Form is usually a
better choice for an interactive application.

Basic authentication must be [enabled explicitly in Quarkus](https://quarkus.io/guides/security-basic-authentication-howto)
with
`quarkus.http.auth.basic=true`. Quarkus-Hilla uses this build-time signal to
activate its security bridge. Quarkus fallback Basic authentication and Basic
authentication configured only through the programmatic `HttpSecurity` API do
not provide that signal and are not currently supported by the bridge.

### OIDC

Use [Quarkus OIDC configuration](https://quarkus.io/guides/security-oidc-code-flow-authentication).
For browser applications,
`quarkus.oidc.application-type=web-app` enables Authorization Code Flow. Quarkus
redirects to the external provider and creates the application identity after
the callback. Quarkus-Hilla does not duplicate this protocol flow.

Quarkus-Hilla integration tests verify activation, identity propagation, and
annotation decisions. They do not repeat Quarkus's provider-specific browser
login tests. Applications should still test their selected provider,
redirect URI, role mapping, and logout configuration.

### JWT

JWT bearer authentication remains stateless at the authentication layer.
Vaadin UI state can still use server-side sessions; authentication state and UI
state are separate concerns. No Spring-specific stateless security
configuration is required.

## Current limitations

- Custom React Router routes are not server-evaluated.
- Quarkus path policies are not replayed for framework-internal navigation.
- Direct integration with authentication mechanisms outside Form, Basic,
  OIDC, and JWT is not automatically activated.
- Authentication behavior during provider redirects, token refresh, logout,
  and multi-tenancy remains governed by Quarkus configuration.

## Migration notes

Disabling Vaadin `NavigationAccessControl` no longer makes Flow routes
implicitly public. Quarkus-Hilla cannot verify `@AnonymousAllowed` in that mode
and therefore requires authentication at the HTTP boundary. Keep navigation
access control enabled and mark intentionally public Flow routes with
`@AnonymousAllowed`. If navigation access control is disabled, Quarkus-Hilla
cannot identify public Flow routes and requires authentication for their HTTP
access.
