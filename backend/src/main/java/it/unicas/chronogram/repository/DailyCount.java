package it.unicas.chronogram.repository;

import java.time.LocalDate;

/**
 * Projection for "how many rows fell on this day" aggregations. Spring Data
 * binds it by query alias, so aggregation queries must alias their columns
 * {@code day} and {@code total}.
 */
public interface DailyCount {

    LocalDate getDay();

    long getTotal();
}
