open Core.Std
open OUnit
open TestUtil
open Util

module AsmsEq = struct
  let (===) (a: Asm.asm list) (b: Asm.asm list) : unit =
    assert_equal ~printer:(fun a -> "\n" ^ Asm.string_of_asms a ^ "\n") a b
end

module Dummy = struct
  open Func_context
  let dummy_ctx = {num_args = 0; num_rets = 0; max_args = 0; max_rets = 0;}
  let dummy_fcontexts = String.Map.empty 
end

module Ir_Abbreviations = struct
  open Ir
  (* binop_codes *)
  let add_ (e1: Ir.expr) (e2: Ir.expr) = Ir.BinOp (e1, Ir.ADD, e2)
  let sub_ (e1: Ir.expr) (e2: Ir.expr) = Ir.BinOp (e1, Ir.SUB, e2)
  let mul_ (e1: Ir.expr) (e2: Ir.expr) = Ir.BinOp (e1, Ir.MUL, e2)
  let hmul_ (e1: Ir.expr) (e2: Ir.expr) = Ir.BinOp (e1, Ir.HMUL, e2)
  let div_ (e1: Ir.expr) (e2: Ir.expr) = Ir.BinOp (e1, Ir.DIV, e2)
  let mod_ (e1: Ir.expr) (e2: Ir.expr) = Ir.BinOp (e1, Ir.MOD, e2)
  let and_ (e1: Ir.expr) (e2: Ir.expr) = Ir.BinOp (e1, Ir.AND, e2)
  let or_ (e1: Ir.expr) (e2: Ir.expr) = Ir.BinOp (e1, Ir.OR, e2)
  let xor_ (e1: Ir.expr) (e2: Ir.expr) = Ir.BinOp (e1, Ir.XOR, e2)
  let lshift_ (e1: Ir.expr) (e2: Ir.expr) = Ir.BinOp (e1, Ir.LSHIFT, e2)
  let rshift_ (e1: Ir.expr) (e2: Ir.expr) = Ir.BinOp (e1, Ir.RSHIFT, e2)
  let arshift_ (e1: Ir.expr) (e2: Ir.expr) = Ir.BinOp (e1, Ir.ARSHIFT, e2)
  let eq_ (e1: Ir.expr) (e2: Ir.expr) = Ir.BinOp (e1, Ir.EQ, e2)
  let neq_ (e1: Ir.expr) (e2: Ir.expr) = Ir.BinOp (e1, Ir.NEQ, e2)
  let lt_ (e1: Ir.expr) (e2: Ir.expr) = Ir.BinOp (e1, Ir.LT, e2)
  let gt_ (e1: Ir.expr) (e2: Ir.expr) = Ir.BinOp (e1, Ir.GT, e2)
  let leq_ (e1: Ir.expr) (e2: Ir.expr) = Ir.BinOp (e1, Ir.LEQ, e2)
  let geq_ (e1: Ir.expr) (e2: Ir.expr) = Ir.BinOp (e1, Ir.GEQ, e2)

  (* exprs *)
  let call (e1: Ir.expr) (args: Ir.expr list) = Ir.Call (e1, args)
  let const (i: Int64.t) = Ir.Const i
  let eseq (s: Ir.stmt) (e: Ir.expr) = Ir.ESeq (s, e)
  let mem (e: Ir.expr) (mem_type: Ir.mem_type) = Ir.Mem (e, mem_type)
  let name (s: string) = Ir.Name s
  let temp (s: string) = Ir.Temp s

  (* stmts *)
  let cjump (e: Ir.expr) (tru: string) (fls: string) = Ir.CJump (e, tru, fls)
  let cjumpone (e: Ir.expr) (tru: string) = Ir.CJumpOne (e, tru)
  let jump (e: Ir.expr) = Ir.Jump e
  let exp (e: Ir.expr) = Ir.Exp e
  let label (l: string) = Ir.Label l
  let move (e1: Ir.expr) (e2: Ir.expr) = Ir.Move (e1, e2)
  let seq (ss: Ir.stmt list) = Ir.Seq ss
  let return = Ir.Return

end

module Asm_Abbreviations = struct
  open Asm
  let arax = Reg (Real Rax)
  let arbx = Reg (Real Rbx)
  let arcx = Reg (Real Rcx)
  let ardx = Reg (Real Rdx)
  let arsi = Reg (Real Rsi)
  let ardi = Reg (Real Rdi)
  let arbp = Reg (Real Rbp)
  let arsp = Reg (Real Rsp)
  let ar8  = Reg (Real R8)
  let ar9  = Reg (Real R9)
  let ar10 = Reg (Real R10)
  let ar11 = Reg (Real R11)
  let ar12 = Reg (Real R12)
  let ar13 = Reg (Real R13)
  let ar14 = Reg (Real R14)
  let ar15 = Reg (Real R15)

  let fake s = Reg (Fake s)
  let a = fake "a"
  let b = fake "b"
  let c = fake "c"
  let w = fake "w"
  let x = fake "x"
  let y = fake "y"
  let z = fake "z"

  let rax = Reg Rax
  let rbx = Reg Rbx
  let rcx = Reg Rcx
  let rdx = Reg Rdx
  let rsi = Reg Rsi
  let rdi = Reg Rdi
  let rbp = Reg Rbp
  let rsp = Reg Rsp
  let r8  = Reg R8
  let r9  = Reg R9
  let r10 = Reg R10
  let r11 = Reg R11
  let r12 = Reg R12
  let r13 = Reg R13
  let r14 = Reg R14
  let r15 = Reg R15

  let mem (r: 'a) : 'a operand =
    Mem (Base (None, r))

  let ( * ) (n: int64) (mem: 'a operand) : 'a operand =
    match mem with
    | Mem (Base (None, b)) -> Mem (Base (Some n, b))
    | Mem (Off (None, o, s)) -> Mem (Off (Some n, o, s))
    | Mem (BaseOff (None, b, o, s)) -> Mem (BaseOff (Some n, b, o, s))
    | _ -> failwith "invalid offset"
end

(* 
 * there isn't a separate chomp_expr test
 * because the cases other than binop are identical to munch 
 *)
let test_chomp_binop () =
  let open Ir_Abbreviations in
  let open Ir in
  let open Asm in 
  let open AsmsEq in
  let open Tiling in
  let module IA = Ir_Abbreviations in

  (* mod2 == 0 with no set destination *)
  FreshReg.reset ();
  let _mod2_cmp_0 = eq_ (mod_ (temp "x") (IA.const 2L)) (IA.const 0L) in
  let fresh_reg = Reg (Fake (FreshReg.fresh ())) in
  let _expected = [
    mov (Reg (Fake "x")) fresh_reg;
    bt (Asm.Const 0L) fresh_reg;
    setnc fresh_reg 
  ]
  in
  (* mod2 == 0 with set destination *)

 ()


let test_register_allocation () =
  let open Asm_Abbreviations in
  let open Asm in
  let open AsmsEq in
  let open Tiling in

  let input = [] in
  let expected = [] in
  expected === register_allocate input;

  let input = [mov arax arbx] in
  let expected = [mov rax rbx] in
  expected === register_allocate input;

  let input = [mov x arbx] in
  let expected = [
    mov (-8L * mem Rbp) r13;
    mov r13 rbx;
    mov r13 (-8L * mem Rbp);
  ] in
  expected === register_allocate input;

  let input = [mov x y] in
  let expected = [
    mov (-8L * mem Rbp) r13;
    mov (-16L * mem Rbp) r14;
    mov r13 r14;
    mov r13 (-8L * mem Rbp);
    mov r14 (-16L * mem Rbp);
  ] in
  expected === register_allocate input;

  let input = [
    mov x y;
    mov z x;
  ] in
  let expected = [
    mov (-8L * mem Rbp) r13;
    mov (-16L * mem Rbp) r14;
    mov r13 r14;
    mov r13 (-8L * mem Rbp);
    mov r14 (-16L * mem Rbp);
    mov (-24L * mem Rbp) r13;
    mov (-8L * mem Rbp) r14;
    mov r13 r14;
    mov r13 (-24L * mem Rbp);
    mov r14 (-8L * mem Rbp);
  ] in
  expected === register_allocate input;

  let input = [
    push arbp;
    mov arsp arbp;
    mov x y;
    andq z x;
    push a;
    leave;
    ret;
  ] in
  let expected = [
    push rbp;
    mov rsp rbp;
    mov (-8L * mem Rbp) r13;
    mov (-16L * mem Rbp) r14;
    mov r13 r14;
    mov r13 (-8L * mem Rbp);
    mov r14 (-16L * mem Rbp);
    mov (-24L * mem Rbp) r13;
    mov (-8L * mem Rbp) r14;
    andq r13 r14;
    mov r13 (-24L * mem Rbp);
    mov r14 (-8L * mem Rbp);
    mov (-32L * mem Rbp) r13;
    push r13;
    mov r13 (-32L * mem Rbp);
    leave;
    ret;
  ] in
  expected === register_allocate input;

  let input = [Lab "foo"] in
  let expected = [Lab "foo"] in
  expected === register_allocate input;

  let input = [
    Directive ("align", ["4"]);
    Directive ("globl", ["foo"]);
  ] in
  let expected = [
    Directive ("align", ["4"]);
    Directive ("globl", ["foo"]);
  ] in
  expected === register_allocate input;

  ()

(* !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! *)
(* ! DON'T FORGET TO ADD YOUR TESTS HERE                                     ! *)
(* !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! *)
let main () =
    "suite" >::: [
      "test_chomp"               >:: test_chomp_binop;
      "test_register_allocation" >:: test_register_allocation;
    ] |> run_test_tt_main

let _ = main ()
