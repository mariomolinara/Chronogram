package it.unicas.chronogram.admin.dto;

/**
 * A page of the back-office user list, plus the per-state totals.
 *
 * <p>The counts travel with the page on purpose: the admin screen shows a
 * "pending" badge next to its filters, and deriving it from the current page
 * would be wrong as soon as the list is filtered or paginated.
 *
 * @param items      the accounts on this page
 * @param page       zero-based page index
 * @param size       page size actually applied
 * @param totalItems accounts matching the current filter
 * @param totalPages number of pages for the current filter
 * @param counts     totals per state, ignoring the current filter
 */
public record AdminUserPageResponse(java.util.List<AdminUserResponse> items,
                                    int page,
                                    int size,
                                    long totalItems,
                                    int totalPages,
                                    StatusCounts counts) {

    /** Totals per lifecycle state across all participants. */
    public record StatusCounts(long pending, long active, long blocked) {
    }
}
