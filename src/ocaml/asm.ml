open Core.Std

(******************************************************************************)
(* types                                                                      *)
(******************************************************************************)
type label = string

type const = int64

type reg =
  | Rax
  | Rbx
  | Rcx
  | Cl
  | Rdx
  | Rsi
  | Rdi
  | Rbp
  | Rsp
  | R8
  | R9
  | R10
  | R11
  | R12
  | R13
  | R14
  | R15

type abstract_reg =
  | Fake of string
  | Real of reg

type scale =
  | One
  | Two
  | Four
  | Eight

type 'reg mem =
  | Base    of const option * 'reg
  | Off     of const option * 'reg * scale
  | BaseOff of const option * 'reg * 'reg * scale

type 'reg operand =
  | Label of label
  | Reg   of 'reg
  | Const of const
  | Mem   of 'reg mem

type 'reg asm_template =
  | Op of string * 'reg operand list
  | Lab of label
  | Directive of string * string list

type abstract_asm = abstract_reg asm_template

type asm = reg asm_template

(******************************************************************************)
(* important register helpers                                                 *)
(******************************************************************************)
let arg_reg = function
  | 0 -> Rdi
  | 1 -> Rsi
  | 2 -> Rdx
  | 3 -> Rcx
  | 4 -> R8
  | 5 -> R9
  | _ -> failwith "nth_arg_reg: bad arg num"

let ret_reg = function
  | 0 -> Rdi
  | 1 -> Rsi
  | _ -> failwith "nth_ret_reg: bad arg num"

let ( $ ) n reg =
  Base (Some (Int64.of_int n), reg)

let const n =
  Const (Int64.of_int n)

let num_caller_save = 9

(******************************************************************************)
(* functions                                                                  *)
(******************************************************************************)
let string_of_const n =
  sprintf "$%s" (Int64.to_string n)

let string_of_label l =
  l

let string_of_reg reg =
  match reg with
  | Rax -> "%rax"
  | Rbx -> "%rbx"
  | Rcx -> "%rcx"
  | Cl  -> "%cl"
  | Rdx -> "%rdx"
  | Rsi -> "%rsi"
  | Rdi -> "%rdi"
  | Rbp -> "%rbp"
  | Rsp -> "%rsp"
  | R8  -> "%r8"
  | R9  -> "%r9"
  | R10 -> "%r10"
  | R11 -> "%r11"
  | R12 -> "%r12"
  | R13 -> "%r13"
  | R14 -> "%r14"
  | R15 -> "%r15"

let string_of_abstract_reg reg =
  match reg with
  | Fake s -> "%" ^ s
  | Real r -> string_of_reg r

let string_of_scale scale =
  match scale with
  | One   -> "1"
  | Two   -> "2"
  | Four  -> "4"
  | Eight -> "8"

let string_of_mem f mem =
  let soc = string_of_const in
  let sos = string_of_scale in

  match mem with
  | Base (None, b) -> sprintf "(%s)" (f b)
  | Base (Some n, b) -> sprintf "%s(%s)" (soc n) (f b)
  | Off (None, o, s) -> sprintf "(,%s,%s)" (f o) (sos s)
  | Off (Some n, o, s) -> sprintf "%s(,%s,%s)" (soc n) (f o) (sos s)
  | BaseOff (None, b, o, s) -> sprintf "(%s,%s,%s)" (f b) (f o) (sos s)
  | BaseOff (Some n, b, o, s) -> sprintf "%s(%s,%s,%s)" (soc n) (f b) (f o) (sos s)

let string_of_operand f o =
  match o with
  | Label l -> string_of_label l
  | Reg r -> f r
  | Const c -> string_of_const c
  | Mem m -> string_of_mem f m

let string_of_asm_template f asm =
  let comma_spaces ss = String.concat ~sep:", " ss in
  let soo = string_of_operand f in
  match asm with
  | Op (s, operands) -> sprintf "    %s %s" s (comma_spaces (List.map ~f:soo operands))
  | Lab s -> s ^ ":"
  | Directive (d, args) -> sprintf "    .%s %s" d (comma_spaces args)

let string_of_abstract_asm asm =
  string_of_asm_template string_of_abstract_reg asm

let string_of_asm asm =
  string_of_asm_template string_of_reg asm

let string_of_asms asms =
  Util.join (List.map ~f:string_of_asm asms)

let fakes_of_reg r =
  match r with
  | Fake s -> [s]
  | Real _ -> []

let fakes_of_regs rs =
  Util.ordered_dedup (List.concat_map ~f:fakes_of_reg rs)

let fakes_of_operand o =
  match o with
  | Reg r -> fakes_of_reg r
  | Mem (Base (_, r)) -> fakes_of_reg r
  | Mem (Off (_, r, _)) -> fakes_of_reg r
  | Mem (BaseOff (_, r1, r2, _)) -> fakes_of_regs [r1; r2]
  | Label _
  | Const _ -> []

let fakes_of_operands os =
  Util.ordered_dedup (List.concat_map ~f:fakes_of_operand os)

let fakes_of_asm asm =
  match asm with
  | Op (_, ops) -> Util.ordered_dedup (List.concat_map ~f:fakes_of_operand ops)
  | Lab _
  | Directive _ -> []

let fakes_of_asms asms =
  Util.ordered_dedup (List.concat_map ~f:fakes_of_asm asms)

(******************************************************************************)
(* instructions                                                               *)
(******************************************************************************)
let die () =
  failwith "invalid assembly instruction"

(* arithmetic *)
let binop_arith_generic (arith_name: string)
  (src: 'reg operand) (dest: 'reg operand) : 'reg asm_template =
  match src, dest with
  | Reg _, Reg _
  | Reg _, Mem _
  | Mem _, Reg _
  | Const _, Reg _
  | Const _, Mem _ -> Op (arith_name, [src; dest])
  | _ -> die ()

let unop_arith_generic
  (arith_name: string)
  (src: 'reg operand)
    : 'reg asm_template =
  match src with
  | (Reg _ | Mem _ ) -> Op (arith_name, [src])
  | _ -> die ()

let addq src dest = binop_arith_generic "addq" src dest
let subq src dest = binop_arith_generic "subq" src dest
let incq dest = unop_arith_generic "incq" dest
let decq dest = unop_arith_generic "decq" dest
let imulq src = unop_arith_generic "imulq" src
let idivq src = unop_arith_generic "idivq" src
let negq src = unop_arith_generic "negq" src 

(* logical/bitwise operations *)
let logic_generic logic_name src dest =
  match src, dest with
  | _, (Mem _ | Reg _) -> Op (logic_name, [src; dest])
  | _ -> die ()

let andq src dest = logic_generic "andq" src dest
let orq src dest = logic_generic "orq" src dest
let xorq src dest = logic_generic "xorq" src dest


(* shifts *)
let shift_generic shiftname a b =
  match a, b with
  | (Reg _ | Const _ ), (Mem _ | Reg _ ) -> Op (shiftname, [a; b])
  | _ -> die ()

let shlq a b = shift_generic "shlq" a b
let shrq a b = shift_generic "shrq" a b
let sarq a b = shift_generic "sarq" a b


(* move/setting operations *)
let mov_generic mov_name src dest =
  match src, dest with
  | Mem _, Mem _ -> die ()
  | _, (Mem _ | Reg _ ) -> Op (mov_name, [src; dest])
  | _ -> die ()

let mov src dest = mov_generic "mov" src dest
let movq src dest = mov_generic "movq" src dest

let set_generic setname dest =
  match dest with
  | Mem _ | Reg _ -> Op (setname, [dest])
  | _ -> die ()

let sete dest = set_generic "sete" dest
let setne dest = set_generic "setne" dest
let setl dest = set_generic "setl" dest
let setg dest = set_generic "setg" dest
let setle dest = set_generic "setle" dest
let setge dest = set_generic "setge" dest
let setz dest = set_generic "setz" dest
let setnz dest = set_generic "setnz" dest
let sets dest = set_generic "sets" dest
let setns dest = set_generic "setns" dest

(* laod effective address *)
let leaq src dest =
  match src, dest with
  | Mem _, Reg _ -> Op ("leaq", [src; dest])
  | _ -> die ()

(* comparisons *)
let cmpq a b =
  match a, b with
  | Mem _, Mem _ -> die ()
  | _, (Reg _ | Mem _) -> Op ("cmpq", [a; b])
  | _ -> die ()

(* test *)
let test a b =
  match a, b with
  | Reg _, (Reg _ | Mem _) -> Op ("test", [a; b])
  | _ -> die ()

(* stack operations *)
let push a =
  match a with
  | Reg _ | Mem _ | Const _ -> Op ("push", [a])
  | _ -> die ()

let pop a =
  match a with
  | Reg _ | Mem _ -> Op ("pop", [a])
  | _ -> die ()

let enter a b =
  match a, b with
  | Const _, Const _ -> Op ("enter", [a; b])
  | _ -> die ()


(* jumps *)
let unop_label (op: string) (l: 'reg operand) =
  match l with
  | Label _ -> Op (op, [l])
  | _ -> die ()

let jmp  l = unop_label "jmp"  l
let je   l = unop_label "je"   l
let jne  l = unop_label "jne"  l
let jnz  l = unop_label "jnz"  l
let jz   l = unop_label "jz"   l
let jg   l = unop_label "jg"   l
let jge  l = unop_label "jge"  l
let jl   l = unop_label "jl"   l
let jle  l = unop_label "jle"  l
let js   l = unop_label "js"   l
let jns  l = unop_label "jns"  l
let call l = unop_label "call" l

(* zeroops *)
let label_op l = Lab l
let leave = Op ("leave", [])
let ret = Op ("retq", [])
