annotation mdd(array [int] of ann: opts);
annotation explain_minimal;
annotation explain_greedy;
annotation store_explanations;
annotation discard_explanations;

predicate regular(array[int] of var int: x, int: Q, int: S,
                  array[int,int] of int: d, int: q0, set of int: F);
