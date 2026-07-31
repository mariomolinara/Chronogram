package it.unicas.chronogram.activity.dto;

/**
 * Optional filter for listing activities. {@code activityDate} is expected in
 * ISO {@code yyyy-MM-dd} format; when absent the current date is used. Any
 * {@code userId} sent by the client is ignored — the owner comes from the JWT.
 */
public record ListActivitiesRequest(String activityDate) {
}
