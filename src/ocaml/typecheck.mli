open Core.Std
open Async.Std

module Error: sig
  type t = Pos.pos * string
  type 'a result = ('a, t) Result.t
end

module Expr: sig
  type t =
    | IntT
    | BoolT
    | UnitT
    | ArrayT of t
    | TupleT of t list (* len >= 2 *)
    | EmptyArray
    [@@deriving sexp, compare]

  val to_string: t -> string
  val of_typ: Pos.typ -> t

  (* subtype and supertype relation *)
  val (<=): t -> t -> bool
  val (>=): t -> t -> bool

  (* `eqs p xs ys num type` checks that
   *
   *     (1) len(xs) == len(ys), and
   *     (2) for all xi and yi, xi >= yi.
   *
   * If (1) fails, `Error (p, num)` is returned. If (2) fails, `Error (p,
   * type)` is returned. *)
  val eqs: Pos.pos -> t list -> t list -> string -> string -> unit Error.result
end

module Stmt: sig
  type t =
    | One  (* aka unit *)
    | Zero (* aka void *)
    [@@deriving sexp]

  (* least upper bound *)
  val lub: t -> t -> t
end

module Sigma: sig
  type t =
    | Var      of Expr.t
    | Function of Expr.t * Expr.t
    [@@deriving sexp]
end

module T: sig
  type p = unit             [@@deriving sexp]
  type u = unit             [@@deriving sexp]
  type c = Expr.t * Expr.t  [@@deriving sexp]
  type i = unit             [@@deriving sexp]
  type a = Expr.t           [@@deriving sexp]
  type v = Expr.t           [@@deriving sexp]
  type s = Stmt.t           [@@deriving sexp]
  type e = Expr.t           [@@deriving sexp]
  type t = Expr.t           [@@deriving sexp]
end
include (module type of Ast.Make(T))

module D: sig
  include (module type of T)
  val dummy_p: p
  val dummy_u: u
  val dummy_c: c
  val dummy_i: i
  val dummy_a: a
  val dummy_v: v
  val dummy_s: s
  val dummy_e: e
  val dummy_t: t
end

module Abbreviations: (module type of Ast.Abbreviate(D))

type context = Sigma.t String.Map.t
module Context: sig
  include (module type of String.Map)

  (* `var p c x` tries to find a binding for `x` of the form `Var e`. If no
   * such binding is found, an error at position `p` is returned instead. *)
  val var:  Pos.pos -> context -> string -> Expr.t Error.result

  (* `func p c x` tries to find a binding for `x` of the form `Function (a,
   * b)`. If no such binding is found, an error at position `p` is returned
   * instead. *)
  val func: Pos.pos -> context -> string -> (Expr.t * Expr.t) Error.result

  (* Abbreviation for Context.add c ~key:k ~data:d. *)
  val bind: context -> string -> Sigma.t -> context

  (* For every annotated variable `x:t` in `vs`, `bind_all c vs` binds `x` to
   * `t`. underscores and annotated underscores are ignored. *)
  val bind_all_vars: context -> var list -> context

  val bind_all_avars: context -> avar list -> context
end

val expr_typecheck: context -> Pos.expr -> expr Error.result
val typ_typecheck: context -> Pos.typ -> typ Error.result
val avar_typecheck: context -> Pos.avar -> avar Error.result
val var_typecheck: context -> Pos.var -> var Error.result
val stmt_typecheck: context -> Expr.t -> Pos.stmt -> stmt Error.result
val func_decl_typecheck: context -> Pos.callable_decl -> context Error.result
val func_typecheck: context -> Pos.callable -> context Error.result
val fst_func_pass: Pos.callable list -> Pos.interface list -> context Error.result
val snd_func_pass: context -> Pos.callable -> callable Error.result
val callable_decl_typecheck : Pos.callable_decl -> callable_decl Error.result
val interface_typecheck : Pos.interface -> interface Error.result
val prog_typecheck: Pos.full_prog -> full_prog Error.result
