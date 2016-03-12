module Long = Int64
open Core.Std
open Async.Std
open Ir
open Ast
open Typecheck

(* label * adjacent nodes * mark *)
type node = Node of string * string list
type graph = node list

type block = Block of string * Ir.stmt list

(* Convert an id string to a temp string. The temp string
 * should not be a possible identifier. Identifiers begin
 * with alphabetic characters. *)
let id_to_temp (idstr: string) : string = "%TEMP%" ^ idstr

let num_temp = ref 0
let fresh_temp () =
	let str = "temp" ^ (string_of_int (!num_temp)) in
	incr num_temp;
	str

(* use this funciton when creating new labels *)
let num_label = ref 0
let fresh_label () =
	let str = "label" ^ (string_of_int (!num_label)) in
	incr num_label;
	str

let rec gen_expr e = failwith "do me"

and gen_control ((t, e): Typecheck.expr) t_label f_label =
  match e with
  | Bool true -> Jump (Name t_label)
  | Bool false -> Jump (Name f_label)
  | BinOp (e1, AMP, e2) ->
      let inter_label = fresh_label () in
      Seq ([
        CJump (gen_expr e1, inter_label, f_label);
        Label inter_label;
        CJump (gen_expr e2, t_label, f_label)
      ])
  | BinOp (e1, BAR, e2) ->
      let inter_label = fresh_label () in
      Seq ([
        CJump (gen_expr e1, t_label, inter_label);
        Label inter_label;
        CJump (gen_expr e2, t_label, f_label)
      ])
  | UnOp (BANG, e1) -> gen_control e1 f_label t_label
  | _ -> CJump (gen_expr (t, e), t_label, f_label)

and gen_stmt ((_, s): Typecheck.stmt) =
  match s with
  (* TODO: is this sane? Rationale is that Decls with
   * no initializations are only useful up to typechecking
   * to verify scoping. Otherwise what code should they
   * generate? *)
  | Decl _ -> Exp (Temp (fresh_temp ()))
  | DeclAsgn (varlist, exp) ->  failwith "do me"
  | Asgn (lhs, rhs) -> begin
    match gen_expr lhs with
    | (Temp _ | Mem _ ) as lhs' -> Move (lhs', gen_expr rhs)
    | _ -> failwith "impossible"
  end
  | Block stmts -> Seq (List.map ~f:gen_stmt stmts)
  | Return exprlist -> failwith "do me"
  | If (pred, t) ->
      let t_label = fresh_label () in
      let f_label = fresh_label () in
      Seq ([
        gen_control pred t_label f_label;
        Label t_label;
        gen_stmt t;
        Label f_label;
      ])
  | IfElse (pred, t, f) ->
      let t_label = fresh_label () in
      let f_label = fresh_label () in
      Seq ([
        gen_control pred t_label f_label;
        Label t_label;
        gen_stmt t;
        Label f_label;
        gen_stmt f;
      ])
  | While (pred, s) ->
      let while_label = fresh_label () in
      let t_label = fresh_label () in
      let f_label = fresh_label () in
      Seq ([
        Label while_label;
        gen_control pred t_label f_label;
        Label t_label;
        gen_stmt s;
        Jump (Name while_label);
        Label f_label;
      ])
  | ProcCall ((_, id), args) ->
      Exp (Call (Name id, List.map ~f:gen_expr args, -1))

let rec lower_expr e =
	match e with
	| BinOp (e1, binop, e2) ->
		let (s1, e1') = lower_expr e1 in
		let (s2, e2') = lower_expr e2 in
		let temp = Temp (fresh_temp ()) in
		let temp_move = Move (temp, e1') in
		(s1 @ [temp_move] @ s2, BinOp(temp, binop, e2'))
	| Call (e', es, i) ->
		let call_fold (acc, temps) elm =
			let (s1, e1) = lower_expr elm in
			let temp = fresh_temp () in
			let temp_move = Move (Temp temp, e1) in
			(temp_move::(List.rev_append s1 acc), (Temp temp)::temps)
		in
		let (name_s, name_e) = lower_expr e' in
		let temp_name = fresh_temp () in
		let temp_move_name = Move (Temp temp_name, name_e) in
		let (arg_stmts, arg_temps) = List.fold_left ~f: call_fold ~init: ([], []) es in
		let fn_stmts = name_s @ (temp_move_name :: (List.rev arg_stmts)) in
		let fn_args = List.rev arg_temps in
		let temp_fn = fresh_temp () in
		let temp_move_fn = Move (Temp temp_fn, Call(Temp temp_name, fn_args, i)) in
		(fn_stmts @ [temp_move_fn], Temp temp_fn)
	| ESeq (s, e') ->
		let s1 = lower_stmt s in
		let (s2, e2) = lower_expr e' in
		(s1 @ s2, e2)
	| Mem (e', t) ->
		let (s', e') = lower_expr e' in
		(s', Mem (e', t))
	| Name _
	| Temp _
	| Const _ -> ([], e)

and lower_stmt s =
	match s with
	| CJump (e, l1, l2) ->
		let (s', e') = lower_expr e in
		s' @ [CJump (e', l1, l2)]
	| Jump e ->
		let (s', e') = lower_expr e in
		s' @ [Jump e']
	| Exp e -> fst (lower_expr e)
	| Move (dest, e') ->
		let (dest_s, dest') = lower_expr dest in
		let (s'', e'') = lower_expr e' in
		let temp = fresh_temp () in
		let temp_move = Move (Temp temp, e'') in
		s'' @ [temp_move] @ dest_s @ [Move(dest', Temp temp)]
	| Seq ss -> List.concat_map ~f:lower_stmt ss
	| Label _
	| Return ->	[s]
	| CJumpOne _ -> failwith "this node shouldn't exist"

let block_reorder (stmts: Ir.stmt list) =
	(* order of stmts in blocks are reversed
	   to make looking at conditionals easier *)
	let gen_block (blocks, acc, label) elm =
		match elm, label, acc with
		| Label s, Some l, _ ->
			(Block (l, acc)::blocks, [], Some s)
		| Label s, None, [] ->
			(blocks, [], Some s)
		| Label s, None, _ ->
			let fresh_label = fresh_label () in
			(Block (fresh_label, acc)::blocks, [], Some s)
		| CJump _, Some l, _ ->
			(Block (l, elm::acc)::blocks, [], None)
		| CJump _, None, _->
			let fresh_label = fresh_label () in
			(Block (fresh_label, elm::acc)::blocks, [], None)
		| Jump _, Some l, _ ->
			(Block (l, elm::acc)::blocks, [], None)
		| Jump _, None, _ ->
			let fresh_label = fresh_label () in
			(Block (fresh_label, elm::acc)::blocks, [], None)
		| _ -> (blocks, elm::acc, label)
	in
	let (b, a, l) = List.fold_left ~f: gen_block ~init: ([], [], None) stmts in
	let blocks =
		match l with
		| None ->
			let fresh_label = fresh_label () in
			(Block (fresh_label, List.rev a)) :: b |> List.rev
		| Some l' -> (Block (l', List.rev a)) :: b |> List.rev
	in
	let check_dup (Block (l1, _)) (Block (l2, _)) =
		compare l1 l2
	in
	(* sanity check to make sure there aren't duplicate labels *)
	assert (not (List.contains_dup ~compare: check_dup blocks));
	let rec create_graph blocks graph =
		match blocks with
		| Block (l1,s1)::Block (l2,s2)::tl ->
				begin
					match s1 with
					| CJump (_, tru, fls)::_ -> create_graph (Block (l2, s2)::tl) (Node (l1, [tru; fls])::graph)
					| Jump (Name l')::_ -> create_graph (Block (l2, s2)::tl) (Node (l1, [l'])::graph)
					| Jump _ ::_ -> failwith "error -- invalid jump"
					| _ -> create_graph (Block (l2, s2)::tl) (Node (l1, [l2])::graph)
				end
		| Block(l,s)::tl ->
				begin
					match s with
					| CJump (_, tru, fls)::_ -> create_graph tl (Node (l, [tru;fls])::graph)
					| Jump (Name l')::_ -> create_graph tl (Node (l, [l'])::graph)
					| Jump _ ::_ -> failwith "error -- invalid jump"
					| _ -> create_graph tl (Node (l, [])::graph)
				end
		| [] ->	List.rev graph
	in
	let graph = create_graph blocks [] in
	let rec find_trace graph (Node (l, adj)) acc =
		match adj with
		| h1::h2::_ ->
			begin
				try
					if List.exists ~f: (fun e -> e = h2) acc then
						if List.exists ~f: (fun e -> e = h1) acc then
							List.rev (l::acc)
						else
							let next' = List.find_exn ~f:(fun (Node (l', _)) -> l' = h1) graph in
							find_trace graph next' (l::acc)
					else
						let next = List.find_exn ~f:(fun (Node (l', _)) -> l' = h2) graph in
						find_trace graph next (l::acc)
				with Not_found -> List.rev (l::acc)
			end
		| hd::_ ->
			begin
				try
					if List.exists ~f: (fun e -> e = hd) acc then
						List.rev (l::acc)
					else
						let next = List.find_exn ~f: (fun (Node (l', _)) -> l' = hd) graph in
						find_trace graph next (l::acc)
				with Not_found -> List.rev (l::acc)
			end
		| [] ->	List.rev (l::acc)
	in
	let rec find_seq graph acc =
		match graph with
		| [] -> List.concat acc
		| hd::_ ->
			let trace = find_trace graph hd [] in
			let remaining_graph = List.filter	graph
																 ~f: (fun (Node (l,_)) -> not (List.exists ~f: (fun e -> e = l) trace))
			in
			find_seq remaining_graph (trace::acc)
	in
	let seq = find_seq graph [] in
	let not_expr e =
		BinOp (BinOp (e, ADD, Const 1L), MOD, Const 2L)
	in
	let rec reorder seq acc =
		match seq with
		| h1::h2::tl ->
			begin
				try
					let (Block (l, stmts)) as b = List.find_exn ~f: (fun (Block (l, _)) -> l = h1) blocks	in
					match stmts with
					| CJump (e, l1, l2)::stmts_tl ->
						if l2 = h2 then
							let new_cjump = CJumpOne (e, l1) in
							reorder (h2::tl) (Block(l, new_cjump::stmts_tl)::acc)
						else if l1 = h2 then
							let new_cjump = CJumpOne (not_expr e, l2) in
							reorder (h2::tl) (Block (l, new_cjump::stmts_tl)::acc)
						else
							let new_cjump = CJumpOne (e, l1) in
							let new_jump = Jump (Name l2) in
							reorder (h2::tl) (Block (l, new_jump::new_cjump::stmts_tl)::acc)
					| Jump (Name l')::stmts_tl ->
						if l' = h2 then
							reorder (h2::tl) (Block (l, stmts_tl)::acc)
						else
							reorder (h2::tl) (b::acc)
					| Jump _ ::_ -> failwith "error -- invalid jump"
					| _ -> reorder (h2::tl) (b::acc)
				with Not_found -> failwith "error -- label does not exist"
			end
		| h1::tl ->
			begin
				try
					let (Block (l, stmts)) as b = List.find_exn ~f: (fun (Block (l, _)) -> l = h1) blocks in
					match stmts with
					| CJump (e, l1, l2)::stmts_tl ->
						let new_cjump = CJumpOne (e, l1) in
						let new_jump = Jump (Name l2) in
						reorder tl ((Block (l, new_jump::new_cjump::stmts_tl))::acc)
					| _ -> reorder tl (b::acc)
				with Not_found -> failwith "error -- label does not exist"
			end
		| [] -> List.rev acc
	in
	let reordered_blocks = reorder seq [] in
	let	final = List.map ~f: (fun (Block (l, s)) -> Block (l, List.rev s)) reordered_blocks in
	final

let rec constant_folding e =
	let open Long in
	let open Big_int in
	match e with
	| BinOp (Const 0L, (ADD|SUB), Const i)
	| BinOp (Const i, (ADD|SUB), Const 0L)
	| BinOp (Const i, (MUL|DIV), Const 1L)
	| BinOp (Const 1L, MUL, Const i) -> Const i
	| BinOp (Const i1, ADD, Const i2) -> Const (add i1 i2)
	| BinOp (Const i1, SUB, Const i2) -> Const (sub i1 i2)
	| BinOp (Const i1, MUL, Const i2) -> Const (mul i1 i2)
	| BinOp (Const i1, HMUL, Const i2) ->
		let i1' = big_int_of_int64 i1 in
		let i2' = big_int_of_int64 i2 in
		let mult = mult_big_int i1' i2' in
		let max_long = big_int_of_int64 max_int in
		let divided = div_big_int mult max_long in
		let result = int64_of_big_int divided in
		Const result
	| BinOp (Const i1, DIV, Const i2) -> Const (div i1 i2)
	| BinOp (Const i1, MOD, Const i2) -> Const (rem i1 i2)
	| BinOp (Const 1L, (AND|OR), Const 1L) -> Const 1L 
	| BinOp (Const 0L, (AND|OR), Const 0L) -> Const 0L
	| BinOp (Const 1L, OR, Const _) 
	| BinOp (Const _, OR, Const 1L) -> Const 1L
	| BinOp (Const 0L, AND, Const _)
	| BinOp (Const _, AND, Const 0L) -> Const 0L
	| BinOp (Const i1, AND, Const i2) -> Const (logand i1 i2)
	| BinOp (Const i1, OR, Const i2) -> Const (logor i1 i2)
	| BinOp (Const i1, XOR, Const i2) -> Const (logxor i1 i2)
	| BinOp (Const i1, LSHIFT, Const i2) ->
		let i2' = to_int i2 in
		Const (shift_left i1 i2')
	| BinOp (Const i1, RSHIFT, Const i2) ->
		let i2' = to_int i2 in
		Const (shift_right_logical i1 i2')
	| BinOp (Const i1, ARSHIFT, Const i2) ->
		let i2' = to_int i2 in
		Const (shift_right i1 i2')
	| BinOp (Const i1, EQ, Const i2) -> if (compare i1 i2) = 0 then Const (1L) else Const (0L)
	| BinOp (Const i1, NEQ, Const i2) -> if (compare i1 i2) <> 0 then Const (1L) else Const (0L)
	| BinOp (Const i1, LT, Const i2) -> if (compare i1 i2) < 0 then Const (1L) else Const (0L)
	| BinOp (Const i1, GT, Const i2) -> if (compare i1 i2) > 0 then Const (1L) else Const (0L)
	| BinOp (Const i1, LEQ, Const i2) -> if (compare i1 i2) <= 0 then Const (1L) else Const (0L)
	| BinOp (Const i1, GEQ, Const i2) -> if (compare i1 i2) >= 0 then Const (1L) else Const (0L)
	| BinOp (e1, op, e2) ->
		begin
			match (constant_folding e1), (constant_folding e2) with
			| (Const _ as c1), (Const _ as c2)-> constant_folding (BinOp (c1, op, c2))
			| e1', e2' -> BinOp (e1', op, e2')
		end
	| Call (e', elist, i) ->
		let folded_list = List.map ~f: constant_folding elist in
		let folded_e = constant_folding e' in
		Call (folded_e, folded_list, i)
	| ESeq (s, e') -> ESeq (s, constant_folding e')
	| Mem (e', t) -> Mem (constant_folding e', t)
	| Const _
	| Name _
	| Temp _ -> e
