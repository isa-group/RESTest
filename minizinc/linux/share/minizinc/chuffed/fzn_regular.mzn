annotation mdd(array [int] of ann: opts);
annotation explain_minimal;
annotation explain_greedy;
annotation store_explanations;
annotation discard_explanations;

predicate chuffed_regular(array[int] of var int: x, int: Q, int: S,
                          array[int] of int: d, int: q0, set of int: F);

predicate fzn_regular(array[int] of var int: x, int: Q, int: S,
                      array[int,int] of int: d, int: q0, set of int: F) =
  chuffed_regular(x,Q,S,array1d(d),q0,F);
