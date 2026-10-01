package it.unicas.chronogram.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.unicas.chronogram.common.exception.ApiExceptions.ServiceException;
import it.unicas.chronogram.config.ChronogramProperties;
import it.unicas.chronogram.domain.PushSubscription;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Subscription;
import org.apache.http.HttpResponse;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.jose4j.lang.JoseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.security.Security;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The single place that talks to the browsers' push services.
 *
 * <p>Wraps the web-push library so the rest of the application deals with a
 * {@link PushSubscription} row, a title and a body, and gets back one of three
 * answers ({@link Result}) instead of half a dozen checked exceptions. The
 * distinction that matters operationally - "this subscription is dead, drop it"
 * versus "this attempt failed, try again later" - is decided here, once, from the
 * HTTP status the push service returned.
 *
 * <p>Nothing secret is logged: the VAPID private key never leaves this class, and
 * endpoints - which embed a per-device token that would let their holder push to
 * that browser - are reduced to their host before they reach a log line.
 */
@Component
public class PushSender {

    private static final Logger log = LoggerFactory.getLogger(PushSender.class);

    /**
     * Groups the notification on the client: the service worker replaces a
     * pending reminder instead of stacking a new one for every missed interval,
     * so a phone left untouched overnight shows one reminder rather than eight.
     */
    private static final String TAG = "chronogram-reminder";
    /** Where a click lands. A path, not a URL: the service worker resolves it. */
    private static final String URL = "/home";

    /**
     * How long a single delivery may take. The library's own {@code send()} waits
     * forever, which on a hanging push service would hold the scheduler thread for
     * the lifetime of the process; the bound is applied here by waiting on the
     * asynchronous call with a deadline instead.
     */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    /** What became of one delivery, from the caller's point of view. */
    public enum Result {
        /** The push service accepted it. */
        DELIVERED,
        /**
         * The subscription no longer exists - the push service answered 404/410,
         * or its stored keys are unusable. Either way the row is dead and the
         * caller is expected to delete it.
         */
        EXPIRED,
        /** Anything else: network, timeout, upstream error. Worth retrying. */
        FAILED
    }

    private final ChronogramProperties properties;
    private final ObjectMapper objectMapper;

    /** Built on first use and reused: constructing it parses the VAPID key pair. */
    private volatile PushService pushService;

    public PushSender(ChronogramProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /** Whether this installation has a VAPID key pair and can send at all. */
    public boolean isConfigured() {
        return properties.getPush().isConfigured();
    }

    /**
     * The public VAPID key the browser must subscribe with, or {@code null} when
     * the feature is not configured.
     */
    public String publicKey() {
        return isConfigured() ? properties.getPush().getVapidPublicKey() : null;
    }

    /**
     * Encrypts and delivers one notification to one device. Never throws for a
     * per-device problem - that is what {@link Result} is for.
     *
     * @throws ServiceException if the configured key pair cannot be used at all,
     *                          which is a deployment mistake rather than a
     *                          property of this subscription
     */
    public Result send(PushSubscription subscription, String title, String body) {
        // Resolved first: building the service is also what registers the JCE
        // provider the Notification constructor below needs.
        PushService service = pushService();
        String endpoint = subscription.getEndpoint();

        Notification notification;
        try {
            notification = new Notification(
                    new Subscription(endpoint,
                            new Subscription.Keys(subscription.getP256dh(), subscription.getAuth())),
                    payload(title, body));
        } catch (GeneralSecurityException e) {
            // The keys stored for this device cannot be read, so they never will
            // be: treat the row like an expired one instead of retrying it every
            // interval forever.
            log.warn("Push subscription at {} has unusable keys ({}); it will be removed",
                    host(endpoint), e.getClass().getSimpleName());
            return Result.EXPIRED;
        }

        Future<HttpResponse> pending;
        try {
            pending = service.sendAsync(notification);
        } catch (GeneralSecurityException | IOException | JoseException e) {
            log.warn("Could not prepare the push notification for {}: {}",
                    host(endpoint), e.getClass().getSimpleName());
            return Result.FAILED;
        }

        try {
            HttpResponse response = pending.get(REQUEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            return classify(response.getStatusLine().getStatusCode(), endpoint);
        } catch (TimeoutException e) {
            pending.cancel(true);
            log.warn("Push notification to {} timed out after {}s",
                    host(endpoint), REQUEST_TIMEOUT.toSeconds());
            return Result.FAILED;
        } catch (InterruptedException e) {
            pending.cancel(true);
            Thread.currentThread().interrupt();
            return Result.FAILED;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            log.warn("Push notification to {} failed: {}: {}",
                    host(endpoint), cause.getClass().getSimpleName(), cause.getMessage());
            return Result.FAILED;
        }
    }

    // ---- internals ----

    /**
     * 404 and 410 are the protocol's way of saying the subscription is gone, and
     * the only statuses that justify deleting a user's row; everything else - 429
     * and 5xx included - is a transient upstream condition.
     */
    private Result classify(int status, String endpoint) {
        if (status >= 200 && status < 300) {
            return Result.DELIVERED;
        }
        if (status == 404 || status == 410) {
            log.info("Push subscription at {} is gone (HTTP {}); it will be removed",
                    host(endpoint), status);
            return Result.EXPIRED;
        }
        log.warn("Push service at {} refused the notification with HTTP {}", host(endpoint), status);
        return Result.FAILED;
    }

    /**
     * The JSON the service worker reads in its {@code push} handler. Built with
     * Jackson rather than by concatenation, so a title or body containing a quote
     * cannot break the payload.
     */
    private String payload(String title, String body) {
        try {
            return objectMapper.writeValueAsString(new Payload(title, body, TAG, URL));
        } catch (JsonProcessingException e) {
            throw new ServiceException("Could not build the notification payload.", e);
        }
    }

    /** Shape of the push payload; the front-end service worker binds to it. */
    private record Payload(String title, String body, String tag, String url) {
    }

    /**
     * Lazily built, so an installation with no VAPID keys - or with a mistake in
     * them - still starts and still serves the notification endpoints, reporting
     * the feature as unconfigured rather than failing at boot.
     */
    private PushService pushService() {
        PushService existing = this.pushService;
        if (existing != null) {
            return existing;
        }
        synchronized (this) {
            if (this.pushService == null) {
                this.pushService = build();
            }
            return this.pushService;
        }
    }

    private PushService build() {
        ChronogramProperties.Push push = properties.getPush();
        if (!push.isConfigured()) {
            // Callers check isConfigured() first and answer 503; getting here means
            // one of them forgot, which is a bug and not a user-facing condition.
            throw new ServiceException("Push notifications are not configured on this server.");
        }
        // web-push loads the P-256 keys through the "BC" provider and does not
        // register it itself. Adding it is a no-op if something else already did.
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        try {
            return new PushService(push.getVapidPublicKey(), push.getVapidPrivateKey(), push.getSubject());
        } catch (GeneralSecurityException | RuntimeException e) {
            // Only the exception's type is logged: its message can quote the
            // malformed input, and that input is the private key.
            log.error("The configured VAPID key pair could not be loaded ({}). Regenerate it with "
                            + "`npx web-push generate-vapid-keys` and set VAPID_PUBLIC_KEY / VAPID_PRIVATE_KEY.",
                    e.getClass().getName());
            throw new ServiceException("Push notifications are misconfigured on this server.");
        }
    }

    /**
     * The push service's host, which is all a log line needs. The rest of the
     * endpoint is a bearer capability for that device and must not be written
     * anywhere it could be read back.
     */
    private static String host(String endpoint) {
        if (endpoint == null) {
            return "<none>";
        }
        try {
            String host = new URI(endpoint).getHost();
            return host == null ? "<unknown host>" : host;
        } catch (URISyntaxException e) {
            return "<unparseable endpoint>";
        }
    }
}
