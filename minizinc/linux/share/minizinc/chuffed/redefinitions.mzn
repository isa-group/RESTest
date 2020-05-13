%
%  Main authors:
%     Guido Tack <tack@gecode.org>
%
%  Copyright:
%     Guido Tack, 2007
%
%  Last modified:
%     $Date: 2009-11-09 22:08:26 +0100 (Mon, 09 Nov 2009) $ by $Author: tack $
%     $Revision: 10067 $
%
%  This file is part of Gecode, the generic constraint
%  development environment:
%     http://www.gecode.org
%
%  Permission is hereby granted, free of charge, to any person obtaining
%  a copy of this software and associated documentation files (the
%  "Software"), to deal in the Software without restriction, including
%  without limitation the rights to use, copy, modify, merge, publish,
%  distribute, sublicense, and/or sell copies of the Software, and to
%  permit persons to whom the Software is furnished to do so, subject to
%  the following conditions:
%
%  The above copyright notice and this permission notice shall be
%  included in all copies or substantial portions of the Software.
%
%  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
%  EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
%  MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
%  NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
%  LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
%  OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
%  WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
%
%

include "nosets.mzn";

% Sums over Boolean variables
predicate bool_lin_eq(array[int] of int: x, array[int] of var bool: b, var int: c) =
  sum(i in index_set(x)) (x[i]*b[i])=c;

%predicate bool_lin_eq(array[int] of int: a, array[int] of var bool: x,
%                      var int: c);
%predicate bool_lin_ne(array[int] of int: a, array[int] of var bool: x,
%                      var int: c);
%predicate bool_lin_le(array[int] of int: a, array[int] of var bool: x,
%                      var int: c);
%predicate bool_lin_lt(array[int] of int: a, array[int] of var bool: x,
%                      var int: c);
%predicate bool_lin_ge(array[int] of int: a, array[int] of var bool: x,
%                      var int: c);
%predicate bool_lin_gt(array[int] of int: a, array[int] of var bool: x,
%                      var int: c);

predicate bool_sum_eq(array[int] of var bool: x, var int: c); %=
%    bool_lin_eq([1 | i in index_set(x)],x,c);
predicate bool_sum_ne(array[int] of var bool: x, var int: c); %=
%    bool_lin_ne([1 | i in index_set(x)],x,c);
predicate bool_sum_le(array[int] of var bool: x, var int: c); %=
%    bool_lin_le([1 | i in index_set(x)],x,c);
predicate bool_sum_lt(array[int] of var bool: x, var int: c); %=
%    bool_lin_lt([1 | i in index_set(x)],x,c);
predicate bool_sum_ge(array[int] of var bool: x, var int: c); %=
%    bool_lin_ge([1 | i in index_set(x)],x,c);
predicate bool_sum_gt(array[int] of var bool: x, var int: c); %=
%    bool_lin_gt([1 | i in index_set(x)],x,c);

predicate array_bool_xor(array[int] of var bool: bs) =
    let { int: bs_lower = min(index_set(bs)),
          int: bs_upper = max(index_set(bs)),
          int: n = length(bs)
    } in
        if n == 1 then bs[bs_lower] else
        if n == 2 then bs[bs_lower] xor bs[bs_upper] else
        if n == 3 then bs[bs_lower] = (bs[bs_lower + 1] = bs[bs_upper])
        else
            let { int: cs_lower = bs_lower + 1,
                  int: cs_upper = bs_upper - 1,
                  array [cs_lower..cs_upper] of var bool: cs
            } in
                forall(i in cs_lower..cs_upper-1)(
                    cs[i+1] = bs[i+1] xor cs[i]
                )
                /\ (cs[cs_lower] = bs[bs_lower] xor bs[bs_lower + 1])
                /\ (bs[bs_upper] xor cs[cs_upper])
        endif endif endif;

predicate int_mod(var int: x, var int: y, var int: z) =
  x=(x div y)*y+z;

predicate int_eq_imp(var int: a, var int: b, var bool: r);
predicate int_ne_imp(var int: a, var int: b, var bool: r);
predicate int_ge_imp(var int: a, var int: b, var bool: r);
predicate int_gt_imp(var int: a, var int: b, var bool: r);
predicate int_le_imp(var int: a, var int: b, var bool: r);
predicate int_lt_imp(var int: a, var int: b, var bool: r);

