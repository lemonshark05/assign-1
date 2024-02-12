# Assignment 1

**See Gradescope for due date.**

## Overall structure you need to implement


In both this and the next assignment, you need to implement a data flow
analyses.  All of these analyses have a common structure:

- You need to implement the join `⊔` operation. I recommend implementing a
  function that performs `a = a ⊔ b` and returns whether there is a change to
  `a`.
  - A generic but inefficient way you can implement this is:
    - calculate `a ⊔ b`, save to a temporary (tmp).
    - check `a == tmp`, this is the return value (whether `a` has changed).
    - assign `a := tmp`.
- You need to implement abstract semantics (abstract transfer functions, F# from
  lectures):
  - `analyze_inst` implements the action of an instruction (computing post state
    from the pre state, or vice versa).
  - `analyze_term` implements the action of an instruction (computing post state
    from the pre state, or vice versa).
  - `analyze_bb` uses `analyze_inst` and `analyze_term`.  **Once you have
    `analyze_inst` and `analyze_term`, there are only 2 possible implementations
    for `analyze_bb`: one for all forward analyses, and the other for all
    backward analyses.**  All analyses for this assignment are forward analyses.
- You need to implement the worklist algorithm covered in lecture 3.  You can
  implement this algorithm in a generic way (e.g., using generics in Rust/Java)
  and re-use it between multiple analyses.
  
## Lack of pointer information

Because we don't know anything about pointers for these analyses, the left-hand
 side/destination should always be ⊤ for following instructions:
- `$alloc`
- `$call` family of instructions
- `$gep`, `$gfp`
- `$load`

In the initial state for entry, all **integer or pointer-typed** locals are
mapped to ⊥, and all **integer or pointer-typed** parameters and globals are
mapped to ⊤.  The analyses in this assignment do not keep track of non-integer
values (function pointers, structs, etc.).

## Division by zero

- **Division by constant zero results in ⊥.**

## General tips

Getting started with the DFA framework is a bit daunting at first.  You need to
implement a whole domain and `forward_analysis` before you can get any results.
I recommend starting with defining the domain and join, then working through the
`analyze_XXX` methods.  Then, you can implement and test the worklist algorithm.

## PART 1: Integer Constant Analysis

Implement the intraprocedural integer constant analysis using the MFP worklist
algorithm, as described in lecture.

As a reminder: The abstract domain is `⊥ ⊑ {..., -2, -1, 0, 1, 2, ...} ⊑ ⊤`,
where `⊥` means "no integer value", `⊤` means "unknown", i.e. "any integer
value", and for n ∈ 𝐙, n means "exactly the value n". The join of `⊥` and any
abstract value `X` is `X`; the join of any abstract value with itself is itself;
otherwise the join of any two abstract values is `⊤`.

You must devise the abstract semantics for the arithmetic operators (`add`,
`sub`, `mul`, `div`) and comparison operators (`eq`, `neq`, `lt`, `lte`, `gt`,
`gte`) using this abstract domain (i.e., fill in the entries of the table below
for each operator, where `c1` and `c2` represent integer constants).

```
<operator> | ⊥ | c2 | ⊤
-----------+---+----+---
         ⊥ |   |    |
        c1 |   |    |
         ⊤ |   |    |
```

## PART 2: Integer Interval Analysis (aka Range Analysis)

Implement the intraprocedural integer interval analysis using the MFP worklist
algorithm and a widening operator (applied only at loop headers), as described
in lecture.

As a reminder: The abstract domain elements are `⊥` (the empty interval) and
intervals `[a, b]` where `a` ∈ 𝐙 ∪ {-∞} and `b` ∈ 𝐙 ∪ {∞} and `a` <= `b`. In
this domain, `⊤` = `[-∞, ∞]`. The join of `⊥` with any abstract value `X` is
`X`; the join of two intervals `[low1, high1]` and `[low2, high2]` is `[min(low1, low2), max(high1, high2)]`.

The widening of `⊥` with any abstract value `X` is `X`; otherwise `I1 ∇ I2` = `I3` s.t.

- `I3.low` = `I1.low` if `I1.low` <= `I2.low`, otherwise `-∞`
- `I3.high` = `I1.high` if `I1.high` >= `I2.high`, otherwise `∞`

You must devise the abstract semantics for the arithmetic operators (`add`,
`sub`, `mul`, `div`) and comparison operators (`eq`, `neq`, `lt`, `lte`, `gt`,
`gte`) using this abstract domain. The comparison operators are straightforward
(remember that comparison always returns either `0` [i.e., false] or `1` [i.e.,
true]). For the arithmetic operators the simplest method is to compute all
possible values using the input interval bounds and then select the minimum and
maximum as the new interval bounds. For example, given `[-2, 3] * [-1, 1]`:
`-2 * -1 = 2`, `-2 * 1 = -2`, `3 * -1 = -3`, `3 * 1 = 3`, therefore the new
interval is `[-3, 3]`. Division is a little trickier because division by zero is
undefined; we handle it like so for `I1 ÷ I2`:

- If `I2` = `[0, 0]`: the answer is `⊥`.
- If `I2.low` is negative and `I2.high` is positive (i.e., the interval contains
  0): treat this as `I1 ÷ [-1, 1]` using the min/max method given above.
- If `I2.low` is 0: treat this as `I1 ÷ [1, I2.high]` using the min/max method
  given above.
- If `I2.high` is 0: treat this as `I1 ÷ [I2.low, -1]` using the min/max method
  given above.
- Otherwise just use the min/max method directly.

## Analysis output

The result of each analysis should be, for the analyzed function, a map from
each basic block to the abstract values _at the end_ of that basic block for all
variables that are **not `⊥`**. Your solution should print the analysis results
to standard output in the following form:

```
<basic block label>:
<variable name 1> -> <abstract value>
<variable name 2> -> <abstract value>
...and so on

<basic block label>:
<variable name 1> -> <abstract value>
<variable name 2> -> <abstract value>
...and so on

...and so on
```

Where the blocks are printed in alphabetical order and, for each block, the
variables are printed in alphabetical order. Whitespace doesn't matter (e.g.,
exact number of spaces, etc).

## Reference solutions

I have placed executables of my own solutions to these analyses on vlabs in
`~memre/260/{int_constants, int_intervals}`. You can use these reference
solutions to test your analyses before submitting. If you have any questions
about the output format, you can answer them using these reference solutions as
well; these are the solutions that Gradescope will use to test your submissions.
My solutions only take two arguments (as opposed to the three that your
solutions should take, described below): the file containing the LIR program and
the name of the function to analyze.

## Submitting to Gradescope

Your submission must meet the following requirements:

- There must be a `build-analyses.sh` bash script that does whatever is needed
  to build or setup both analyses (e.g., compile the code if necessary). If
  nothing is necessary the script must still exist, it can just do nothing.

- There must be `run-constants-analysis.sh` and `run-interval-analysis.sh` bash
  scripts that each take three command-line arguments: the first is a file
  containing the LIR program to analyze, the second is a file containing the
  same program in JSON format, and the third is the name of the function to
  analyze. You can use whichever program format you wish and ignore the
  other. Each script must print the result of the respective analysis to
  standard out.

- If your solution contains sub-directories then the entire submission must be
  given as a zip file (this is required by Gradescope for submissions that
  contain a directory structure). The scripts should all be at the root
  directory of the solution.
  
- **If you have many files, I recommend submitting via GitHub.**  Using version
  control is always a good idea anyway.

- The submitted files are not allowed to contain any binaries, and your
  solutions are not allowed to use the network.

If you want to submit one analysis before you've implemented the other that's
fine, but you still need all the scripts I mentioned (otherwise the autograder
will just bail). The script for the missing analysis can just do nothing.

## Running scripts

See assignment 0 readme.

## Test cases

You are given a few small example programs.  For each program `foo`, you have
the following files:

- `foo.cb` is the CFlat program.
- `foo.lir` is the LIR program.
- `foo.lir.json` is the JSON representation of the LIR program.
- `foo.constants.soln` is the expected result for the constants analysis for the
  `main` function.
- `foo.intervals.soln` is the expected result for the intervals analysis for the
  `main` function.

The autograder will use a different test suite based on randomly-generated tests
(it uses 721 tests).  When you submit your assignment, you'll get the first
failing test for each category.  I encourage you to write your own tests, and
build a test suite collectively.  Sharing tests is **OK** in this course.

## Grading

Here's how the grading will be broken down so that you can focus your work
accordingly. There are two parts to the assignment (constants analysis and
interval analysis), each worth 1.5 points.  For each part, the point breakdown
will be:

- Programs with no pointers or function calls: 0.75

- Programs with no pointers but with function calls: 0.15

- Programs with pointers but no function calls: 0.3

- Programs with pointers and function calls: 0.3

Each of these categories will have a test suite of LIR programs that will be
used to test your submission on that category for the given analysis. You must
get all tests in a given test suite correct in order to receive points for the
corresponding category (there is no partial credit _within a category_). You are
encouraged to focus on one category at a time and get it fully correct before
moving on to the next. Remember that you can also create your own test programs
and use my solution on vlabs to see what my solution for that program would be.

## Timely submission grade

If you finish part 1 completely and submit it a week from now, you will get 0.5
points extra credit (to remind you, that's half a minor grade bump in this
class).
