%-----------------------------------------------------------------------------%
% Requires exactly 'n' variables in 'x' to take the value 'v'.
%-----------------------------------------------------------------------------%

predicate exactly_int(int: n, array[int] of var int: x, int: v) =
    bool_sum_eq([x[i] == v | i in index_set(x)], n);
