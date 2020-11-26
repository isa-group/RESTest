%-----------------------------------------------------------------------------%
% Requires that the number of distinct values in 'x' is 'n'.
%-----------------------------------------------------------------------------%
predicate fzn_nvalue(var int: n, array[int] of var int: x) =
    let { int: lx = lb_array(x),
          int: ux = ub_array(x),
        } in
        bool_sum_eq([exists(i in index_set(x)) ( x[i] = j ) | j in lx..ux], n);
