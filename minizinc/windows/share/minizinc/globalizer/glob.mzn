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
