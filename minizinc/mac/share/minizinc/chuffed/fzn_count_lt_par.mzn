predicate fzn_count_lt_par(array[int] of var int: x, int: y, int: c) =
    bool_sum_gt([x[i] == y | i in index_set(x)], c);
