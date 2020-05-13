%-----------------------------------------------------------------------------%
% Constrains the elements of the array 'vs' to be all different except those
% elements that are assigned the value 0.
%-----------------------------------------------------------------------------%

predicate alldifferent_except_0(array[int] of var int: vs) = 
    forall(i, j in index_set(vs) where i != j) (
       (vs[i] != 0 /\ vs[j] != 0) -> vs[i] != vs[j]
    );
%-----------------------------------------------------------------------------%
% Constrains the array of objects 'x' to be all different.
%-----------------------------------------------------------------------------%

predicate all_different_int(array[int] of var int: x) =
    forall(i,j in index_set(x) where i < j) ( x[i] != x[j] );
%-----------------------------------------------------------------------------%
% Constrains the array of objects 'x' to be all different.
%-----------------------------------------------------------------------------%


predicate all_different(array[int] of var int: x) =
    all_different_int(x);

predicate all_different(array[int] of var set of int: x) =
    all_different_set(x);

% Synonyms for the above.

predicate alldifferent(array[int] of var int: x) =
    all_different_int(x);

predicate alldifferent(array[int] of var set of int: x) =
    all_different_set(x);

% The actual definitions are in all_different.mzn.
% This file is used to handle the case where users include
% "alldifferent.mzn";
%
%-----------------------------------------------------------------------------%
% Constrains the array of objects 'x' to be all different.
%-----------------------------------------------------------------------------%

predicate all_different_set(array[int] of var set of int: x) =
    forall(i,j in index_set(x) where i < j) ( x[i] != x[j] );
%-----------------------------------------------------------------------------%
% Ensures that every pair of sets in the array 'S' is disjoint.
%-----------------------------------------------------------------------------%

predicate all_disjoint(array[int] of var set of int: S) =
    forall(i,j in index_set(S) where i < j) ( disjoint(S[i], S[j]) );

predicate all_equal_int(array[int] of var int: x) =
	forall(i, j in index_set(x) where i < j) ( x[i] = x[j] );
%-----------------------------------------------------------------------------%
% Constraints the array of objects 'x' to be all equal.
%-----------------------------------------------------------------------------%


predicate all_equal(array[int] of var int: x) =
	all_equal_int(x);

predicate all_equal(array[int] of var set of int: x) =
	all_equal_set(x);
predicate all_equal_set(array[int] of var set of int: x) =
	forall(i, j in index_set(x) where i < j) ( x[i] = x[j] );
%-----------------------------------------------------------------------------%
% Requires exactly 'n' variables in 'x' to take one of the values in 'v'.
%-----------------------------------------------------------------------------%
predicate among(var int: n, array[int] of var int: x, set of int: v) =
    n == sum(i in index_set(x)) ( bool2int(x[i] in v) );

%-----------------------------------------------------------------------------%
% Requires at least 'n' variables in 'x' to take the value 'v'.
%-----------------------------------------------------------------------------%

predicate at_least_int(int: n, array[int] of var int: x, int: v) =
    sum(i in index_set(x)) ( bool2int(x[i] == v) ) >= n;

%-----------------------------------------------------------------------------%
% Requires at least 'n' variables in 'x' to take the value 'v'.
%-----------------------------------------------------------------------------%


predicate at_least(int: n, array[int] of var int: x, int: v) =
    at_least_int(n, x, v);

predicate at_least(int: n, array[int] of var set of int: x, set of int: v) =
    at_least_set(n, x, v);

% Synonyms for the above.

predicate atleast(int: n, array[int] of var int: x, int: v) =
    at_least_int(n, x, v);

predicate atleast(int: n, array[int] of var set of int: x, set of int: v) =
    at_least_set(n, x, v);
% The actual definitions are in at_least.mzn.
% This file is used to handle the case where users include
% "atleast.mzn";
%
%-----------------------------------------------------------------------------%
% Requires at least 'n' variables in 'x' to take the value 'v'.
%-----------------------------------------------------------------------------%

predicate at_least_set(int: n, array[int] of var set of int: x, set of int: v) =
    sum(i in index_set(x)) ( bool2int(x[i] == v) ) >= n;

%-----------------------------------------------------------------------------%
% Requires that each pair of sets in s overlap in at most one element.
%-----------------------------------------------------------------------------%
predicate at_most1(array[int] of var set of int: s) =
    forall(i,j in index_set(s) where i < j) (
         card(s[i] intersect s[j]) <= 1 );

% Synonym for the above.

predicate atmost1(array[int] of var set of int: s) = at_most1(s);
% The actual definitions are in at_most1.mzn.
% This file is used to handle the case where users include
% "atmost1.mzn";
%
%-----------------------------------------------------------------------------%
% Requires at most 'n' variables in 'x' to take the value 'v'.
%-----------------------------------------------------------------------------%

predicate at_most_int(int: n, array[int] of var int: x, int: v) =
    sum(i in index_set(x)) ( bool2int(x[i] == v) ) <= n;

%-----------------------------------------------------------------------------%
% Requires at most 'n' variables in 'x' to take the value 'v'.
%-----------------------------------------------------------------------------%


predicate at_most(int: n, array[int] of var int: x, int: v) =
    at_most_int(n, x, v);

predicate at_most(int: n, array[int] of var set of int: x, set of int: v) =
    at_most_set(n, x, v);

% Synonyms for the above.

predicate atmost(int: n, array[int] of var int: x, int: v) =
    at_most_int(n, x, v);

predicate atmost(int: n, array[int] of var set of int: x, set of int: v) =
    at_most_set(n, x, v);
% The actual definitions are in atmost.mzn.
% This file is used to handle the case where users include
% "atmost.mzn";
%
%-----------------------------------------------------------------------------%
% Requires at most 'n' variables in 'x' to take the value 'v'.
%-----------------------------------------------------------------------------%

predicate at_most_set(int: n, array[int] of var set of int: x, set of int: v) =
    sum(i in index_set(x)) ( bool2int(x[i] == v) ) <= n;

%-----------------------------------------------------------------------------%
% Requires that each item i weight w[i], be put into bin[i] such
% that the sume of the weights in each bin b does not exceed capacity c[b].
% Assumptions:
% - forall i, w[i] >= 0
% - forall b, c[b] >= 0
%-----------------------------------------------------------------------------%

predicate bin_packing_capa(array[int] of int: c,
                           array[int] of var int: bin,
                           array[int] of int: w) =
    assert(index_set(bin) = index_set(w),
        "bin_packing_capa: the bin and weight arrays must have identical index sets",
    assert(lb_array(w) >= 0,
        "bin_packing_capa: the weights must be non-negative",
    assert(lb_array(c) >= 0,
        "bin_packing_capa: the capacities must be non-negative",
    
      forall( i in index_set(bin) ) (
            min(index_set(c)) <= bin[i] /\ bin[i] <= max(index_set(c))
      )
    /\
      forall( b in index_set(c) ) (
            c[b] >= sum ( i in index_set(bin) ) (
                w[i] * bool2int( bin[i] = b )
            )
      )
    )));

%-----------------------------------------------------------------------------%
%-----------------------------------------------------------------------------%
% Requires that each item i with weight w[i], be put into bin[i] such
% that the sum of the weights of the items in each bin b is equal to load[b].
% Assumptions:
% - forall i, w[i] >= 0
%-----------------------------------------------------------------------------%

predicate bin_packing_load(array[int] of var int: load,
                           array[int] of var int: bin,
                           array[int] of int: w) =
    assert(index_set(bin) == index_set(w),
        "bin_packing_load: the bin and weight arrays must have identical index sets",
    assert(lb_array(w) >= 0,
        "bin_packing_load: the weights must be non-negative",
        sum(load) = sum(w)
    /\  forall( i in index_set(bin) ) (
            min(index_set(load)) <= bin[i] /\ bin[i] <= max(index_set(load))
        )
    /\  forall( b in index_set(load) ) (
            load[b] = sum ( i in index_set(bin) ) (
                w[i] * bool2int( bin[i] = b )
            )
        )
    ));

%-----------------------------------------------------------------------------%
%-----------------------------------------------------------------------------%
% Requires that each item i with weight w[i], be put into bin[i] such that the
% sum of the weights of the items in each bin does not exceed the capacity c.
% Assumptions:
% - forall i, w[i] >=0
% - c >=0
%-----------------------------------------------------------------------------%

predicate bin_packing(int: c,
                      array[int] of var int: bin,
                      array[int] of int: w) =
    assert(index_set(bin) == index_set(w),
        "bin_packing: the bin and weight arrays must have identical index sets",
    assert(lb_array(w) >= 0,
        "bin_packing: the weights must be non-negative",
    assert(c >= 0, "bin_packing: capacity must be non-negative",
        forall( b in lb_array(bin)..ub_array(bin) ) (
            c >= sum ( i in index_set(bin) ) (
                w[i] * bool2int( bin[i] == b )
            )
        )
    )));

%-----------------------------------------------------------------------------%
%-----------------------------------------------------------------------------%
% Constrains the elements of 'x' to define a circuit where 'x[i] = j' means
% that 'j' is the successor of 'i'.
%-----------------------------------------------------------------------------%


predicate circuit(array[int] of var int: x) = 
    let { set of int: S = index_set(x),
          int: l = min(S),
	  int: n = card(S),
	  array[S] of var 1..n: order
        } in
    alldifferent(x) /\	
    alldifferent(order) /\
    forall(i in S)(x[i] != i) /\ 
    order[l] = 1 /\
    forall(i in S)(order[i] != n -> order[x[i]] = order[i] + 1) /\
    forall(i in S)(order[i] == n -> x[i] = l );

predicate circuit_reif(array[int] of var int: x, var bool: b) = 
    abort("Reified circuit/1 is not supported.");

%-----------------------------------------------------------------------------%
%-----------------------------------------------------------------------------%
%-----------------------------------------------------------------------------%
% Reflect an array of comparison values onto a comparison value variable using
% a lexicographic interpretation of the array.  The comparison values are
% encoded as follows:  > | = | <  as  -1 | 0 | +1.
% Uses of this constraint are generated by Cadmium transformations that
% simplify ordering constraints on expressions of complex types.
%-----------------------------------------------------------------------------%
predicate comparison_rel_array(array[int] of var -1..1: rels, var -1..1: rel) =
    let { int: l = min(index_set(rels)),
          int: u = max(index_set(rels)),
          array[l-1..u] of var -1..1: r }
    in
        r[l-1] = 0  % initial state (before first array position) is 'equal'
        /\
        forall (i in l..u) (
            % new state: as given array at current position if
            % previous state is 'equal', otherwise previous state
            %
            % r[i] = (if r[i-1] = 0 then rels[i] else r[i-1] endif)
            (r[i-1]  = 0 -> r[i] = rels[i])
            /\
            (r[i-1] != 0 -> r[i] = r[i-1])
        )
        /\
        r[u] = rel; % final state (at last array position)

%-----------------------------------------------------------------------------%
% Constrains 'c' to be the number of occurrences of 'y' in 'x'. 
%-----------------------------------------------------------------------------%

predicate count_eq(array[int] of var int: x, var int: y, var int: c) =
    c = sum(i in index_set(x)) ( bool2int(x[i] == y) );

%-----------------------------------------------------------------------------%
%-----------------------------------------------------------------------------%
%-----------------------------------------------------------------------------%
% Constrains 'c' to be greater than or equal to the number of occurrences of
% 'y' in 'x'.
%-----------------------------------------------------------------------------%

predicate count_geq(array[int] of var int: x, var int: y, var int: c) =
    c >= sum(i in index_set(x)) ( bool2int(x[i] == y) );

%-----------------------------------------------------------------------------%
%-----------------------------------------------------------------------------%
%-----------------------------------------------------------------------------%
% Constrains 'c' to be strictly greater than the number of occurrences of 'y'
% in 'x'.
%-----------------------------------------------------------------------------%

predicate count_gt(array[int] of var int: x, var int: y, var int: c) =
    c > sum(i in index_set(x)) ( bool2int(x[i] == y) );

%-----------------------------------------------------------------------------%
%-----------------------------------------------------------------------------%
%-----------------------------------------------------------------------------%
% Constrains 'c' to be less than or equal to the number of occurrences of
% 'y' in 'x'.
%-----------------------------------------------------------------------------%

predicate count_leq(array[int] of var int: x, var int: y, var int: c) =
    c <= sum(i in index_set(x)) ( bool2int(x[i] == y) );

%-----------------------------------------------------------------------------%
%-----------------------------------------------------------------------------%
%-----------------------------------------------------------------------------%
% Constrains 'c' to be strictly less than the number of occurrences of 'y'
% in 'x'.
%-----------------------------------------------------------------------------%

predicate count_lt(array[int] of var int: x, var int: y, var int: c) =
    c < sum(i in index_set(x)) ( bool2int(x[i] == y) );

%-----------------------------------------------------------------------------%
%-----------------------------------------------------------------------------%
%-----------------------------------------------------------------------------%
% Constrains 'c' to be the number of occurrences of 'y' in 'x'. 
%-----------------------------------------------------------------------------%


predicate count(array[int] of var int: x, var int: y, var int: c) =
    count_eq(x, y, c);

%-----------------------------------------------------------------------------%
%-----------------------------------------------------------------------------%

%-----------------------------------------------------------------------------%
% Constrains 'c' to not be the number of occurrences of 'y' in 'x'.
%-----------------------------------------------------------------------------%

predicate count_neq(array[int] of var int: x, var int: y, var int: c) =
    c != sum(i in index_set(x)) ( bool2int(x[i] == y) );

%-----------------------------------------------------------------------------%
%-----------------------------------------------------------------------------%

%-----------------------------------------------------------------------------%
% Requires that a set of tasks given by start times 's', durations 'd', and
% resource requirements 'r', never require more than a global resource bound
% 'b' at any one time.
% Assumptions:
% - forall i, d[i] >= 0 and r[i] >= 0
%-----------------------------------------------------------------------------%
predicate cumulative(array[int] of var int: s,
                     array[int] of var int: d,
                     array[int] of var int: r, var int: b) =
    assert(index_set(s) == index_set(d) /\ index_set(s) == index_set(r),
        "cumulative: the 3 array arguments must have identical index sets",
        assert(lb_array(d) >= 0 /\ lb_array(r) >= 0,
            "cumulative: durations and resource usages must be non-negative",
            let { 
               set of int: tasks = 
                  {i | i in index_set(s) where ub(r[i]) > 0 /\ ub(d[i]) > 0 },
               set of int: times =
                  min([ lb(s[i]) | i in tasks ]) ..
                  max([ ub(s[i]) + ub(d[i]) | i in tasks ]) 
                } 
            in
               forall( t in times ) (
                  b >= sum( i in tasks ) (
                     bool2int( s[i] <= t /\ t < s[i] + d[i] ) * r[i]
                  )
               )
        )
    );
%-----------------------------------------------------------------------------%
% Requires that the array 'x' is in decreasing order (duplicates are allowed).
%-----------------------------------------------------------------------------%

predicate decreasing_bool(array[int] of var bool: x) =
    forall(i in index_set(x) diff { min(index_set(x)) }) (x[i-1] >= x[i]);
%-----------------------------------------------------------------------------%
% Requires that the array 'x' is in decreasing order (duplicates are allowed).
%-----------------------------------------------------------------------------%

predicate decreasing_float(array[int] of var float: x) =
    forall(i in index_set(x) diff { min(index_set(x)) }) (x[i-1] >= x[i]);
%-----------------------------------------------------------------------------%
% Requires that the array 'x' is in decreasing order (duplicates are allowed).
%-----------------------------------------------------------------------------%

predicate decreasing_int(array[int] of var int: x) =
    forall(i in index_set(x) diff { min(index_set(x)) }) (x[i-1] >= x[i]);

%-----------------------------------------------------------------------------%
% Requires that the array 'x' is in decreasing order (duplicates are allowed).
%-----------------------------------------------------------------------------%


predicate decreasing(array[int] of var bool: x) =
    decreasing_bool(x);

predicate decreasing(array[int] of var float: x) =
    decreasing_float(x);

predicate decreasing(array[int] of var int: x) =
    decreasing_int(x);

predicate decreasing(array[int] of var set of int: x) =
    decreasing_set(x);

%-----------------------------------------------------------------------------%
% Requires that the array 'x' is in decreasing order (duplicates are allowed).
%-----------------------------------------------------------------------------%

predicate decreasing_set(array[int] of var set of int: x) =
    forall(i in index_set(x) diff { min(index_set(x)) }) (x[i-1] >= x[i]);

%-----------------------------------------------------------------------------%
% diffn: constrains rectangles, given by their origins and sizes, to be
% non-overlapping
%-----------------------------------------------------------------------------%

predicate diffn(array[int] of var int: x,
                array[int] of var int: y,
                array[int] of var int: dx,
                array[int] of var int: dy) =
    assert(
        index_set(x) = index_set(y)  /\
        index_set(x) = index_set(dx) /\
        index_set(x) = index_set(dy),
        "diffn: index set mismatch",
    forall(i,j in index_set(x) where i < j)(
        x[i] + dx[i] <= x[j] \/ y[i] + dy[i] <= y[j] \/
        x[j] + dx[j] <= x[i] \/ y[j] + dy[j] <= y[i]
    )
    );

%-----------------------------------------------------------------------------%
%-----------------------------------------------------------------------------%
% Requires that sets 's1' and 's2' do not intersect.
%-----------------------------------------------------------------------------%
predicate disjoint(var set of int: s1, var set of int: s2) =
    s1 intersect s2 == {};

%-----------------------------------------------------------------------------%
% Requires that 'card[i]' is the number of occurences of 'value[i]' in 'base'.
%
% XXX: currently the values in 'value' need not be distinct.  Perhaps they
%      should be?
%-----------------------------------------------------------------------------%
predicate distribute(array[int] of var int: card,
                     array[int] of var int: value,
                     array[int] of var int: base) =
    assert(index_set(card) == index_set(value),
        "distribute: card and value arrays must have identical index sets",
        forall (i in index_set(card)) (
            card[i] == sum(j in index_set(base)) (
                            bool2int(value[i] = base[j])
                       )
        )
    );

%-----------------------------------------------------------------------------%
% Requires that 'y' is the ith element of the array 'x'.
%-----------------------------------------------------------------------------%

predicate element_bool(var int: i, array[int] of var bool: x, var bool: y) =
    y = x[i];
%-----------------------------------------------------------------------------%
% Requires that 'y' is the ith element of the array 'x'.
%-----------------------------------------------------------------------------%

predicate element_float(var int: i, array[int] of var float: x, var float: y) =
    y = x[i];
%-----------------------------------------------------------------------------%
% Requires that 'y' is the ith element of the array 'x'.
%-----------------------------------------------------------------------------%

predicate element_int(var int: i, array[int] of var int: x, var int: y) =
    y = x[i];
%-----------------------------------------------------------------------------%
% Requires that 'y' is the ith element of the array 'x'.
%-----------------------------------------------------------------------------%


predicate element(var int: i, array[int] of var bool: x, var bool: y) =
    element_bool(i, x, y);

predicate element(var int: i, array[int] of var float: x, var float: y) =
    element_float(i, x, y);

predicate element(var int: i, array[int] of var int: x, var int: y) =
    element_int(i, x, y);

predicate element(var int: i, array[int] of var set of int: x,
        var set of int: y) =
    element_set(i, x, y);
%-----------------------------------------------------------------------------%
% Requires that 'y' is the ith element of the array 'x'.
%-----------------------------------------------------------------------------%

predicate element_set(var int: i, array[int] of var set of int: x,
        var set of int: y) =
    y = x[i];
%-----------------------------------------------------------------------------%
% Requires exactly 'n' variables in 'x' to take the value 'v'.
%-----------------------------------------------------------------------------%

predicate exactly_int(int: n, array[int] of var int: x, int: v) =
    n == sum(i in index_set(x)) ( bool2int(x[i] == v) );
%-----------------------------------------------------------------------------%
% Requires exactly 'n' variables in 'x' to take the value 'v'.
%-----------------------------------------------------------------------------%


predicate exactly(int: n, array[int] of var int: x, int: v) =
    exactly_int(n, x, v);

predicate exactly(int: n, array[int] of var set of int: x, set of int: v) =
    exactly_set(n, x, v);

%-----------------------------------------------------------------------------%
% Requires exactly 'n' variables in 'x' to take the value 'v'.
%-----------------------------------------------------------------------------%

predicate exactly_set(int: n, array[int] of var set of int: x, set of int: v) =
    n == sum(i in index_set(x)) ( bool2int(x[i] == v) );

% flatzinc.mzn
% vim: ft=zinc ts=4 sw=4 et tw=0
% <rafe@csse.unimelb.edu.au>
% Fri Nov 21 12:17:05 EST 2008
%
% A list of constraints recognised by the G12 FlatZinc interpreter.

predicate all_different(array [int] of var int: as);
predicate array_bool_and(array [int] of var bool: as, var bool: r);
predicate array_bool_element(var int: b, array [int] of bool: as,
    var bool: c);
predicate array_bool_or(array [int] of var bool: as, var bool: r);
predicate array_float_element(var int: b, array [int] of float: as,
    var float: c);
predicate array_int_element(var int: b, array [int] of int: as, var int: c);
predicate array_set_element(var int: b, array [int] of set of int: as,
    var set of int: c);
predicate array_var_bool_element(var int: b, array [int] of var bool: as,
    var bool: c);
predicate array_var_float_element(var int: b, array [int] of var float: as,
    var float: c);
predicate array_var_int_element(var int: b, array [int] of var int: as,
    var int: c);
predicate array_var_set_element(var int: b, array [int] of var set of int: as,
    var set of int: c);
predicate bool2int(var bool: a, var int: b);
predicate bool_and(var bool: a, var bool: b, var bool: r);
predicate bool_clause(array [int] of var bool: as,
    array [int] of var bool: bs);
predicate bool_eq(var bool: a, var bool: b);
predicate bool_eq_reif(var bool: a, var bool: b, var bool: r);
predicate bool_le(var bool: a, var bool: b);
predicate bool_le_reif(var bool: a, var bool: b, var bool: r);
predicate bool_lt(var bool: a, var bool: b);
predicate bool_lt_reif(var bool: a, var bool: b, var bool: r);
predicate bool_not(var bool: a, var bool: b);
predicate bool_or(var bool: a, var bool: b, var bool: r);
predicate bool_xor(var bool: a, var bool: b, var bool: r);
predicate float_eq(var float: a, var float: b);
predicate float_le(var float: a, var float: b);
predicate float_lin_eq(array [int] of float: as,
    array [int] of var float: bs, float: c);
predicate float_lin_le(array [int] of float: as,
    array [int] of var float: bs, float: c);
predicate float_lin_lt(array [int] of float: as,
    array [int] of var float: bs, float: c);
predicate float_lt(var float: a, var float: b);
predicate float_plus(var float: a, var float: b, var float: c);
predicate int_abs(var int: a, var int: b);
predicate int_div(var int: a, var int: b, var int: c);
predicate int_eq(var int: a, var int: b);
predicate int_eq_reif(var int: a, var int: b, var bool: r);
predicate int_le(var int: a, var int: b);
predicate int_le_reif(var int: a, var int: b, var bool: r);
predicate int_lin_eq(array [int] of int: as, array [int] of var int: bs,
    int: c);
predicate int_lin_eq_reif(array [int] of int: as, array [int] of var int: bs,
    int: c, var bool: r);
predicate int_lin_gt_reif(array [int] of int: as, array [int] of var int: bs,
    int: c, var bool: r);
predicate int_lin_le(array [int] of int: as, array [int] of var int: bs,
    int: c);
predicate int_lin_le_reif(array [int] of int: as, array [int] of var int: bs,
    int: c, var bool: r);
predicate int_lin_lt(array [int] of int: as, array [int] of var int: bs,
    int: c);
predicate int_lin_lt_reif(array [int] of int: as, array [int] of var int: bs,
    int: c, var bool: r);
predicate int_lin_ne(array [int] of int: as, array [int] of var int: bs,
    int: c);
predicate int_lin_ne_reif(array [int] of int: as, array [int] of var int: bs,
    int: c, var bool: r);
predicate int_lt(var int: a, var int: b);
predicate int_lt_reif(var int: a, var int: b, var bool: r);
predicate int_max(var int: a, var int: b, var int: c);
predicate int_min(var int: a, var int: b, var int: c);
predicate int_mod(var int: a, var int: b, var int: c);
predicate int_ne(var int: a, var int: b);
predicate int_ne_reif(var int: a, var int: b, var bool: r);
predicate int_plus(var int: a, var int: b, var int: c);
predicate int_times(var int: a, var int: b, var int: c);
predicate set_card(var set of int: a, var int: b);
predicate set_diff(var set of int: a, var set of int: b, var set of int: c);
predicate set_eq(var set of int: a, var set of int: b);
predicate set_eq_reif(var set of int: a, var set of int: b, var bool: r);
predicate set_in(var int: a, var set of int: b);
predicate set_in_reif(var int: a, var set of int: b, var bool: r);
predicate set_intersect(var set of int: a, var set of int: b,
    var set of int: c);
predicate set_le(var set of int: a, var set of int: b);
predicate set_lt(var set of int: a, var set of int: b);
predicate set_ne(var set of int: a, var set of int: b);
predicate set_ne_reif(var set of int: a, var set of int: b, var bool: r);
predicate set_subset(var set of int: a, var set of int: b);
predicate set_subset_reif(var set of int: a, var set of int: b, var bool: r);
predicate set_symdiff(var set of int: a, var set of int: b,
    var set of int: c);
predicate set_union(var set of int: a, var set of int: b, var set of int: c);

%-----------------------------------------------------------------------------%
% Requires that the number of occurences of 'i' in 'x' is 'counts[i]'.
% The elements of 'x' must take their values from 'cover'.
%-----------------------------------------------------------------------------%

predicate global_cardinality_closed(array[int] of var int: x,
                                    array[int] of int: cover,
                                    array[int] of var int: counts) =
    assert(index_set(cover) = index_set(counts),
        "global_cardinality_closed: " ++
        "cover and counts must have identical index sets",
    forall(i in index_set(x))( x[i] in { d | d in cover } )
    /\
    global_cardinality(x, cover, counts)
    /\
    % Implied constraint
    length(x) = sum(counts)
    );



%-----------------------------------------------------------------------------%
%-----------------------------------------------------------------------------%
%-----------------------------------------------------------------------------%
% Requires that the number of occurences of 'cover[i]' in 'x' is between
% 'lbound[i]' and 'ubound[i]'.
% The elements of 'x' must take their values from 'cover'.
%-----------------------------------------------------------------------------%

predicate global_cardinality_low_up_closed(array[int] of var int: x,
                                           array[int] of int: cover,
				           array[int] of int: lbound,
                                           array[int] of int: ubound) =
    forall(i in index_set(x))( x[i] in { d | d in cover } )
    /\
    global_cardinality_low_up(x, cover, lbound, ubound)
    /\
    % Implied condition
    length(x) in sum(lbound)..sum(ubound);



%-----------------------------------------------------------------------------%
%-----------------------------------------------------------------------------%
%-----------------------------------------------------------------------------%
% Requires that for all `i', the value `cover[i]' appears at least `lbound[i]'
% and at most `ubound[i]' times in the array `x'.
%-----------------------------------------------------------------------------%

predicate global_cardinality_low_up(array[int] of var int: x,
                                    array[int] of int: cover,
				    array[int] of int: lbound,
                                    array[int] of int: ubound) =
     forall(i in index_set(cover)) (
         sum(j in index_set(x)) ( bool2int(x[j] = cover[i]) )
         in lbound[i]..ubound[i]
     );
%-----------------------------------------------------------------------------%
% Requires that the number of occurrences of 'cover[i]' in 'x' is 'counts[i]'.
%-----------------------------------------------------------------------------%


predicate global_cardinality(array[int] of var int: x,
	  		     array[int] of int: cover,
                             array[int] of var int: counts) =
    assert(index_set(cover) = index_set(counts),
        "global_cardinality: cover and counts must have identical index sets",
    forall(i in index_set(cover))( count(x, cover[i], counts[i]) )
    /\
    % Implied constraint
    length(x) >= sum(counts)
    );

%-----------------------------------------------------------------------------%
%-----------------------------------------------------------------------------%
%-----------------------------------------------------------------------------%
% Requires that the array 'x' is in increasing order (duplicates are allowed).
%-----------------------------------------------------------------------------%

predicate increasing_bool(array[int] of var bool: x) =
    forall(i in index_set(x) diff { min(index_set(x)) }) (x[i-1] <= x[i]);
%-----------------------------------------------------------------------------%
% Requires that the array 'x' is in increasing order (duplicates are allowed).
%-----------------------------------------------------------------------------%

predicate increasing_float(array[int] of var float: x) =
    forall(i in index_set(x) diff { min(index_set(x)) }) (x[i-1] <= x[i]);
%-----------------------------------------------------------------------------%
% Requires that the array 'x' is in increasing order (duplicates are allowed).
%-----------------------------------------------------------------------------%

predicate increasing_int(array[int] of var int: x) =
    forall(i in index_set(x) diff { min(index_set(x)) }) (x[i-1] <= x[i]);

%-----------------------------------------------------------------------------%
% Requires that the array 'x' is in increasing order (duplicates are allowed).
%-----------------------------------------------------------------------------%


predicate increasing(array[int] of var bool: x) =
    increasing_bool(x);

predicate increasing(array[int] of var float: x) =
    increasing_float(x);

predicate increasing(array[int] of var int: x) =
    increasing_int(x);

predicate increasing(array[int] of var set of int: x) =
    increasing_set(x);

%-----------------------------------------------------------------------------%
% Requires that the array 'x' is in increasing order (duplicates are allowed).
%-----------------------------------------------------------------------------%

predicate increasing_set(array[int] of var set of int: x) =
    forall(i in index_set(x) diff { min(index_set(x)) }) (x[i-1] <= x[i]);

%-----------------------------------------------------------------------------%
% Requires that array of int variables 'x' and array of set variables 'y'
% are related such that '(x[i] = j) <-> (i in y[j])'.
%-----------------------------------------------------------------------------%

predicate int_set_channel(array[int] of var int: x,
                          array[int] of var set of int: y) =
    forall(i in index_set(x)) (x[i] in index_set(y)) /\
    forall(j in index_set(y)) (y[j] subset index_set(x)) /\
    forall(i in index_set(x), j in index_set(y)) (x[i] = j <-> i in y[j]);

%-----------------------------------------------------------------------------%
%-----------------------------------------------------------------------------%
%-----------------------------------------------------------------------------%
% Constrains two arrays of int variables, 'f' and 'invf', to represent
% inverse functions.  All the values in each array must be within the index
% set of the other array.
%-----------------------------------------------------------------------------%
predicate inverse(array[int] of var int: f,
                  array[int] of var int: invf) =
    forall(i in index_set(f), j in index_set(invf)) (
           f[i] in index_set(invf) /\
        invf[j] in index_set(f   ) /\
        (j == f[i] <-> i == invf[j])
    );

%-----------------------------------------------------------------------------%
% Constrains two arrays of set of int variables, 'f' and 'invf', so that
% a 'j in f[i]' iff 'i in invf[j]'.  All the values in each array's sets
% must be within the index set of the other array.
%-----------------------------------------------------------------------------%
predicate inverse_set(array[int] of var set of int: f,
                      array[int] of var set of int: invf) =
    forall(i in index_set(f)) (
           f[i] subset index_set(invf)
    ) /\
    forall(j in index_set(invf)) (
        invf[j] subset index_set(f)
    ) /\
    forall(i in index_set(f), j in index_set(invf)) (
        (j in f[i] <-> i in invf[j])
    );

%-----------------------------------------------------------------------------%
% Require adjacent rows and adjacent columns in the array 'x' to be
% lexicographically ordered.  Adjacent rows and adjacent columns may be equal.
%-----------------------------------------------------------------------------%


predicate lex2(array[int, int] of var int: x) =
    let {
        int: lbx1 = min(index_set_1of2(x)),
        int: ubx1 = max(index_set_1of2(x)),
        int: lbx2 = min(index_set_2of2(x)),
        int: ubx2 = max(index_set_2of2(x))
    } in (
        
        forall(i in lbx1 + 1 .. ubx1) (
            lex_lesseq([x[i - 1, j] | j in index_set_2of2(x)],
                       [x[i,     j] | j in index_set_2of2(x)]
            )
        )
        
        /\

        forall(j in lbx2 + 1 .. ubx2) (
            lex_lesseq([x[i, j - 1] | i in index_set_1of2(x)],
                       [x[i, j    ] | i in index_set_1of2(x)]
            )
        )
    );
%-----------------------------------------------------------------------------%
% Requires that the array 'x' is lexicographically greater than or equal to
% array 'y'.  Compares them from first to last element, regardless of indices.
%-----------------------------------------------------------------------------%


predicate lex_greatereq(array[int] of var bool: x,
                        array[int] of var bool: y) =
    lex_lesseq(y, x);

predicate lex_greatereq(array[int] of var int: x,
                      array[int] of var int: y) =
    lex_lesseq(y, x);
 
predicate lex_greatereq(array[int] of var float: x,
                      array[int] of var float: y) =
    lex_lesseq(y, x);

predicate lex_greatereq(array[int] of var set of int: x,
                      array[int] of var set of int: y) =
    lex_lesseq(y, x);
%-----------------------------------------------------------------------------%
% Requires that the array 'x' is strictly lexicographically greater than
% array 'y'.
% Compares them from first to last element, regardless of indices
%-----------------------------------------------------------------------------%


predicate lex_greater(array[int] of var bool: x,
                      array[int] of var bool: y) = 
    lex_less(y, x);                     

predicate lex_greater(array[int] of var int: x,
                      array[int] of var int: y) =
    lex_less(y, x);
 
predicate lex_greater(array[int] of var float: x,
                      array[int] of var float: y) =
    lex_less(y, x);

predicate lex_greater(array[int] of var set of int: x,
                      array[int] of var set of int: y) =
    lex_less(y, x);
%-----------------------------------------------------------------------------%
% Requires that the array 'x' is strictly lexicographically less than array 'y'.
% Compares them from first to last element, regardless of indices
%-----------------------------------------------------------------------------%

predicate lex_less_bool(array[int] of var bool: x,
                       array[int] of var bool: y) =
    let { int: lx = min(index_set(x)),
          int: ux = max(index_set(x)),
          int: ly = min(index_set(y)),
          int: uy = max(index_set(y)),
          int: size = max(ux - lx, uy - ly),
          array[0..size+1] of var bool: b }
    in
    b[0]
    /\
    forall(i in 0..size) (
        b[i] = ( x[lx + i] <= y[ly + i]
                 /\
                (x[lx + i] <  y[ly + i] \/ b[i+1]) )
    )
    /\
    b[size + 1] = (ux - lx < uy - ly);

predicate lex_lt_bool(array[int] of var bool: x,
                     array[int] of var bool: y) =
          lex_less(x, y);

%-----------------------------------------------------------------------------%
%-----------------------------------------------------------------------------%
% Requires that the array 'x' is lexicographically less than or equal to
% array 'y'.  Compares them from first to last element, regardless of indices
%-----------------------------------------------------------------------------%

predicate lex_lesseq_bool(array[int] of var bool: x,
                         array[int] of var bool: y) =
    let { int: lx = min(index_set(x)),
          int: ux = max(index_set(x)),
          int: ly = min(index_set(y)),
          int: uy = max(index_set(y)),
          int: size = max(ux - lx, uy - ly),
          array[0..size] of var bool: b }
          % b[i] is true if the lexicographical order holds from position i on.
    in
    b[0]
    /\
    forall(i in 0..size) (
        b[i] = ( x[lx + i] <= y[ly + i]
                 /\
                 if i = size then true
                 else x[lx + i] <  y[ly + i] \/ b[i+1] endif
               )
    );


predicate lex_leq_bool(array[int] of var bool: x,
                       array[int] of var bool: y) =
          lex_lesseq(x, y);

%-----------------------------------------------------------------------------%
%-----------------------------------------------------------------------------%
% Requires that the array 'x' is lexicographically less than or equal to
% array 'y'.  Compares them from first to last element, regardless of indices
%-----------------------------------------------------------------------------%

predicate lex_lesseq_float(array[int] of var float: x,
                           array[int] of var float: y) =
    let { int: lx = min(index_set(x)),
          int: ux = max(index_set(x)),
          int: ly = min(index_set(y)),
          int: uy = max(index_set(y)),
          int: size = max(ux - lx, uy - ly),
          array[0..size] of var bool: b }
          % b[i] is true if the lexicographical order holds from position i on.
    in
    b[0]
    /\
    forall(i in 0..size) (
        b[i] = ( x[lx + i] <= y[ly + i]
                 /\
                 if i = size then true
                 else x[lx + i] <  y[ly + i] \/ b[i+1] endif
               )
    );


predicate lex_leq_float(array[int] of var float: x,
                        array[int] of var float: y) =
          lex_lesseq(x, y);

%-----------------------------------------------------------------------------%
%-----------------------------------------------------------------------------%
% Requires that the array 'x' is lexicographically less than or equal to
% array 'y'.  Compares them from first to last element, regardless of indices
%-----------------------------------------------------------------------------%

predicate lex_lesseq_int(array[int] of var int: x,
                         array[int] of var int: y) =
    let { int: lx = min(index_set(x)),
          int: ux = max(index_set(x)),
          int: ly = min(index_set(y)),
          int: uy = max(index_set(y)),
          int: size = max(ux - lx, uy - ly),
          array[0..size] of var bool: b }
          % b[i] is true if the lexicographical order holds from position i on.
    in
    b[0]
    /\
    forall(i in 0..size) (
        b[i] = ( x[lx + i] <= y[ly + i]
                 /\
                 if i = size then true
                 else x[lx + i] <  y[ly + i] \/ b[i+1] endif
               )
    );


predicate lex_leq_int(array[int] of var int: x,
                      array[int] of var int: y) =
          lex_lesseq(x, y);

%-----------------------------------------------------------------------------%
%-----------------------------------------------------------------------------%
% Requires that the array 'x' is lexicographically less than or equal to
% array 'y'.  Compares them from first to last element, regardless of indices.
%-----------------------------------------------------------------------------%


predicate lex_lesseq(array[int] of var bool: x,
                     array[int] of var bool: y) = 
    lex_lesseq_bool(x, y);

predicate lex_lesseq(array[int] of var float: x,
                     array[int] of var float: y) =
    lex_lesseq_float(x, y);

predicate lex_lesseq(array[int] of var int: x,
                     array[int] of var int: y) =
    lex_lesseq_int(x, y);

predicate lex_lesseq(array[int] of var set of int: x,
                     array[int] of var set of int: y) =
    lex_lesseq_set(x, y);

% Alternative names for the above.
%
predicate lex_leq(array[int] of var bool: x, array[int] of var bool: y) =
	lex_lesseq(x, y);

predicate lex_leq(array[int] of var int: x, array[int] of var int: y) =
	lex_lesseq(x, y);

predicate lex_leq(array[int] of var float: x, array[int] of var float: y) =
	lex_lesseq(x, y);

predicate lex_leq(array[int] of var set of int: x,
                  array[int] of var set of int: y) =
	lex_lesseq(x, y);

%-----------------------------------------------------------------------------%
% Requires that the array 'x' is lexicographically less than or equal to
% array 'y'.  Compares them from first to last element, regardless of indices
%-----------------------------------------------------------------------------%

predicate lex_lesseq_set(array[int] of var set of int: x,
                         array[int] of var set of int: y) =
    let { int: lx = min(index_set(x)),
          int: ux = max(index_set(x)),
          int: ly = min(index_set(y)),
          int: uy = max(index_set(y)),
          int: size = max(ux - lx, uy - ly),
          array[0..size] of var bool: b }
          % b[i] is true if the lexicographical order holds from position i on.
    in
    b[0]
    /\
    forall(i in 0..size) (
        b[i] = ( x[lx + i] <= y[ly + i]
                 /\
                 if i = size then true
                 else x[lx + i] <  y[ly + i] \/ b[i+1] endif
               )
    );


predicate lex_leq_set(array[int] of var set of int: x,
                      array[int] of var set of int: y) =
          lex_lesseq(x, y);

%-----------------------------------------------------------------------------%
%-----------------------------------------------------------------------------%
% Requires that the array 'x' is strictly lexicographically less than array 'y'.
% Compares them from first to last element, regardless of indices
%-----------------------------------------------------------------------------%

predicate lex_less_float(array[int] of var float: x,
                         array[int] of var float: y) =
    let { int: lx = min(index_set(x)),
          int: ux = max(index_set(x)),
          int: ly = min(index_set(y)),
          int: uy = max(index_set(y)),
          int: size = max(ux - lx, uy - ly),
          array[0..size+1] of var bool: b }
    in
    b[0]
    /\
    forall(i in 0..size) (
        b[i] = ( x[lx + i] <= y[ly + i]
                 /\
                (x[lx + i] <  y[ly + i] \/ b[i+1]) )
    )
    /\
    b[size + 1] = (ux - lx < uy - ly);


predicate lex_lt_float(array[int] of var float: x,
                       array[int] of var float: y) =
          lex_less(x, y);

%-----------------------------------------------------------------------------%
%-----------------------------------------------------------------------------%
% Requires that the array 'x' is strictly lexicographically less than array 'y'.
% Compares them from first to last element, regardless of indices
%-----------------------------------------------------------------------------%

predicate lex_less_int(array[int] of var int: x,
                       array[int] of var int: y) =
    let { int: lx = min(index_set(x)),
          int: ux = max(index_set(x)),
          int: ly = min(index_set(y)),
          int: uy = max(index_set(y)),
          int: size = max(ux - lx, uy - ly),
          array[0..size+1] of var bool: b }
    in
    b[0]
    /\
    forall(i in 0..size) (
        b[i] = ( x[lx + i] <= y[ly + i]
                 /\
                (x[lx + i] <  y[ly + i] \/ b[i+1]) )
    )
    /\
    b[size + 1] = (ux - lx < uy - ly);


predicate lex_lt_int(array[int] of var int: x,
                     array[int] of var int: y) =
          lex_less(x, y);

%-----------------------------------------------------------------------------%
%-----------------------------------------------------------------------------%
% Requires that the array 'x' is strictly lexicographically less than array 'y'.
% Compares them from first to last element, regardless of indices.
%-----------------------------------------------------------------------------%


predicate lex_less(array[int] of var bool: x,
                   array[int] of var bool: y) =
    lex_less_bool(x, y);

predicate lex_less(array[int] of var int: x,
                   array[int] of var int: y) =
    lex_less_int(x, y);

predicate lex_less(array[int] of var float: x,
                   array[int] of var float: y) =
    lex_less_float(x, y);

predicate lex_less(array[int] of var set of int: x,
                   array[int] of var set of int: y) =
    lex_less_set(x, y);

% Alternative names for the above.
%
predicate lex_lt(array[int] of var bool: x, array[int] of var bool: y) =
	lex_less(x, y);

predicate lex_lt(array[int] of var int: x, array[int] of var int: y) =
	lex_less(x, y);

predicate lex_lt(array[int] of var float: x, array[int] of var float: y) =
	lex_less(x, y);

predicate lex_lt(array[int] of var set of int: x,
                  array[int] of var set of int: y) =
	lex_less(x, y);

%-----------------------------------------------------------------------------%
% Requires that the array 'x' is strictly lexicographically less than array 'y'.
% Compares them from first to last element, regardless of indices
%-----------------------------------------------------------------------------%

predicate lex_less_set(array[int] of var set of int: x,
                       array[int] of var set of int: y) =
    let { int: lx = min(index_set(x)),
          int: ux = max(index_set(x)),
          int: ly = min(index_set(y)),
          int: uy = max(index_set(y)),
          int: size = max(ux - lx, uy - ly),
          array[0..size+1] of var bool: b }
    in
    b[0]
    /\
    forall(i in 0..size) (
        b[i] = ( x[lx + i] <= y[ly + i]
                 /\
                (x[lx + i] <  y[ly + i] \/ b[i+1]) )
    )
    /\
    b[size + 1] = (ux - lx < uy - ly);


predicate lex_lt_set(array[int] of var set of int: x,
                     array[int] of var set of int: y) =
          lex_less(x, y);

%-----------------------------------------------------------------------------%
%-----------------------------------------------------------------------------%
% the array of booleans 'b' is a representation of the set 's'
%-----------------------------------------------------------------------------%
predicate link_set_to_booleans(var set of int: s, array[int] of var bool: b) =
    assert(ub(s) subset index_set(b),
        "link_set_to_booleans: the index set of b must be a superset of the possible values of s",
        forall(i in index_set(b)) ( b[i] <-> i in s )
    );

%-----------------------------------------------------------------------------%
% Constrains 'm' to be the maximum of the values in 'x'.
% Assumptions: |x| > 0.
%-----------------------------------------------------------------------------%

predicate maximum_float(var float: m, array[int] of var float: x) =
    let { int: l = min(index_set(x)),
          int: u = max(index_set(x)),
          float: ly = lb_array(x),
          float: uy = ub_array(x),
          array[l..u] of var ly..uy: y } in
    y[l] = x[l] /\
    m = y[u] /\
    forall (i in l+1 .. u) ( y[i] == max(x[i],y[i-1]) );
%-----------------------------------------------------------------------------%
% Constrains 'm' to be the maximum of the values in 'x'.
% Assumptions: |x| > 0.
%-----------------------------------------------------------------------------%

predicate maximum_int(var int: m, array[int] of var int: x) =
    let { int: l = min(index_set(x)),
          int: u = max(index_set(x)),
          int: ly = lb_array(x),
          int: uy = ub_array(x),
          array[l..u] of var ly..uy: y } in
    y[l] = x[l] /\
    m = y[u] /\
    forall (i in l+1 .. u) ( y[i] == max(x[i],y[i-1]) );
%-----------------------------------------------------------------------------%
% Constrains 'm' to be the maximum of the values in 'x'.
% Assumptions: |x| > 0.
%-----------------------------------------------------------------------------%


predicate maximum(var int: m, array[int] of var int: x) =
    maximum_int(m, x);

predicate maximum(var float: m, array[int] of var float: x) =
    maximum_float(m, x);

    % XXX: currently doesn't work:  same problem as 'minimum' above.
%predicate maximum(var set of int: m, array[int] of var set of int: x) =
%    let { int: l = min(index_set(x)),
%          int: u = max(index_set(x)),
%          set of int: uy = ub(x),
%          array[l..u] of var set of uy: y } in
%    y[l] = x[l] /\
%    m = y[u] /\
%    forall (i in l+1 .. u) ( y[i] == max(x[i],y[i-1]) );

%-----------------------------------------------------------------------------%
% Requires that 'y' occurs in the array or set 'x'.
%-----------------------------------------------------------------------------%

predicate member_bool(array[int] of var bool: x, var bool: y) =
    exists(i in index_set(x)) ( x[i] == y );
%-----------------------------------------------------------------------------%
% Requires that 'y' occurs in the array or set 'x'.
%-----------------------------------------------------------------------------%

predicate member_float(array[int] of var float: x, var float: y) =
    exists(i in index_set(x)) ( x[i] == y );
%-----------------------------------------------------------------------------%
% Requires that 'y' occurs in the array or set 'x'.
%-----------------------------------------------------------------------------%

predicate member_int(array[int] of var int: x, var int: y) =
    exists(i in index_set(x)) ( x[i] == y );
%-----------------------------------------------------------------------------%
% Requires that 'y' occurs in the array or set 'x'.
%-----------------------------------------------------------------------------%


predicate member(array[int] of var bool: x, var bool: y) =
    member_bool(x, y);

predicate member(array[int] of var float: x, var float: y) =
    member_float(x, y);

predicate member(array[int] of var int: x, var int: y) =
    member_int(x, y);

predicate member(array[int] of var set of int: x, var set of int: y) =
    member_set(x, y);

predicate member(var set of int: x, var int: y) =
    set_member(x, y);

%-----------------------------------------------------------------------------%
% Requires that 'y' occurs in the array or set 'x'.
%-----------------------------------------------------------------------------%

predicate member_set(array[int] of var set of int: x, var set of int: y) =
    exists(i in index_set(x)) ( x[i] == y );
%-----------------------------------------------------------------------------%
% Constrains 'm' to be the minimum of the values in 'x'.
% Assumptions: |x| > 0.
%-----------------------------------------------------------------------------%

predicate minimum_float(var float: m, array[int] of var float: x) =
    let { int: l = min(index_set(x)),
          int: u = max(index_set(x)),
          float: ly = lb_array(x),
          float: uy = ub_array(x),
          array[l..u] of var ly..uy: y } in
    y[l] = x[l] /\
    m = y[u] /\
    forall (i in l+1 .. u) ( y[i] == min(x[i],y[i-1]) );

%-----------------------------------------------------------------------------%
% Constrains 'm' to be the minimum of the values in 'x'.
% Assumptions: |x| > 0.
%-----------------------------------------------------------------------------%

predicate minimum_int(var int: m, array[int] of var int: x) =
    let { int: l = min(index_set(x)),
          int: u = max(index_set(x)),
          int: ly = lb_array(x),
          int: uy = ub_array(x),
          array[l..u] of var ly..uy: y } in
    y[l] = x[l] /\
    m = y[u] /\
    forall (i in l+1 .. u) ( y[i] == min(x[i],y[i-1]) );
%-----------------------------------------------------------------------------%
% Constrains 'm' to be the minimum of the values in 'x'.
% Assumptions: |x| > 0.
%-----------------------------------------------------------------------------%


predicate minimum(var float: m, array[int] of var float: x) =
    minimum_float(m, x);

predicate minimum(var int: m, array[int] of var int: x) =
    minimum_int(m, x);

    % XXX: currently doesn't work:  'uy' is used as a type but that isn't
    %      allowed because it's not a global variable.
%predicate minimum(var set of int: m, array[int] of var set of int: x) =
%    let { int: l = min(index_set(x)),
%          int: u = max(index_set(x)),
%          set of int: uy = ub(x),
%          array[l..u] of var set of uy: y } in
%    y[l] = x[l] /\
%    m = y[u] /\
%    forall (i in l+1 .. u) ( y[i] == min(x[i],y[i-1]) );

%-----------------------------------------------------------------------------%
% Requires that the number of distinct values in 'x' is 'n'.
%-----------------------------------------------------------------------------%
predicate nvalue(var int: n, array[int] of var int: x) =
    let { int: lx = lb_array(x),
          int: ux = ub_array(x),
        } in
        n == sum(j in lx..ux) (
	   bool2int(exists(i in index_set(x)) ( x[i] = j ))
        );
%-----------------------------------------------------------------------------%
% Partitions a 'universe' into disjoint sets.
%-----------------------------------------------------------------------------%
predicate partition_set(array[int] of var set of int: S,
                        set of int: universe) =
    all_disjoint(S) /\ universe == array_union(i in index_set(S)) ( S[i] );

%-----------------------------------------------------------------------------%
% Requires that the image of function 'x' (represented as an array) on set
% of values 's' is 't'.  ub(s) must be a subset of index_set(x) otherwise an
% assertion failure will occur.
%-----------------------------------------------------------------------------%
predicate range(array[int] of var int: x, var set of int: s,
                                          var set of int: t) =
    assert(ub(s) subset index_set(x),
        "range: upper bound of 's' must be a subset of the index set of 'x'",
    
        % All values in 's' must map to a value in 't'.
        forall(i in ub(s)) (
            i in s -> x[i] in t
        ) /\
        % All values in 't' must be mapped from a value in 's'.
        forall(i in ub(t)) (
            i in t -> exists(j in ub(s)) ( j in s /\ x[j] == i )
        )
    );

% This file is intentionally empty since there are no standard redefinitions
% of FlatZinc built-ins.  The file has to exist because globals.mzn includes
% it.
%-----------------------------------------------------------------------------%
% The sequence of values in array 'x' (which must all be in the range 1..S)
% is accepted by the DFA of 'Q' states with input 1..S and transition
% function 'd' (which maps (1..Q, 1..S) -> 0..Q)) and initial state 'q0'
% (which must be in 1..Q) and accepting states 'F' (which all must be in
% 1..Q).  We reserve state 0 to be an always failing state.
%-----------------------------------------------------------------------------%
predicate regular(array[int] of var int: x, int: Q, int: S,
                  array[int,int] of int: d, int: q0, set of int: F) =
    assert(Q > 0,
        "regular: 'Q' must be greater than zero",

    assert(S > 0,
        "regular: 'S' must be greater than zero",

    assert(index_set_1of2(d) = 1..Q /\ index_set_2of2(d) == 1..S,
        "regular: the transition function 'd' must be [1..Q,1..S]",

    assert(forall([d[i, j] in 0..Q | i in 1..Q, j in 1..S]),
        "regular: transition function 'd' points to states outside 0..Q",

        % Nb: we need the parentheses around the expression otherwise the
        % parser thinks it's a generator call!
    assert((q0 in 1..Q),
        "regular: start state 'q0' not in 1..Q",

    assert(F subset 1..Q,
        "regular: final states in 'F' contain states outside 1..Q",

        let {
            % If x has index set m..n-1, then a[m] holds the initial state
            % (q0), and a[i+1] holds the state we're in after  processing
            % x[i].  If a[n] is in F, then we succeed (ie. accept the string).
              int: m = min(index_set(x)),
              int: n = max(index_set(x)) + 1,
              array[m..n] of var 1..Q: a
            } in
        a[m] = q0 /\                    % Set a[0].
        forall(i in index_set(x)) (
            x[i] in 1..S /\             % Do this in case it's a var.
            a[i+1] = d[a[i], x[i]]      % Determine a[i+1].
        ) /\
        a[n] in F                       % Check the final state is in F.

     ))))));
%-----------------------------------------------------------------------------%
% Requires that 'x[i] in t' for all 'i in s'
%-----------------------------------------------------------------------------%

predicate roots(array[int] of var int: x, var set of int: s,
                                          var set of int: t) =
    assert(ub(s) subset index_set(x),
        "roots: upper bound of 's' must be a subset of the index set of 'x'",

        % All values in 's' must map to a value in 't'.
        forall(i in ub(s)) (
            i in s -> x[i] in t
        ) /\
        forall(i in ub(t)) (
            i in t -> forall(j in index_set(x)) (x[j] = i -> j in s )
        )
    );

%-----------------------------------------------------------------------------%
%-----------------------------------------------------------------------------%
%-----------------------------------------------------------------------------%
% Requires that 'y' occurs in the array or set 'x'.
%-----------------------------------------------------------------------------%

predicate set_member(var set of int: x, var int: y) =
    y in x;

%-----------------------------------------------------------------------------%
% Requires that in each subsequence 'vs[i], ..., vs[i + seq - 1]' the sum of the
% values belongs to the interval ['low', 'up'].
%-----------------------------------------------------------------------------%

predicate sliding_sum(int: low, int: up, int: seq, array[int] of var int: vs) =
    let { int: lx = min(index_set(vs)),
          int: ux = max(index_set(vs)),
        } in
        forall (i in lx .. ux - seq + 1) ( 
            let { 
                    var int: sum_of_l = sum(j in i..i + seq - 1) (vs[j]) 
            } in
                low <= sum_of_l /\ sum_of_l <= up
        );
%-----------------------------------------------------------------------------%
% Requires that the multiset of values in 'x' are the same as the
% multiset of values in 'y' but 'y' is in sorted order.
%-----------------------------------------------------------------------------%
predicate sort(array[int] of var int: x, array[int] of var int: y) =
    assert(card(index_set(x)) == card(index_set(y)),
        "sort: x and y must be same sized arrays",
        let { int: lx = min(index_set(x)),
              int: ux = max(index_set(x)),
              int: ly = min(index_set(y)),
              int: uy = max(index_set(y)),
              array[lx..ux] of var ly..uy: p } in
        forall(i in index_set(x)) ( y[p[i]] == x[i] ) /\
        alldifferent(p) /\
        increasing(y)
    );

%-----------------------------------------------------------------------------%
% MiniZinc standard library.
%-----------------------------------------------------------------------------%
% This file contains built-in operations that can be expressed in MiniZinc,
% and so are not implemented as true built-ins within the compiler.

%-----------------------------------------------------------------------------%
%
% Search annotations
%

annotation seq_search(array[int] of ann: s);

annotation int_search(
    array[int] of var int: vars,
    ann: select,
    ann: choice,
    ann: explore,
);

annotation int_search_all(
    ann: select,
    ann: choice,
    ann: explore
);

annotation int_search_bd(
    ann: a,
    ann: b,
    ann: c,
    ann: d
);

annotation bool_search(
    array[int] of var bool: vars,
    ann: select,
    ann: choice,
    ann: explore
);

annotation float_search(
    array[int] of var float: vars,
    float: prec,
    ann: select,
    ann: choice,
    ann: explore
);

annotation set_search(
    array[int] of var set of int: vars,
    ann: select,
    ann: choice,
    ann: explore
);

annotation labelling_ff;

%-----------------------------------------------------------------------------%
%
% Variable selection annotations.
%

annotation input_order;
annotation first_fail;
annotation anti_first_fail;
annotation smallest;
annotation largest;
annotation occurrence;
annotation most_constrained;
annotation max_regret;
annotation dom_w_deg;
annotation impact;

%-----------------------------------------------------------------------------%
%
% Domain reduction strategies.
%

annotation indomain;
annotation indomain_min;
annotation indomain_max;
annotation indomain_middle;
annotation indomain_median;
annotation indomain_random;
annotation indomain_split;
annotation indomain_split_random;
annotation indomain_reverse_split;
annotation indomain_interval;
annotation outdomain_min;
annotation outdomain_median;
annotation outdomain_random;

%-----------------------------------------------------------------------------%
%
% Exploration strategies.
%

annotation complete;
annotation bbs(int: s);
annotation fail;
annotation lds(int: d);
annotation credit(int: c, ann: explore);
annotation dbs(int: d, ann: explore);
annotation restart_geometric(float: i, float: s, ann: search);
annotation limit(ann: measure, int: value, ann: search);
annotation time;

%-----------------------------------------------------------------------------%
%-----------------------------------------------------------------------------%

% Include solver-specific redefinitions for any FlatZinc built-ins.
%

%-----------------------------------------------------------------------------%
%-----------------------------------------------------------------------------%
%-----------------------------------------------------------------------------%
% MiniZinc standard library (Draft for version MiniZinc 2.0)
%-----------------------------------------------------------------------------%
% This file contains built-in operations that can be expressed in MiniZinc,
% and so are not implemented as true built-ins within the compiler.

%-----------------------------------------------------------------------------%
%
% Search annotations
%

annotation bool_search(
    array[int] of var bool: vars,
    ann: select,
    ann: choice
);

annotation int_search(
    array[int] of var int: vars,
    ann: select,
    ann: choice
);

annotation set_search(
    array[int] of var set of int: vars,
    ann: select,
    ann: choice,
);

annotation float_search(
    array[int] of var float: vars,
    float: prec,
    ann: select,
    ann: choice
);

annotation int_search_all(
    ann: select,
    ann: choice
);

annotation set_search_all(
    ann: select,
    ann: choice
);

annotation seq_search(array[int] of ann: s);
annotation par_search(array[int] of ann: s);

annotation sample_search(
    ann: select,
    ann: limit
);

annotation backdoor_search(
    ann: select,
    float: ratio,
    ann: limit,
    array[int] of ann: s
);

% Search with limits and restart annotations
annotation limit_search(ann: measure, int: value, ann: search);
annotation restart_geometric(float: factor, float: value, ann: search);
annotation restart_luby(float: factor, float: value, ann: search);

% Sequential variable selection strategies
annotation seq_vss(array[int] of ann: select);

% Multiple domain selection strategies
annotation dss(array[int] of ann: choice);

% Combine and manipulate variable selection scores
annotation weight_score(ann: select, float: weight);
annotation sum_score(array[int] of ann: weight_score);

% Measures to limit search (e.g., run-time, number of search nodes, fails)
annotation measure;

%-----------------------------------------------------------------------------%
%
% Variable selection annotations.
%

annotation input_order;
annotation reverse_input_order;

annotation random_order;

annotation min_lb;
annotation min_ub;
annotation max_lb;
annotation max_ub;

annotation min_dom_size;
annotation max_dom_size;

annotation min_degree;
annotation max_degree;

annotation min_lb_regret;
annotation max_lb_regret;
annotation min_ub_regret;
annotation max_ub_regret;

annotation min_dom_size_degree;
annotation max_dom_size_degree;
annotation min_dom_size_weighted_degree;
annotation max_dom_size_weighted_degree;

annotation min_impact;
annotation max_impact;

annotation min_activity;
annotation max_activity;

%-----------------------------------------------------------------------------%
%
% Domain reduction strategies.
%

annotation assign_lb;
annotation assign_ub;
annotation exclude_lb;
annotation exclude_ub;

annotation assign_mean;
annotation exclude_mean;

annotation assign_median;
annotation exclude_median;

annotation assign_random;
annotation exclude_random;

annotation assign_impact_min;
annotation assign_impact_max;
annotation exclude_impact_min;
annotation exclude_impact_max;

annotation assign_activity_min;
annotation assign_activity_max;
annotation exclude_activity_min;
annotation exclude_activity_max;

annotation include_min;
annotation include_max;
annotation exclude_min;
annotation exclude_max;

annotation enumerate_lb;
annotation enumerate_ub;

annotation bisect_low;
annotation bisect_high;
annotation bisect_median_low;
annotation bisect_median_high;
annotation bisect_random_low;
annotation bisect_random_high;
annotation bisect_interval_low;
annotation bisect_interval_high;
annotation bisect_impact_min;
annotation bisect_impact_max;
annotation bisect_activity_min;
annotation bisect_activity_max;

%-----------------------------------------------------------------------------%
%-----------------------------------------------------------------------------%

% Include solver-specific redefinitions for any FlatZinc built-ins.
%

%-----------------------------------------------------------------------------%
%-----------------------------------------------------------------------------%
%-----------------------------------------------------------------------------%
% Require adjacent rows and adjacent columns in the array 'x' to be
% lexicographically ordered.  Adjacent rows and adjacent columns cannot be
% equal.
%-----------------------------------------------------------------------------%


predicate strict_lex2(array[int, int] of var int: x) =
    let {
        int: lbx1 = min(index_set_1of2(x)),
        int: ubx1 = max(index_set_1of2(x)),
        int: lbx2 = min(index_set_2of2(x)),
        int: ubx2 = max(index_set_2of2(x))
    } in (
        
        forall(i in lbx1 + 1 .. ubx1) (
            lex_less([x[i - 1, j] | j in index_set_2of2(x)],
                     [x[i,     j] | j in index_set_2of2(x)]
            )
        )
        
        /\

        forall(j in lbx2 + 1 .. ubx2) (
            lex_less([x[i, j - 1] | i in index_set_1of2(x)],
                     [x[i, j    ] | i in index_set_1of2(x)]
            )
        )
    );
%-----------------------------------------------------------------------------%
% Constrains the elements of 'x' to define a subcircuit where 'x[i] = j'
% means that 'j' is the successor of 'i' and 'x[i] = i' means that 'i'
% is not in the circuit.
%-----------------------------------------------------------------------------%


predicate subcircuit(array[int] of var int: x) = 
    let { set of int: S = index_set(x),
          int: l = min(S),
          int: u = max(S),
          int: n = card(S),
          array[S] of var 1..n: order,
          array[S] of var bool: ins = array1d(S,[ x[i] != i | i in S]),
          var l..u+1: firstin = min([ u+1 + bool2int(ins[i])*(i-u-1) | i in S]),
          var S: lastin,
          var bool: empty = (firstin > u), 
    } in
    alldifferent(x) /\  
    alldifferent(order) /\
    
    % If the subcircuit is empty then each node points at itself.
    %
    (empty -> forall(i in S)(not ins[i])) /\

    % If the subcircuit is non-empty then order numbers the subcircuit.
    %
    ((not empty) ->  

       % The firstin node is numbered 1.
       order[firstin] = 1 /\

       % The lastin node is greater than firstin.
       lastin > firstin /\

       % The lastin node points at firstin.
       x[lastin] = firstin /\

       % And both are in
       ins[lastin] /\ ins[firstin] /\

       % The successor of each node except where it is firstin is
       % numbered one more than the predecessor.
       forall(i in S) (
           (ins[i] /\ x[i] != firstin) -> order[x[i]] = order[i] + 1
       ) /\

       % Each node that is not in is numbered after the lastin node.
       forall(i in S) (
           ins[i] \/ order[lastin] < order[i]
       )
    );

predicate subcircuit_reif(array[int] of var int: x, var bool: b) = 
    abort("Reified subcircuit/1 is not supported.");

%-----------------------------------------------------------------------------%
%-----------------------------------------------------------------------------%
%-----------------------------------------------------------------------------%
% Requires that the sum of cs[i1]...cs[iN] equals 's', where i1...iN are the
% elements of the 'i'th set in 'sets'.
%
% Nb: not called 'sum' as in the constraints catalog because 'sum' is a
% MiniZinc built-in function, and we cannot overload a name as both a
% function and a predicate.
%-----------------------------------------------------------------------------%

predicate sum_pred(var int: i, array[int] of set of int: sets,
                   array[int] of int: cs, var int: s) =
    s == sum(j in index_set(cs)) ( bool2int(j in sets[i]) * cs[j] );
%-----------------------------------------------------------------------------%
% A table constraint: table(x, t) represents the constraint x in t where we
% consider each row in t to be a tuple and t as a set of tuples.
%-----------------------------------------------------------------------------%

predicate table_bool(array[int] of var bool: x, array[int, int] of bool: t) =
    assert (index_set_2of2(t) == index_set(x),
        "The second dimension of the table must equal the number of variables "
            ++ "in the first argument",
        let { int: l = min(index_set(x)),
              int: u = max(index_set(x)),
              int: lt = min(index_set_1of2(t)),
              int: ut = max(index_set_1of2(t)),
              var lt..ut: i,
              array[l..u, lt..ut] of bool: t_transposed =
                  array2d(l..u, lt..ut, [ t[i,j] | j in l..u, i in lt..ut ]) }
        in
            forall(j in l..u) (
                % Having the variable index component at the left position
                % means that the nD-to-1D array translation during Mzn-to-Fzn
                % will generate at most an offset constraint, instead of a
                % scaling + offset constraint.
                %
                t_transposed[j,i] = x[j]
                %
                % t[i,j] = x[j]
            )
    );

predicate table_bool_reif(array[int] of var bool: x, array[int, int] of bool: t,
      var bool: b) = 
   abort("Reified table/2 for Booleans is not supported.");
%-----------------------------------------------------------------------------%
% A table constraint table(x, t) represents the constraint x in t where we
% consider each row in t to be a tuple and t as a set of tuples.
%-----------------------------------------------------------------------------%

predicate table_int(array[int] of var int: x, array[int, int] of int: t) =
    assert (index_set_2of2(t) == index_set(x),
        "The second dimension of the table must equal the number of variables "
            ++ "in the first argument",
        let { int: l = min(index_set(x)),
              int: u = max(index_set(x)),
              int: lt = min(index_set_1of2(t)),
              int: ut = max(index_set_1of2(t)),
              var lt..ut: i,
              array[l..u, lt..ut] of int: t_transposed =
                  array2d(l..u, lt..ut, [ t[i,j] | j in l..u, i in lt..ut ]) }
        in
            forall(j in l..u) (
                % Having the variable index component at the left position
                % means that the nD-to-1D array translation during Mzn-to-Fzn
                % will generate at most an offset constraint, instead of a
                % scaling + offset constraint.
                %
                t_transposed[j,i] = x[j]
                %
                % t[i,j] = x[j]
            )
    );

%-----------------------------------------------------------------------------%
% Reified version
%
% We only support special cases of a few variables.
%
% The approach is to add the Boolean variable to the list of variables and
% create an extended table.  The extended table covers all combinations of
% assignments to the original variables, and every entry in it is padded with a
% value that depends on whether that entry occurs in the original table.
%
% For example, the original table constraint
%
%   x y
%   ---
%   2 3
%   5 8
%   4 1
%
% reified with a Boolean b is turned into a table constraint of the form
%
%   x y  b
%   ---------
%   2 3 true
%   5 8 true
%   4 1 true
%   ... false
%   ... false  % for all other pairs (x,y)
%   ... false
%

predicate table_int_reif(array[int] of var int: x, array[int, int] of int: t,
                         var bool: b) =

    let { int: n_vars = length(x) }
    in

    assert(n_vars in 1..5,
        "'table' constraints in a reified context " ++
        "are only supported for 1..5 variables.",

    if n_vars = 1 then

        x[1] in { t[it,1] | it in index_set_1of2(t) } <-> b

    else

        let { set of int: ix  = index_set(x),
              set of int: full_size = 1..product(i in ix)( dom_size(x[i]) ),
              array[full_size, 1..n_vars + 1] of int: t_b =
                    array2d(full_size, 1..n_vars + 1,

                    if n_vars = 2 then

                        [ let { array[ix] of int: tpl = [i1,i2] } in
                            (tpl ++ [bool2int(aux_is_in_table(tpl,t))])[p]
                          | i1 in dom(x[1]),
                            i2 in dom(x[2]),
                            p in 1..n_vars + 1 ]

                    else if n_vars = 3 then

                        [ let { array[ix] of int: tpl = [i1,i2,i3] } in
                            (tpl ++ [bool2int(aux_is_in_table(tpl,t))])[p]
                          | i1 in dom(x[1]),
                            i2 in dom(x[2]),
                            i3 in dom(x[3]),
                            p in 1..n_vars + 1 ]

                    else if n_vars = 4 then

                        [ let { array[ix] of int: tpl = [i1,i2,i3,i4] }
                          in
                            (tpl ++ [bool2int(aux_is_in_table(tpl,t))])[p]
                          | i1 in dom(x[1]),
                            i2 in dom(x[2]),
                            i3 in dom(x[3]),
                            i4 in dom(x[4]),
                            p in 1..n_vars + 1 ]

                    else % if n_vars = 5 then

                        [ let { array[ix] of int: tpl = [i1,i2,i3,i4,i5] } in
                            (tpl ++ [bool2int(aux_is_in_table(tpl,t))])[p]
                          | i1 in dom(x[1]),
                            i2 in dom(x[2]),
                            i3 in dom(x[3]),
                            i4 in dom(x[4]),
                            i5 in dom(x[5]),
                            p in 1..n_vars + 1 ]

                    endif endif endif ) }
        in
        table_int(x ++ [bool2int(b)], t_b)

    endif
    );

test aux_is_in_table(array[int] of int: e, array[int, int] of int: t) =
    exists(i in index_set_1of2(t))(
        forall(j in index_set(e))( t[i,j] = e[j] )
    );

%-----------------------------------------------------------------------------%
%-----------------------------------------------------------------------------%
%-----------------------------------------------------------------------------%
% A table constraint: table(x, t) represents the constraint x in t where we
% consider each row in t to be a tuple and t as a set of tuples.
%-----------------------------------------------------------------------------%


predicate table(array[int] of var bool: x, array[int, int] of bool: t) =
    table_bool(x, t);

predicate table(array[int] of var int: x, array[int, int] of int: t) =
    table_int(x, t);

predicate value_precede_chain_int(array[int] of int: c, array[int] of var int: x) =
    forall (i in min(index_set(c)) + 1 .. max(index_set(c))) (
        value_precede(c[i - 1], c[i], x)
    );
%-----------------------------------------------------------------------------%
% Require that the value_precede constraint is true for every pair of adjacent
% integers in 'c' in the array 'x'.
%-----------------------------------------------------------------------------%


predicate value_precede_chain(array[int] of int: c, array[int] of var int: x) =
    value_precede_chain_int(c, x);
predicate value_precede_chain(array[int] of int: c, array[int] of var set of int: x) =
    value_precede_chain_set(c, x);

predicate value_precede_chain_set(array[int] of int: c, array[int] of var set of int: x) =
    forall (i in min(index_set(c)) + 1 .. max(index_set(c))) (
        value_precede(c[i - 1], c[i], x)
    );
predicate value_precede_int(int: s, int: t, array[int] of var int: x) =
    let {
        int: imin = min(index_set(x)),
        int: imax = max(index_set(x)),
        array[imin..imax+1] of var bool: b
    } in (
        forall (i in imin..imax) (let {
            var bool: xis = (x[i] == s)
        } in
            (xis -> (b[i+1] == true))
        /\  ((not xis) -> (b[i] == b[i+1]))
        /\  ((not b[i]) -> (x[i] != t))
        )
    /\  b[imin] == false
    );
%-----------------------------------------------------------------------------%
% Requires that 's' precede 't' in the array 'x'.
%
% For integer variables this constraint means that if any element of 'x'
% is equal to 't', then another element of 'x' with a  lower index is equal
% to 's'.
% For set variables this constraint means that if an element of 'x'
% contains 't' but not 's', then another element of 'x' with lower index contains
% 's' but not 't'.
%-----------------------------------------------------------------------------%


predicate value_precede(int: s, int: t, array[int] of var int: x) =
    value_precede_int(s, t, x);
predicate value_precede(int: s, int: t, array[int] of var set of int: x) =
    value_precede_set(s, t, x);
predicate value_precede_set(int: s, int: t, array[int] of var set of int: x) =
    let {
        int: imin = min(index_set(x)),
        int: imax = max(index_set(x)),
        array[imin..imax + 1] of var bool: b
    } in (
        forall (i in imin..imax) (let {
            var bool: xis = (s in x[i] /\ not (t in x[i]))
        } in
            (xis -> (b[i + 1] == true))
        /\  ((not xis) -> (b[i] == b[i + 1]))
        /\  ((not b[i]) -> (s in x[i] \/ not (t in x[i])))
        )
    /\  b[imin] == false
    );
include "globals.mzn";


%-----------------------------------------------------------------------------%
% Requires that a set of tasks given by start times 's', durations 'd', and
% resource requirements 'r', never require more than a global resource bound
% 'b' at any one time.
% Assumptions:
% - forall i, d[i] >= 0 and r[i] >= 0
%-----------------------------------------------------------------------------%
predicate cumulative_assert(array[int] of var int: s,
                            array[int] of var int: d,
                            array[int] of var int: r, var int: b) =
    assert(index_set(s) == index_set(d) /\ index_set(s) == index_set(r),
        "cumulative: the 3 array arguments must have identical index sets",
        assert(lb_array(d) >= 0 /\ lb_array(r) >= 0,
            "cumulative: durations and resource usages must be non-negative",
            let { 
               set of int: tasks = 
                  {i | i in index_set(s) where ub(r[i]) > 0 /\ ub(d[i]) > 0 }
            } in if card(tasks) = 0 then true
               else let {
                 set of int: times =
                        min([ lb(s[i]) | i in tasks ]) ..
                        max([ ub(s[i]) + ub(d[i]) | i in tasks ])
                 } in
                   forall( t in times ) (
                      b >= sum( i in tasks ) (
                         bool2int( s[i] <= t /\ t < s[i] + d[i] ) * r[i]
                      )
                   )
               endif
        )
    );

predicate lex_less_int_checking(array[int] of var int : x, array[int] of var int : y);
predicate lex_lesseq_int_checking(array[int] of var int : x, array[int] of var int : y);

predicate lex2_checking(array[int, int] of var int: x) =
    let {
        int: lbx1 = min(index_set_1of2(x)),
        int: ubx1 = max(index_set_1of2(x)),
        int: lbx2 = min(index_set_2of2(x)),
        int: ubx2 = max(index_set_2of2(x))
    } in (
        
        forall(i in lbx1 + 1 .. ubx1) (
            lex_lesseq_int_checking([x[i - 1, j] | j in index_set_2of2(x)],
                                    [x[i,     j] | j in index_set_2of2(x)]
            )
        )
        
        /\

        forall(j in lbx2 + 1 .. ubx2) (
            lex_lesseq_int_checking([x[i, j - 1] | i in index_set_1of2(x)],
                                    [x[i, j    ] | i in index_set_1of2(x)]
            )
        )
    );

predicate strict_lex2_checking(array[int, int] of var int: x) =
    let {
        int: lbx1 = min(index_set_1of2(x)),
        int: ubx1 = max(index_set_1of2(x)),
        int: lbx2 = min(index_set_2of2(x)),
        int: ubx2 = max(index_set_2of2(x))
    } in (
        
        forall(i in lbx1 + 1 .. ubx1) (
            lex_less_int_checking([x[i - 1, j] | j in index_set_2of2(x)],
                                  [x[i,     j] | j in index_set_2of2(x)]
            )
        )
        
        /\

        forall(j in lbx2 + 1 .. ubx2) (
            lex_less_int_checking([x[i, j - 1] | i in index_set_1of2(x)],
                                  [x[i, j    ] | i in index_set_1of2(x)]
            )
        )
    );

predicate maximum_int_checking(int: m, array[int] of int: x) =
    max(x) = m;

predicate minimum_int_checking(int: m, array[int] of int: x) =
    min(x) = m;

%-----------------------------------------------------------------------------%
% 2-argument version of global_cardinality.
%-----------------------------------------------------------------------------%

predicate gcc(array[int] of var int: x, array[int] of var int: counts) =
  global_cardinality(x,
                     [ i | i in index_set(counts) ],
                     array1d(counts));


predicate true_constraint = true;
predicate false_constraint = false;


predicate lineareq(array[int] of var int : x, array[int] of var int : y, var int : n) =
  n = sum(i in index_set(x))(x[i]*y[i]);

% Channel
predicate channel(var int : x, array [int] of var 0..1 : a) =
  forall (i in index_set(a)) (a[i] = 1 <-> x = i);

predicate unary(array[int] of var int: s, array[int] of var int: d) =
  % let { array[int] of int : r = [ 1 | i in index_set(s) ] }
  % in cumulative_assert(s, d, r, 1);
  assert(index_set(s) == index_set(d),
      "cumulative: the 3 array arguments must have identical index sets",
    forall (i,j in index_set(s) where i<j)
      (s[i]+d[i] <= s[j] \/ s[j]+d[j] <= s[i]));

predicate bin_packing_load_ub(array[int] of var int: load,
                              array[int] of var int: bin,
                              array[int] of int: w) =
    assert(index_set(bin) == index_set(w),
        "bin_packing_load: the bin and weight arrays must have identical index sets",
    assert(lb_array(w) >= 0,
        "bin_packing_load: the weights must be non-negative",
%        sum(load) = sum(w)
        forall( i in index_set(bin) ) (
            min(index_set(load)) <= bin[i] /\ bin[i] <= max(index_set(load))
        )
    /\  forall( b in index_set(load) ) (
            load[b] >= sum ( i in index_set(bin) ) (
                w[i] * bool2int( bin[i] = b )
            )
        )
    ));

predicate binaries_represent_int(array[int] of var int : b) =
     lb_array(b) in {0,1}
  /\ ub_array(b) in {0,1}
  /\ sum(b) = 1;

predicate binaries_represent_int_3A(array[int,int,int] of var int : b) =
     lb_array(b) in {0,1}
  /\ ub_array(b) in {0,1}
  /\ forall (j in index_set_2of3(b), k in index_set_3of3(b))
       (sum (i in index_set_1of3(b))(b[i,j,k]) = 1);

predicate binaries_represent_int_3B(array[int,int,int] of var int : b) =
     lb_array(b) in {0,1}
  /\ ub_array(b) in {0,1}
  /\ forall (i in index_set_1of3(b), k in index_set_3of3(b))
       (sum (j in index_set_2of3(b))(b[i,j,k]) = 1);

predicate binaries_represent_int_3C(array[int,int,int] of var int : b) =
     lb_array(b) in {0,1}
  /\ ub_array(b) in {0,1}
  /\ forall (i in index_set_1of3(b), j in index_set_2of3(b))
       (sum (k in index_set_3of3(b))(b[i,j,k]) = 1);

predicate channelACB(array[int,int] of var int : x, array[int,int,int] of var int : b) =
     lb_array(b) in {0,1}
  /\ ub_array(b) in {0,1}
  /\ forall (i in index_set_1of3(b), j in index_set_2of3(b), k in index_set_3of3(b))
       (x[i,k] = j <-> (b[i,j,k]=1));
predicate channelABC(array[int,int] of var int : x, array[int,int,int] of var int : b) =
     lb_array(b) in {0,1}
  /\ ub_array(b) in {0,1}
  /\ forall (i in index_set_1of3(b), j in index_set_2of3(b), k in index_set_3of3(b))
       (x[i,j] = k <-> (b[i,j,k]=1));

predicate channelCAB(array[int,int] of var int : x, array[int,int,int] of var int : b) =
     lb_array(b) in {0,1}
  /\ ub_array(b) in {0,1}
  /\ forall (i in index_set_1of3(b), j in index_set_2of3(b), k in index_set_3of3(b))
       (x[j,k] = i <-> (b[i,j,k]=1));
