%-----------------------------------------------------------------------------%
% Constrains 'c' to be the number of occurrences of 'y' in 'x'. This
% is less general than the Beldiceanu version, which takes an extra
% relational operation argument, and so allows "equal" in the previous
% sentence to be something else, eg. greater-than.
%-----------------------------------------------------------------------------%
predicate count(array[int] of var int: x, var int: y, var int: c) =
    bool_sum_eq([x[i] == y | i in index_set(x)], c);

predicate count_reif(array[int] of var int: x, var int: y, var int: c,
                     var bool: b) = 
    b <-> c = sum(i in index_set(x)) ( bool2int(x[i] == y) );



