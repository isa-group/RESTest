%-----------------------------------------------------------------------------%
% Requires that a set of tasks given by start times 's', durations 'd', and
% resource requirements 'r', never require more than a global resource bound
% 'b' at any one time.
% Assumptions:
% - forall i, d[i] >= 0 and r[i] >= 0
%-----------------------------------------------------------------------------%

predicate fzn_cumulative(array[int] of var int: s,
                     array[int] of var int: d,
                     array[int] of var int: r, var int: b) =
    assert(
        index_set(s) == index_set(d) /\ index_set(s) == index_set(r),
        "cumulative: the 3 array arguments must have identical index sets",
        forall(i in index_set(d))(d[i] >= 0 /\ r[i] >= 0)
	/\  if is_fixed(d) /\ is_fixed(r) /\ is_fixed(b) then
		    chuffed_cumulative(s, fix(d), fix(r), fix(b))
	    else
		    chuffed_cumulative_vars(s, d, r, b)
	    endif
    );


predicate cumulative_cal(array[int] of var int: s,
                     array[int] of var int: d,
                     array[int] of var int: r, var int: b,
                     array[int,int] of int: cal,
                     array[int] of int: tc, int: rho, int: resCal) =
    let {
        int: index1 = card(index_set_1of2(cal)),
        int: index2 = card(index_set_2of2(cal)),
        set of int: times = 0..index2-1
    } in
    assert(
       index_set(s) == index_set(d) /\ index_set(s) == index_set(r),
       "cumulative: the 3 array arguments must have identical index sets",
            chuffed_cumulative_cal(s, d, r, b, index1, index2, cal, tc, rho, resCal)
    
 %   forall( t in times ) (
  %  b >= sum( i in index_set(s) ) (
 %   bool2int( s[i] <= t /\ t < s[i] + d[i] ) * (cal[tc[i],t+1] + (1 - cal[tc[i],t+1]) * rho)* r[i] 
 %   )
);

	% Global cumulative propagator
	%
predicate chuffed_cumulative(array[int] of var int: s, array[int] of int: d,
	array[int] of int: r, int: b);

predicate chuffed_cumulative_vars(array[int] of var int: s, array[int] of var int: d,
	array[int] of var int: r, var int: b);

predicate chuffed_cumulative_cal(array[int] of var int: s, array[int] of var int: d,
	array[int] of var int: r, var int: b, int: index1, int: index2, array[int,int] of int: cal, array[int] of int: tc, int: rho, int: resCal);

%------------------------------------------------------------------------------
% Options for the cumulative constraint
%   Annotate any cumulative predicate to override Chuffed's defaults for
%   the cumulative propagator.
% 
%   - tt_filt: TimeTable filtering
%   - ttef_check: TimeTable-EdgeFinding consistency check
%   - ttef_filt: TimeTable-EdgeFinding filtering

annotation tt_filt(bool: on_or_off);
annotation ttef_check(bool: on_or_off);
annotation ttef_filt(bool: on_or_off);
annotation name(string: s);
