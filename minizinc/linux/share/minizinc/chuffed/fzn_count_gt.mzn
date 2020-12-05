predicate fzn_count_gt(array[int] of var int: x, var int: y, var int: c) =
    bool_sum_lt([x[i] == y | i in index_set(x)], c);
