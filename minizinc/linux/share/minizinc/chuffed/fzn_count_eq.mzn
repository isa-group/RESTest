predicate fzn_count_eq(array[int] of var int: x, var int: y, var int: c) =
    bool_sum_eq([x[i] == y | i in index_set(x)], c);
