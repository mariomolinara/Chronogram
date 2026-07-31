package it.unicas.chronogram.common;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Standard response envelope preserved from the legacy API so existing clients
 * keep working: {@code {"success": ..., "message": ..., "data": ...}}.
 * The {@code data} field is omitted from the JSON when null.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, String message, T data) {

    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(true, message, data);
    }

    public static ApiResponse<Void> ok(String message) {
        return new ApiResponse<>(true, message, null);
    }

    public static ApiResponse<Void> fail(String message) {
        return new ApiResponse<>(false, message, null);
    }
}
