%------------------------------------------------------------------------------%
% Requires that a set of tasks given by start times s and durations d
% do not overlap in time. Tasks with duration 0 can be scheduled at any time,
% even in the middle of other tasks.
%
%  Assumptions:
%  - forall i, d[i] >= 0
%------------------------------------------------------------------------------%

include "fzn_cumulative.mzn";

predicate fzn_disjunctive(array[int] of var int: s,
                          array[int] of var int: d) =
        forall(i in index_set(d))(d[i] >= 0)
    /\  fzn_cumulative(s, d, [1 | i in index_set(s)], 1)
    ;
