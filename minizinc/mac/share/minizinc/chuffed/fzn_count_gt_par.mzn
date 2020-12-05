predicate fzn_count_gt_par(array[int] of var int: x, int: y, int: c) =
    bool_sum_lt([x[i] == y | i in index_set(x)], c);
