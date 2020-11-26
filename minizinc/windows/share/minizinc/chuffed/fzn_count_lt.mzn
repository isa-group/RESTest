predicate fzn_count_lt(array[int] of var int: x, var int: y, var int: c) =
    bool_sum_gt([x[i] == y | i in index_set(x)], c);
