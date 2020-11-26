predicate chuffed_maximum_arg_bool(array[int] of var bool: x, int: offset, var int: y);

predicate fzn_maximum_arg_bool(array[int] of var bool: x, var int: i) =
  chuffed_maximum_arg_bool(x,min(index_set(x)),i);
