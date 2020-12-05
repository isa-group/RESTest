%-----------------------------------------------------------------------------%
% Requires that for all `i', the value `cover[i]' appears at least `lbound[i]'
% and at most `ubound[i]' times in the array `x'.
%-----------------------------------------------------------------------------%

predicate fzn_global_cardinality_low_up(array[int] of var int: x,
                                        array[int] of int: cover,
                                        array[int] of int: lbound,
                                        array[int] of int: ubound) =
     forall(i in index_set(cover)) (
         bool_sum_ge([x[j] = cover[i] | j in index_set(x)], lbound[i]) /\
         bool_sum_le([x[j] = cover[i] | j in index_set(x)], ubound[i])
     );
