predicate chuffed_cost_regular(array[int] of var int: x, int: Q, int: S,
                               array[int] of int: d, array[int] of int: w, int: q0,
                               set of int: F, var int: c);

predicate fzn_cost_regular(array[int] of var int: x, int: Q, int: S,
                           array[int,int] of int: d, int: q0, set of int: F,
                           array[int,int] of int: c, var int: C) =
  chuffed_cost_regular(x,Q,S,array1d(d),array1d(c),q0,F,C);
