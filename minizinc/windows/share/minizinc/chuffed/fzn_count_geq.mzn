predicate fzn_count_geq(array[int] of var int: x, var int: y, var int: c) =
    bool_sum_le([x[i] == y | i in index_set(x)], c);
