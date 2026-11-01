package com.overleaf.client.ui.backend;

import java.time.LocalDate;
import java.util.Map;

/**
 * UI seam for the contribution graph on the home dashboard.
 */
public interface ActivityGateway {

    Map<LocalDate, Integer> editsByDay(LocalDate from, LocalDate to);

    int compilesThisYear();

    /** Adds {@code count} local edits to today's total. */
    void recordEdits(int count);

    /** Counts one successful compile. */
    void recordCompile();
}
