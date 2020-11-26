predicate fzn_count_leq_par(array[int] of var int: x, int: y, int: c) =
    bool_sum_ge([x[i] == y | i in index_set(x)], c);
