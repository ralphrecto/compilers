open Core.Std
open Asm

module FreshReg   = Fresh.Make(struct let name = "_asmreg" end)
module FreshLabel = Fresh.Make(struct let name = "_asmlabel" end)

let rec munch_expr (e: Ir.expr) : abstract_reg * abstract_asm list =
  match e with
  | BinOp (e1, opcode, e2) -> begin
    let (reg1, asm1) = munch_expr e1 in
    let (reg2, asm2) = munch_expr e2 in

    let cmp_action set_func =
      let cmp_asm = [
        cmpq (Reg reg1) (Reg reg2);
        set_func (Reg reg2);
      ] in
      (reg2, asm1 @ asm2 @ cmp_asm) in

    (* Java style shifting: mod RHS operand by word size *)
    let shift_action shift_func =
      let shift_asm = [
        movq (Reg reg2) (Reg (Real Rcx));
        shift_func (Reg (Real Cl)) (Reg reg1);
      ] in
      (reg1, asm1 @ asm2 @ shift_asm) in

      match opcode with
      | ADD -> (reg2, asm1 @ asm2 @ [addq (Reg reg1) (Reg reg2)])
      | SUB -> (reg2, asm1 @ asm2 @ [subq (Reg reg1) (Reg reg2)])
      | MUL | HMUL -> begin
        let mul_asm = [
          movq (Reg reg2) (Reg (Real Rax));
          imulq (Reg reg1);
        ] in
        let r = if opcode = MUL then Rax else Rdx in
        (Real r, asm1 @ asm2 @ mul_asm)
      end
      | DIV | MOD -> begin
        let div_asm = [
          movq (Reg reg1) (Reg (Real Rax));
          idivq (Reg reg2);
        ] in
        let r = if opcode = DIV then Rax else Rdx in
        (Real r, asm1 @ asm2 @ div_asm)
      end
      | AND-> (reg2, asm1 @ asm2 @ [andq (Reg reg1) (Reg reg2)])
      | OR-> (reg2, asm1 @ asm2 @ [orq (Reg reg1) (Reg reg2)])
      | XOR-> (reg2, asm1 @ asm2 @ [xorq (Reg reg1) (Reg reg2)])
      | LSHIFT -> shift_action salq
      | RSHIFT -> shift_action shrq
      | ARSHIFT -> shift_action sarq
      | EQ -> cmp_action sete
      | NEQ-> cmp_action setne
      | LT -> cmp_action setl
      | GT -> cmp_action setg
      | LEQ -> cmp_action setle
      | GEQ -> cmp_action setge
  end
  | Call (func, arglist) -> failwith "implement me"
  | Const c ->
      let new_tmp = FreshReg.fresh () in
      (Fake new_tmp, [mov (Asm.Const c) (Reg (Fake new_tmp))])
  | Mem (e, memtype) ->
      let (e_reg, e_asm) = munch_expr e in
      let new_tmp = FreshReg.fresh () in
      (Fake new_tmp, [mov (Mem (Base (None, e_reg))) (Reg (Fake new_tmp))])
  | Name str ->
      let new_tmp = FreshReg.fresh () in
      (Fake new_tmp, [mov (Label str) (Reg (Fake new_tmp))])
  | Temp str -> (Fake str, [])
  | ESeq _ -> failwith "eseq shouldn't exist"

let rec munch_stmt (s: Ir.stmt) : abstract_asm list =
	match s with
  | CJumpOne (e1, tru) ->
		begin
			match e1 with
			| BinOp (e1, ((EQ|NEQ|LT|GT|LEQ|GEQ) as op), e2) ->
				let fresh_l = FreshLabel.fresh () in
				let cond_jump =
					match op with
					| EQ -> jne (Asm.Label fresh_l);
					| NEQ -> je (Asm.Label fresh_l);
					| LT -> jge (Asm.Label fresh_l);
					| GT -> jle (Asm.Label fresh_l);
					| LEQ -> jg (Asm.Label fresh_l);
					| GEQ -> jl (Asm.Label fresh_l);
				in
				let (e1_reg, e1_lst) = munch_expr e1 in
				let (e2_reg, e2_lst) = munch_expr e2 in
				let jump_lst = [
					cmpq (Reg e2_reg) (Reg e1_reg);
					cond_jump;
					jmp (Asm.Label tru);
					label_op fresh_l;
				] in
				e1_lst @ e2_lst @ jump_lst
			| _ ->
				let (binop_reg, binop_lst) = munch_expr e1 in
				let fresh_l = FreshLabel.fresh () in
				let jump_lst = [
					cmpq (Const 0L) (Reg binop_reg);
					jz (Asm.Label fresh_l);
					jmp (Asm.Label tru);
					label_op fresh_l;
				] in
				binop_lst @ jump_lst
		end
  | Jump (Name s) -> [jmp (Asm.Label s)]
  | Exp e -> snd (munch_expr e)
  | Label l -> [label_op l]
  | Move (e1, e2) ->
		let (e1_reg, e1_lst) = munch_expr e1 in
		let (e2_reg, e2_lst) = munch_expr e2 in
		e1_lst @ e2_lst @ [movq (Reg e2_reg) (Reg e1_reg)]
  | Seq s_list -> List.map ~f:munch_stmt s_list |> List.concat
  | Return -> [ret]
	| Jump _ -> failwith "jump to a non label shouldn't exist"
	| CJump _ -> failwith "cjump shouldn't exist"

let register_allocate asms =
  (* [fakes_of_operand ] returns the names of all the fake registers in asm. *)

  (* [fakes asm] returns the names of all the fake registers in asm. *)
  let fakes_of_asm asm =
    match asm with
    | BinOp (_, Reg (Fake s1), Reg (Fake s2)) -> [s1; s2]
    | BinOp (_, Reg (Fake s1), _)
    | BinOp (_, _, Reg (Fake s1)) -> [s1]
    | BinOp _ -> []
    | UnOp (_, Reg (Fake s)) -> [s]
    | UnOp _ -> []
    | ZeroOp _ -> []
  in

  (* [reals asm] returns all of the real registers in asm. *)
  let fakes asm =
    match asm with
    | BinOp (_, Reg (Fake s1), Reg (Fake s2)) -> [s1; s2]
    | BinOp (_, Reg (Fake s1), _)
    | BinOp (_, _, Reg (Fake s1)) -> [s1]
    | BinOp _ -> []
    | UnOp (_, Reg (Fake s)) -> [s]
    | UnOp _ -> []
    | ZeroOp _ -> []
  in

  (* env maps each fake name to an index, starting at 1, into the stack. For
   * example, if the fake name "foo" is mapped to n in env, then Reg (Fake
   * "foo") will be spilled to -8n(%rbp). *)
  let env =
    List.concat_map ~f:fakes asms
    |> List.dedup
    |> List.mapi ~f:(fun i asm -> (asm, i + 1))
    |> String.Map.of_alist_exn
  in

  let translate name env =
    let i = String.Map.find_exn env name in
    let offset = Int64.of_int (-8 * i) in
    Mem (Base (Some offset, Rax))
  in

  (* op "foo", "bar", "baz"
   *
   * mov -8(%rbp), %rax  \
   * mov -16(%rbp), %rbx  } pre
   * mov -24(%rbp), %rcx /
   *
   * op %rax, %rbx, %rcx  } translation
   *
   * mov %rax, -8(%rbp)  \
   * mov %rbx, -16(%rbp)  } post
   * mov %rcx, -24(%rbp) /
   *)
  let allocate asm env =
    let (op, args) =
      match asm with
      | BinOp (s, a, b) -> (s, [a; b])
      | UnOp (s, a) -> (s, [a])
      | ZeroOp s  -> (s, [])
    in
    let pre = List.map args ~f:(fun fake -> failwith "a") in
    let translation = failwith "" in
    let post = failwith "A"  in
    pre @ translation @ post
  in

  (* help asms env [] *)
  failwith "a"
