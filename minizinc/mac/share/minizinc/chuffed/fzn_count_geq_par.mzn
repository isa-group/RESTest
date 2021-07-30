predicate fzn_count_geq_par(array[int] of var int: x, int: y, int: c) =
    bool_sum_le([x[i] == y | i in index_set(x)], c);
