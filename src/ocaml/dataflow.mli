open Graph
open Cfg

type direction = [`Forward | `Backward]

module type LowerSemilattice = sig
  (* data associated with each control flow node *)
  type data

  (* meet operation in the semilattice *)
  val ( ** ) : data -> data -> data

  (* equality over data values *)
  val ( === ) : data -> data -> bool

  val to_string : data -> string
end

module type CFGWithLatticeT = sig
  module Lattice : LowerSemilattice
  module CFG : ControlFlowGraph

  type graph = CFG.t
  type node = CFG.V.t
  type edge = CFG.E.t
  type data = Lattice.data

  type extra_info

  val direction : direction
  val init: extra_info -> graph -> node -> data
  val transfer : extra_info -> edge -> data -> data
end

module type Analysis = sig
  module CFGL : CFGWithLatticeT
  open CFGL

  val direction : direction
  val iterative : extra_info -> graph -> edge -> data
  val worklist : extra_info -> graph -> edge -> data
end

module GenericAnalysis (CFGL: CFGWithLatticeT) : Analysis with module CFGL = CFGL
