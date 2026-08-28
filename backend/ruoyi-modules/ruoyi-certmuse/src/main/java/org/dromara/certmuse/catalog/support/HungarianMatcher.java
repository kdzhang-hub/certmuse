package org.dromara.certmuse.catalog.support;

import org.springframework.stereotype.Component;

import java.util.Arrays;

/** Computes a maximum-weight one-to-one assignment without external dependencies. */
@Component
public class HungarianMatcher {

    public int[] maximize(int[][] weights) {
        int rows = weights.length;
        int columns = rows == 0 ? 0 : weights[0].length;
        int size = Math.max(rows, columns);
        if (size == 0) return new int[0];
        int max = 0;
        for (int[] row : weights) for (int weight : row) max = Math.max(max, weight);
        long[] u = new long[size + 1];
        long[] v = new long[size + 1];
        int[] p = new int[size + 1];
        int[] way = new int[size + 1];
        for (int i = 1; i <= size; i++) {
            p[0] = i;
            long[] min = new long[size + 1];
            Arrays.fill(min, Long.MAX_VALUE / 4);
            boolean[] used = new boolean[size + 1];
            int column = 0;
            do {
                used[column] = true;
                int row = p[column];
                long delta = Long.MAX_VALUE / 4;
                int next = 0;
                for (int j = 1; j <= size; j++) if (!used[j]) {
                    int weight = row <= rows && j <= columns ? weights[row - 1][j - 1] : 0;
                    long current = (long) max - weight - u[row] - v[j];
                    if (current < min[j]) { min[j] = current; way[j] = column; }
                    if (min[j] < delta) { delta = min[j]; next = j; }
                }
                for (int j = 0; j <= size; j++) {
                    if (used[j]) { u[p[j]] += delta; v[j] -= delta; }
                    else min[j] -= delta;
                }
                column = next;
            } while (p[column] != 0);
            do {
                int next = way[column];
                p[column] = p[next];
                column = next;
            } while (column != 0);
        }
        int[] assignment = new int[rows];
        Arrays.fill(assignment, -1);
        for (int column = 1; column <= size; column++) {
            if (p[column] > 0 && p[column] <= rows && column <= columns) assignment[p[column] - 1] = column - 1;
        }
        return assignment;
    }
}
