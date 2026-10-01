package it.unicas.chronogram.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on Spring's {@code @Scheduled} support, which today drives one job:
 * {@code NotificationScheduler}, the periodic push reminders.
 *
 * <p>Kept as its own {@code @Configuration} rather than an annotation on
 * {@code ChronogramApplication} so the slice tests - which load web components
 * only - do not start a background scheduler as a side effect of testing a
 * controller.
 *
 * <p>The default single-threaded scheduler is deliberate: one pass a minute over
 * the users who opted in is not work that needs parallelism, and a single thread
 * makes it impossible for two passes to send the same reminder twice. Note that
 * each instance runs its own scheduler, so should this ever be deployed behind
 * more than one Tomcat, the passes would need a shared lock.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
