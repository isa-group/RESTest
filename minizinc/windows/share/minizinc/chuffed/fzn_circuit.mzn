include "all_different.mzn";

/** @group globals
  Constrains the elements of \a x to define a circuit where \a x[\p i] = \p j means
  that \p j is the successor of \p i. 
*/

predicate fzn_circuit(array[int] of var int: x) = 
    % XXX [AS] It seems so that the decompositionis performing better than the
    % global propagator.
    % TODO Extensive performance tests should be conducted
    let { bool: use_global = false } in
    if use_global then
        chuffed_circuit(x, min(index_set(x)))
    else
        circuit_decomp(x)
    endif;

predicate circuit_decomp(array[int] of var int: x) = 
    let { set of int: S = index_set(x),
          int: l = min(S),
	  int: n = card(S),
	  array[S] of var 1..n: order
        } in
    all_different(x) /\	
    all_different(order) /\
    forall(i in S)(x[i] != i) /\ 
    order[l] = 1 /\
    forall(i in S)(order[i] != n -> order[x[i]] = order[i] + 1) /\
    forall(i in S)(order[i] == n -> x[i] = l );

predicate chuffed_circuit(array[int] of var int: x, int: index_offset);
        
