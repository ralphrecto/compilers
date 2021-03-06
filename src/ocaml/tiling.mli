open Core.Std
open Func_context

(* variable used for implicit 0th argument *)
val ret_ptr_reg : Asm.abstract_reg

(* Maximal munch tiling algorithm with baby tiles.
 *
 * For example, munch_expr takes in
 *     - a debug flag to pretty print comments in asm
 *     - the function context of the current function being munched,
 *     - a map of all function contexts, and
 *     - an IR expression; it returns
 *     - the name of the register holding the result and a list of asm
 *       instructions.
 * The other munch functions are similar.
 *
 * Here is our stack layout:
 *
 *                 | ...       |
 *                 | ARG8      | | args to this function
 *                 | ARG7      | |
 *                 | ARG6      |/
 *                 | saved rip |
 *         rbp --> | saved rbp |
 *               1 | rax       |\
 *               2 | rbx       | | callee-saved
 *               3 | rcx       | |
 *               4 | rdx       | |
 *               5 | rsi       | |
 *               6 | rdi       | |
 *               7 | r8        | |
 *               8 | r9        | |
 *               9 | r10       | |
 *              10 | r11       | |
 *              11 | r12       | | callee-saved
 *              12 | r13       | | callee-saved
 *              13 | r14       | | callee-saved
 *              14 | r15       |/  callee-saved
 *              15 | r13'      |\
 *              16 | r14'      | | spill space for shuttle registers
 *              17 | r15'      |/
 *              18 | temp_0    |\  <-- beginning of stack space for spills
 *                 | temp_1    | | stack space for fake registers
 *                 | ...       | |
 *                 | temp_n    |/
 *                 | padding   | | optional padding to 16-byte align stack
 *                 | RETm      |\
 *                 | ...       | | returns from function being called
 *                 | RET4      | |
 *                 | RET3      |/
 *                 | ARGo      |\
 *                 | ...       | | arguments to function being called
 *                 | ARG7      | |
 *         rsp --> | ARG6      |/
 *
 * From top to bottom:
 *     - ARG6, ARG7, ... are passed to us above the saved return pointer
 *     - the return pointer is pushed on the stack by the call instruction
 *     - the enter instruction stores the old value of rbp and points rbp there
 *     - we save all callee-saved registers because we are a callee
 *     - we save all caller-saved registers because we call functions too
 *     - we have a stack location for every temp we  use
 *     - we pad to 16-bytes before calling a function
 *     - we stack allocate space for the function we're calling to return stuff
 *     - we then push ARG6, ARG7, ...
 *)
type ('input, 'output) with_fcontext =
  ?debug:bool -> func_context -> func_contexts -> 'input -> 'output
type ('input, 'output) without_fcontext =
  ?debug:bool -> func_contexts -> 'input -> 'output

val munch_expr      : (Ir.expr, Asm.fake * Asm.abstract_asm list) with_fcontext
val munch_stmt      : (Ir.stmt, Asm.abstract_asm list) with_fcontext
val munch_func_decl : (Ir.func_decl, Asm.abstract_asm list) without_fcontext
val munch_comp_unit : (Ir.comp_unit, (Asm.asm list * (Asm.abstract_asm list list))) without_fcontext

(* Maximal munch tiling algorithm with good tiles. *)
val chomp_expr      : (Ir.expr, Asm.abstract_reg * Asm.abstract_asm list) with_fcontext
val chomp_stmt      : (Ir.stmt, Asm.abstract_asm list) with_fcontext
val chomp_func_decl : (Ir.func_decl, Asm.abstract_asm list) without_fcontext
val chomp_comp_unit : (Ir.comp_unit, (Asm.asm list * (Asm.abstract_asm list list))) without_fcontext

(* Naive register allocation.
 *
 * It is assumed that you call register_allocate with the body of a function.
 *
 * First, the list of fake names appearing anywhere in the assembly is gathered
 * in the order in which they first appear. See Asm.fakes_of_asms for more
 * information.
 *
 * Next, fake names are associated with a position in the stack in the order in
 * which they were gathered. For example, the first fake names is spilled to
 * -8(%rbp), the next fake names is spilled to -16(%rbp), etc.
 *
 * Next, for each assembly instruction the values of each fake register is read
 * from the stack before the instruction and written to the stack after the
 * instruction. The fake registers are assigned to registers r13, r14, and r15
 * in the order they appear. For example, the following abstract assembly.
 *
 *     op "foo", "bar", "baz"
 *
 * is converted to the following assembly:
 *
 *     mov -8(%rbp), %rax  \
 *     mov -16(%rbp), %rbx  } read from stack
 *     mov -24(%rbp), %rcx /
 *     op %rax, %rbx, %rcx  } translated op
 *     mov %rax, -8(%rbp)  \
 *     mov %rbx, -16(%rbp)  } written to stack
 *     mov %rcx, -24(%rbp) /
 *
 * It is heavily assumed that r13, r14, and r15 are not produced in abstract
 * assembly!
 *
 * Note that this is all register allocate does! It doesn't translate ARGi
 * registers, it doesn't translate RETi registers, it doesn't prepend enter
 * instructions or append leave instructions. All these operations should be
 * performed as the abstract assembly is being produced.
 *)
val register_allocate : ?debug:bool -> Asm.abstract_asm list -> Asm.asm list

type allocator = ?debug:bool -> Asm.abstract_asm list -> Asm.asm list
type eater = ?debug:bool ->
             allocator ->
             Typecheck.full_prog ->
             Ir.comp_unit ->
             Asm.asm_prog
val asm_munch : eater
val asm_chomp : eater
