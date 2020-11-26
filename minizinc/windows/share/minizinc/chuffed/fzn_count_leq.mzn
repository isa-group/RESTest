predicate fzn_count_leq(array[int] of var int: x, var int: y, var int: c) =
    bool_sum_ge([x[i] == y | i in index_set(x)], c);
