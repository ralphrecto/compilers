open Core.Std
open Cfg
open Dataflow
open Ir
open Tiling
open Fresh

(* ************************************************************************** *)
(* Helpers                                                                    *)
(* ************************************************************************** *)
module ExprSet = struct
  include Set.Make (struct
    type t = expr [@@deriving sexp, compare]
  end)

  let to_string irs =
    to_list irs
    |> List.map ~f:Ir.string_of_expr
    |> List.map ~f:(fun s -> "  " ^ s ^ ",\n")
    |> Util.join
    |> fun s -> "{\n" ^ s ^ "\n}"
end
open ExprSet

module ExprSetIntersectLattice = struct
  type data = ExprSet.t
  let ( ** ) = ExprSet.inter
  let ( === ) = ExprSet.equal
  let to_string = ExprSet.to_string
end

module ExprSetUnionLattice = struct
  type data = ExprSet.t
  let ( ** ) = ExprSet.union
  let ( === ) = ExprSet.equal
  let to_string = ExprSet.to_string
end

module type ExprSetIntersectCFG = sig
  include Dataflow.CFGWithLatticeT with
    module Lattice = ExprSetIntersectLattice and
    module CFG = Cfg.IrCfg
end

module type ExprSetUnionCFG = sig
  include Dataflow.CFGWithLatticeT with
    module Lattice = ExprSetUnionLattice and
    module CFG = Cfg.IrCfg
end

let rec get_subexpr (e: expr) : ExprSet.t =
  match e with
  | BinOp (e1, (DIV|MOD), e2) -> union (get_subexpr e1) (get_subexpr e2)
  | BinOp (e1, _, e2) -> add (union (get_subexpr e1) (get_subexpr e2)) e
  | Call (_, elst) ->
    let f acc e = union acc (get_subexpr e) in
    List.fold_left ~f ~init: empty elst
  | Mem (e1, _) -> add (get_subexpr e1) e
  | Temp _ | Const _ | Name _ -> empty
  | ESeq _ -> failwith "shouldn't exist!"

and get_subexpr_stmt (s: stmt) : ExprSet.t =
  match s with
  | CJumpOne (e1, _) -> get_subexpr e1
  | Exp e1 -> get_subexpr e1
  | Move (e1, e2) -> union (get_subexpr e1) (get_subexpr e2)
  | Seq slst ->
      let f acc s = union acc (get_subexpr_stmt s) in
      List.fold_left ~f ~init: empty slst
  | Jump _ | Label _ | Return -> empty
  | CJump _ -> failwith "shouldn't exist!"

let rec get_mem_temp (e: expr) : ExprSet.t =
  match e with
  | BinOp (e1, _, e2) -> union (get_mem_temp e1) (get_mem_temp e2)
  | Call (_, elst) ->
    let f acc e = union acc (get_mem_temp e) in
    List.fold_left ~f ~init: empty elst
  | Mem (e1, _) -> add (get_mem_temp e1) e
  | Temp _ -> add empty e
  | Const _ | Name _ -> empty
  | ESeq _ -> failwith "shouldn't exist!"

let rec kill_func_args (elst: expr list) : ExprSet.t =
  let f acc e =
    match e with
    | Mem _ -> add acc e
    | Call (_, elst') -> kill_func_args elst'
    | _ -> empty
  in
  List.fold_left ~f ~init: empty elst

and kill_expr (e: expr) : ExprSet.t =
  match e with
  | Call (_, elst) -> kill_func_args elst
  | Temp _
  | Mem _ -> add empty e
  | BinOp _
  | Const _
  | Name _ -> empty
  | ESeq _ -> failwith "shouldn't exist!"

and kill_stmt (s: stmt) : ExprSet.t =
  match s with
  | Move ((Temp _ | Mem _) as e1, Call (_, elst)) ->
    let set = add empty e1 in
    union set (kill_func_args elst)
  | Move ((Temp _ | Mem _) as e1, _) -> add empty e1
  | Seq slst ->
      let f acc s =
        let set = kill_stmt s in
        union acc set
      in
      List.fold_left ~f ~init: empty slst
  | Exp e -> kill_expr e
  | CJumpOne _
  | Jump _
  | Label _
  | Return -> empty
  | Move _
  | CJump _ -> failwith "shouldn't exist!"

(* ************************************************************************** *)
(* Preprocessing Step                                                         *)
(* ************************************************************************** *)
let dummy_ir = Label "__dummy"
let preprocess g =
  let open Cfg in
  let open IrCfg in
  let open IrData in
  let open IrDataStartExit in

  (* compare two IR CFG nodes *)
  let v_compare a b =
    Int.compare (to_int a) (to_int b)
  in

  (* compare two IR CFG edges *)
  let e_compare a b =
    v_compare (V.label (E.src a)) (V.label (E.src b))
  in

  (* dummy node helpers *)
  let fresh_num = nb_vertex g in
  let dummy i = V.create (Node {num=i; ir=dummy_ir}) in

  (* gather all the vertices with more than on predecessor *)
  let vs = vertex_set g in
  let multiple_preds =
    VertexSet.filter ~f:(fun v -> in_degree g v > 1) vs
    |> VertexSet.to_list
    |> List.sort ~cmp:v_compare
  in

  (* add dummy nodes on all the incoming edges *)
  List.fold_left multiple_preds ~init:fresh_num ~f:(fun a v ->
    let preds =
      preds_e g v
      |> EdgeSet.to_list
      |> List.sort ~cmp:e_compare
    in
    List.fold_left preds ~init:a ~f:(fun a e ->
      let d = dummy a in
      remove_edge_e g e;
      add_vertex g d;
      add_edge g (E.src e) d;
      add_edge g d (E.dst e);
      a + 1
    )
  ) |> ignore

(* ************************************************************************** *)
(* Anticipated Expressions                                                    *)
(* ************************************************************************** *)
module BusyExprCFG = struct
  module Lattice = ExprSetIntersectLattice
  module CFG = IrCfg
  module IDSE = IrDataStartExit
  open Lattice
  open CFG

  type graph = CFG.t
  type node = CFG.V.t
  type edge = CFG.E.t
  type data = Lattice.data

  let direction = `Backward

  (* first is univ, second is a node to uses mapping and the third is a node to kill mapping *)
  type extra_info = {
    g     : graph;        (* the graph *)
    univ  : data;         (* all of the used expressions in the graph *)
    uses  : node -> data; (* uses[B] *)
    kills : node -> data; (* kill[B] *)
  }

  let init ({univ; _} : extra_info) (_: graph) =
    fun n ->
      match n with
      | IDSE.Exit -> empty
      | IDSE.Start
      | IDSE.Node _ -> univ

  let transfer ({uses; kills; _}: extra_info) (e: edge) (d: data) =
    let node = E.dst e in
    match node with
    | IDSE.Start -> failwith "TODO"
    | IDSE.Exit -> failwith "TODO"
    | IDSE.Node _ ->
      let use = uses node in
      let kill = kills node in
      let f acc expr =
        let mem_temps = get_mem_temp expr in
        if ExprSet.is_empty (inter mem_temps kill) then
          add acc expr
        else
          acc
      in
      let diff_expr_kill = fold ~f ~init: empty d in
      union use diff_expr_kill
end

module BusyExpr = Dataflow.GenericAnalysis(BusyExprCFG)

(* ************************************************************************** *)
(* Available Expressions                                                      *)
(* ************************************************************************** *)
module AvailExprCFG = struct
  module Lattice = ExprSetIntersectLattice
  module CFG = IrCfg
  module IDSE = IrDataStartExit
  open Lattice
  open CFG

  type graph = CFG.t
  type node = CFG.V.t
  type edge = CFG.E.t
  type data = Lattice.data

  let direction = `Forward

  type extra_info = {
    g     : graph;        (* the graph *)
    univ  : data;         (* all of the used expressions in the graph *)
    busy  : node -> data; (* anticipated[B].in *)
    kills : node -> data; (* kill[B] *)
  }

  let (+) = ExprSet.union

  let init ({univ; _}: extra_info) (_: graph) =
    fun n ->
      match n with
      | IDSE.Start -> empty
      | IDSE.Exit
      | IDSE.Node _ -> univ

  let transfer ({busy; kills; _}: extra_info) (e:edge) (d: data) =
    let source = E.src e in
    let anticipated = busy source in
    let kill = kills source in
    let union' = anticipated + d in
    let f acc expr =
      let mem_temps = get_mem_temp expr in
      if ExprSet.is_empty (inter mem_temps kill) then
        add acc expr
      else
        acc
    in
    fold ~f ~init: empty union'
end

module AvailExpr = Dataflow.GenericAnalysis(AvailExprCFG)

(* ************************************************************************** *)
(* Postponable Expressions                                                    *)
(* ************************************************************************** *)
module PostponeExprCFG = struct
  module Lattice = ExprSetIntersectLattice
  module CFG = IrCfg
  module IDSE = IrDataStartExit
  open Lattice
  open CFG

  type graph = CFG.t
  type node = CFG.V.t
  type edge = CFG.E.t
  type data = Lattice.data

  let direction = `Forward

  type extra_info = {
    g        : graph;        (* the graph *)
    univ     : data;         (* all of the used expressions in the graph *)
    uses     : node -> data; (* e_use_{B} *)
    earliest : node -> data; (* earlieset[B] *)
  }

  let (+) = ExprSet.union
  let (-) = ExprSet.diff

  let init ({univ; _}: extra_info) (_: graph) =
    fun n ->
      match n with
      | IDSE.Start -> empty
      | IDSE.Exit
      | IDSE.Node _ -> univ

  let transfer ({earliest; uses; _}: extra_info) (e: edge) (d: data) =
    let source = E.src e in
    ((earliest source) + d) - (uses source)
end

module PostponeExpr = Dataflow.GenericAnalysis(PostponeExprCFG)

(* ************************************************************************** *)
(* Used Expressions                                                           *)
(* ************************************************************************** *)
module UsedExprCFG = struct
  module Lattice = ExprSetIntersectLattice
  module CFG = IrCfg

  type graph = CFG.t
  type node = CFG.V.t
  type edge = CFG.E.t
  type data = Lattice.data

  let direction = `Backward

  type extra_info = {
    g        : graph;        (* the graph *)
    uses     : node -> data; (* e_use_{B} *)
    post     : node -> data; (* postponable[B].in *)
    earliest : node -> data; (* earlieset[B] *)
  }

  let init _ _ _ = ExprSet.empty

  let (+) = ExprSet.union
  let (-) = ExprSet.diff

  let latest ({g; uses; post; earliest; _}: extra_info) (n: node) =
    ExprSet.(filter (earliest n + post n) ~f:(fun e ->
      mem (uses n) e || CFG.VertexSet.exists (CFG.succs g n) ~f:(fun n' ->
        not (mem (earliest n') e || mem (post n') e)
      )
    ))

  let transfer ({uses; _} as info) e x =
    let n = CFG.E.dst e in
    (uses n + x) - latest info n
end

module UsedExpr = Dataflow.GenericAnalysis(UsedExprCFG)

(* ************************************************************************** *)
(* Whole Enchilada                                                            *)
(* ************************************************************************** *)
let pre _irs =
  failwith "a"
