predicate fzn_count_eq_par(array[int] of var int: x, int: y, int: c) =
    bool_sum_eq([x[i] == y | i in index_set(x)], c);
