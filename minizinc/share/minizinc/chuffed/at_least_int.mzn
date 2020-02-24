%-----------------------------------------------------------------------------%
% Requires at least 'n' variables in 'x' to take the value 'v'.
%-----------------------------------------------------------------------------%

predicate at_least_int(int: n, array[int] of var int: x, int: v) =
    bool_sum_ge([x[i] == v | i in index_set(x)], n);

