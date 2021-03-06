package mjw297;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java_cup.runtime.Symbol;
import org.junit.Test;
import static mjw297.Ast.*;
import static mjw297.Sym.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class ParserTest {
    private static HashMap<List<Symbol>, Expr<Position>> exprs = new HashMap<>();

    ////////////////////////////////////////////////////////////////////////////
    // Helper Functions
    ////////////////////////////////////////////////////////////////////////////
    @SuppressWarnings("deprecation")
    private static Program<Position> parsePos(List<Symbol> symbols) throws Exception {
        MockLexer l = new MockLexer(symbols) ;
        Parser p = new Parser(l);
        return p.parse().value();
    }

    @SuppressWarnings("deprecation")
    private static Program<Position> parse(List<Symbol> symbols) throws Exception {
        MockLexer l = new MockLexer(symbols) ;
        Parser p = new Parser(l);
        Program<Position> prog = p.parse().value();
        return PositionKiller.kill(prog);
    }

    @SuppressWarnings({"unchecked", "varargs"})
    @SafeVarargs
    private static <A> List<A> l(A... xs) {
        return Arrays.asList(xs);
    }

    private static Symbol sym(int type) {
        return SymUtil.sym(type, PositionKiller.dummyPosition.row,
                PositionKiller.dummyPosition.col);
    }

    private static Symbol sym(int type, Object value) {
        return SymUtil.sym(type, PositionKiller.dummyPosition.row,
                           PositionKiller.dummyPosition.col, value);
    }

    private static Symbol sym(int type, int row, int col) {
        return SymUtil.sym(type, row, col);
    }

    private static Symbol sym(int type, Object value, int row, int col) {
        return SymUtil.sym(type, row, col, value);
    }

    private static Symbol sym(Symbol s) {
        return sym(s.sym, s.value());
    }

    private static Position pos(int row, int col) {
        return new Position(row, col);
    }

    public void stmtTestHelper(List<Symbol> syms, Stmt<Position> stmt) throws Exception {
        stmtsTestHelper(syms, l(stmt));
    }

	public void exprErrorTestHelper(List<Symbol> syms) throws Exception {
        exprTestHelper(syms, id("dummy"));
	}

    public void stmtErrorTestHelper(List<Symbol> syms) throws Exception {
        stmtTestHelper(syms, decl(l(underscore())));
	}

    public void stmtsTestHelper(List<Symbol> syms, List<Stmt<Position>> stmts)
           throws Exception {
        List<Symbol> symbols = new ArrayList<>();
        symbols.add(sym(ID, "main"));
        symbols.add(sym(LPAREN));
        symbols.add(sym(RPAREN));
        symbols.add(sym(LBRACE));

        for (Symbol sym : syms) {
            symbols.add(sym);
        }
        symbols.add(sym(RBRACE));

        Program<Position> expected = program(
                l(),
                l(proc(id("main"), l(), block(stmts, Optional.empty())))
        );
        assertEquals(expected, parse(symbols));
    }

    public void exprTestHelper(List<Symbol> syms, Expr<Position> e) throws Exception {
        List<Symbol> symbols = new ArrayList<>();
        symbols.add(sym(ID, "main"));
        symbols.add(sym(LPAREN));
        symbols.add(sym(RPAREN));
        symbols.add(sym(LBRACE));
        symbols.add(sym(ID, "x"));
        symbols.add(sym(EQ));
        for (Symbol sym : syms) {
            symbols.add(sym);
        }
        symbols.add(sym(RBRACE));

        Program<Position> expected = program(
            l(),
            l(proc(id("main"), l(), block(l(asgn(id("x"), e)), Optional.empty())))
        );
        assertEquals(expected, parse(symbols));
        exprs.put(syms, e);
    }

    public void callableTestHelper(List<Symbol> declSyms,
                                   List<Symbol> typeSyms,
                                   Func<Position> f)
                throws Exception {
        List<Symbol> funcSyms = new ArrayList<>();
        for (Symbol s : declSyms) {
            funcSyms.add(sym(s));
        }
        funcSyms.add(sym(COLON));
        for (Symbol s : typeSyms) {
            funcSyms.add(sym(s));
        }
        funcSyms.add(sym(LBRACE));
        funcSyms.add(sym(RBRACE));
        assertEquals(program(l(), l(f)), parse(funcSyms));

        List<Symbol> procSyms = new ArrayList<>();
        for (Symbol s : declSyms) {
            procSyms.add(sym(s));
        }
        procSyms.add(sym(LBRACE));
        procSyms.add(sym(RBRACE));
        Proc<Position> p = proc(f.name, f.args, f.body);
        assertEquals(program(l(), l(p)), parse(procSyms));
    }

    ////////////////////////////////////////////////////////////////////////////
    // Abbreviations
    ////////////////////////////////////////////////////////////////////////////
    public static AnnotatedId<Position> annotatedId (
        Id<Position> x,
        Type<Position> t
    ) {
        return AnnotatedId.of(PositionKiller.dummyPosition, x, t);
    }

    public static AnnotatedUnderscore<Position> annotatedUnderscore (
        Underscore<Position> u,
        Type<Position> t
    ) {
        return AnnotatedUnderscore.of(PositionKiller.dummyPosition, u, t);
    }

    public static Func<Position> func (
        Id<Position> name,
        List<AnnotatedVar<Position>> args,
        List<Type<Position>> returnType,
        Stmt<Position> body
    ) {
        return Func.of(PositionKiller.dummyPosition, name, args, returnType, body);
    }

    public static Proc<Position> proc (
        Id<Position> name,
        List<AnnotatedVar<Position>> args,
        Stmt<Position> body
    ) {
        return Proc.of(PositionKiller.dummyPosition, name, args, body);
    }

    public static Id<Position> id (
        String x
    ) {
        return Id.of(PositionKiller.dummyPosition, x);
    }

    public static BinOp<Position> binOp (
        BinOpCode c,
        Expr<Position> lhs,
        Expr<Position> rhs
    ) {
        return BinOp.of(PositionKiller.dummyPosition, c, lhs, rhs);
    }

    public static BinOp<Position> plus(Expr<Position> lhs, Expr<Position> rhs) {
        return binOp(BinOpCode.PLUS, lhs, rhs);
    }

    public static BinOp<Position> minus(Expr<Position> lhs, Expr<Position> rhs) {
        return binOp(BinOpCode.MINUS, lhs, rhs);
    }

    public static UnOp<Position> unOp (
        UnOpCode c,
        Expr<Position> e
    ) {
        return UnOp.of(PositionKiller.dummyPosition, c, e);
    }

    public static Index<Position> index (
        Expr<Position> e,
        Expr<Position> index
    ) {
        return Index.of(PositionKiller.dummyPosition, e, index);
    }

    public static Length<Position> length (
        Expr<Position> e
    ) {
        return Length.of(PositionKiller.dummyPosition, e);
    }

    public static NumLiteral<Position> numLiteral (
        long x
    ) {
        return NumLiteral.of(PositionKiller.dummyPosition, x);
    }

    public static BoolLiteral<Position> boolLiteral (
        boolean b
    ) {
        return BoolLiteral.of(PositionKiller.dummyPosition, b);
    }

    public static BoolLiteral<Position> true_ () {
        return boolLiteral(true);
    }

    public static BoolLiteral<Position> false_ () {
        return boolLiteral(false);
    }

    public static StringLiteral<Position> stringLiteral (
        String s
    ) {
        return StringLiteral.of(PositionKiller.dummyPosition, s);
    }

    public static CharLiteral<Position> charLiteral (
        char c
    ) {
        return CharLiteral.of(PositionKiller.dummyPosition, c);
    }

    public static ArrayLiteral<Position> arrayLiteral (
        List<Expr<Position>> xs
    ) {
        return ArrayLiteral.of(PositionKiller.dummyPosition, xs);
    }

    public static Program<Position> program (
        List<Use<Position>> uses,
        List<Callable<Position>> fs
    ) {
        return Program.of(PositionKiller.dummyPosition, uses, fs);
    }

    public static Decl<Position> decl (
        List<Var<Position>> vs
    ) {
        return Decl.of(PositionKiller.dummyPosition, vs);
    }

    public static DeclAsgn<Position> declAsgn (
        List<Var<Position>> vs,
        Expr<Position> e
    ) {
        return DeclAsgn.of(PositionKiller.dummyPosition, vs, e);
    }

    public static Asgn<Position> asgn (
        Expr<Position> indexable,
        Expr<Position> index
    ) {
        return Asgn.of(PositionKiller.dummyPosition, indexable, index);
    }

    public static Block<Position> block (
        List<Stmt<Position>> ss,
        Optional<List<Expr<Position>>> ret
    ) {
        return Block.of(PositionKiller.dummyPosition,
                ss, ret, PositionKiller.dummyPosition);
    }

    public static If<Position> if_ (
        Expr<Position> b,
        Stmt<Position> body
    ) {
        return If.of(PositionKiller.dummyPosition, b, body);
    }

    public static IfElse<Position> ifElse (
        Expr<Position> b,
        Stmt<Position> thenBody,
        Stmt<Position> elseBody
    ) {
        return IfElse.of(PositionKiller.dummyPosition, b, thenBody, elseBody);
    }

    public static While<Position> while_ (
        Expr<Position> b,
        Stmt<Position> body
    ) {
        return While.of(PositionKiller.dummyPosition, b, body);
    }

    public static Int<Position> num (
    ) {
        return Int.of(PositionKiller.dummyPosition);
    }

    public static Bool<Position> bool (
    ) {
        return Bool.of(PositionKiller.dummyPosition);
    }

    public static Array<Position> array (
        Type<Position> t,
        Optional<Expr<Position>> size
    ) {
        return Array.of(PositionKiller.dummyPosition, t, size);
    }

    public static Use<Position> use (
        Id<Position> x
    ) {
        return Use.of(PositionKiller.dummyPosition, x);
    }

    public static Underscore<Position> underscore (
    ) {
        return Underscore.of(PositionKiller.dummyPosition);
    }

    public static FuncCall<Position> funcCall (
        Id<Position> f,
        List<Expr<Position>> args
    ) {
        return FuncCall.of(PositionKiller.dummyPosition, f, args);
    }

    public static ProcCall<Position> procCall (
        Id<Position> f,
        List<Expr<Position>> args
    ) {
        return ProcCall.of(PositionKiller.dummyPosition, f, args);
    }

    ////////////////////////////////////////////////////////////////////////////
    // Simple Programs and Use Tests
    ////////////////////////////////////////////////////////////////////////////
    @Test
    public void emptyMainTest() throws Exception {
        List<Symbol> symbols = Arrays.asList(
            sym(ID, "main"),
            sym(LPAREN),
            sym(RPAREN),
            sym(LBRACE),
            sym(RBRACE)
        );
        Program<Position> expected = program(l(), l(proc(id("main"), l(),
                block(l(), Optional.empty()))));
        assertEquals(expected, parse(symbols));
    }

    @Test
    public void emptyFooTest() throws Exception {
        List<Symbol> symbols = Arrays.asList(
            sym(ID, "foo_bar'"),
            sym(LPAREN),
            sym(RPAREN),
            sym(LBRACE),
            sym(RBRACE)
        );
        Program<Position> expected = program(l(), l(proc(id("foo_bar'"), l(),
                block(l(), Optional.empty())))
        );
        assertEquals(expected, parse(symbols));
    }

    @Test
    public void singleUseTest() throws Exception {
        List<Symbol> symbols = Arrays.asList(
            sym(USE),
            sym(ID, "foo"),
            sym(ID, "main"),
            sym(LPAREN),
            sym(RPAREN),
            sym(LBRACE),
            sym(RBRACE)
        );
        Program<Position> expected = program(
            l(use(id("foo"))),
            l(proc(id("main"), l(), block(l(), Optional.empty())))
        );
        assertEquals(expected, parse(symbols));
    }

    @Test
    public void singleUsePosTest() throws Exception {
        List<Symbol> symbols = Arrays.asList(
            sym(USE, 1, 1),
            sym(ID, "foo", 1, 5),
            sym(ID, "main", 2, 1),
            sym(LPAREN, 2, 5),
            sym(RPAREN, 2, 6),
            sym(LBRACE, 3, 1),
            sym(RBRACE, 3, 2)
        );
        Program<Position> expected = Program.of(
                pos(0, 5),
                l(Use.of(
                        pos(1, 1),
                        Id.of(pos(1, 5), "foo")
                )),
                l(Proc.of(
                        pos(2, 1),
                        Id.of(pos(2, 1), "main"),
                        l(),
                        Block.of(pos(3, 1), l(), Optional.empty(),pos(-1,-1))
                ))
        );
        assertEquals(expected, parsePos(symbols));
    }

    @Test
    public void multiUseTest() throws Exception {
        List<Symbol> symbols = Arrays.asList(
            sym(USE), sym(ID, "foo"),
            sym(USE), sym(ID, "bar"),
            sym(USE), sym(ID, "foo"),
            sym(USE), sym(ID, "bar"),
            sym(ID, "main"),
            sym(LPAREN),
            sym(RPAREN),
            sym(LBRACE),
            sym(RBRACE)
        );
        Program<Position> expected = program(
                l(
                        use(id("foo")),
                        use(id("bar")),
                        use(id("foo")),
                        use(id("bar"))
                ),
                l(proc(id("main"), l(), block(l(), Optional.empty())))
        );
        assertEquals(expected, parse(symbols));
    }

    @Test
    public void multiProcTest() throws Exception {
        List<Symbol> symbols = Arrays.asList(
            sym(ID, "foo"), sym(LPAREN), sym(RPAREN), sym(LBRACE), sym(RBRACE),
            sym(ID, "bar"), sym(LPAREN), sym(RPAREN), sym(LBRACE), sym(RBRACE),
            sym(ID, "baz"), sym(LPAREN), sym(RPAREN), sym(LBRACE), sym(RBRACE)
        );
        Program<Position> expected = program(
            l(),
            l(proc(id("foo"), l(), block(l(), Optional.empty())),
              proc(id("bar"), l(), block(l(), Optional.empty())),
              proc(id("baz"), l(), block(l(), Optional.empty())))
        );
        assertEquals(expected, parse(symbols));
    }

	//////////////////////////////////////////////////////////////////////////
	// Declarations
	/////////////////////////////////////////////////////////////////////////
    // x:int = x == x
    @Test
    public void declTest1() throws Exception {
        List<Symbol> symbols = Arrays.asList(
            sym(ID, "x"), sym(COLON), sym(INT),
            sym(EQ),
            sym(ID, "x"), sym(EQEQ), sym(ID, "x")
        );
        Stmt<Position> stmt = declAsgn(
                l(annotatedId(id("x"), Int.of(PositionKiller.dummyPosition))),
                binOp(BinOpCode.EQEQ, id("x"), id("x"))
        );

        stmtTestHelper(symbols, stmt);
    }

    // x:int, y:bool = 5
    @Test
    public void declTest2() throws Exception {
        List<Symbol> symbols = Arrays.asList(
            sym(ID, "x"), sym(COLON), sym(INT), sym(COMMA),
            sym(ID, "y"), sym(COLON), sym(BOOL), sym(EQ),
            sym(NUM, new Long(5L))
        );
        Stmt<Position> stmt = declAsgn(
            l(annotatedId(id("x"), num()),
              annotatedId(id("y"), bool())),
            numLiteral(new Long(5L))
        );

        stmtTestHelper(symbols, stmt);
    }

    // _:int[]
    @Test
    public void declTest3() throws Exception {
        List<Symbol> symbols = Arrays.asList(
            sym(UNDERSCORE), sym(COLON),
            sym(INT), sym(LBRACKET), sym(RBRACKET)
        );
        Stmt<Position> stmt = decl(
                l(annotatedUnderscore(underscore(), array(num(), Optional.empty())))
        );

        stmtTestHelper(symbols, stmt);
    }

    // x:int, y:bool, z:int, x:int, y:bool, z:int, x:int
    @Test
    public void declTest4() throws Exception {
        List<Symbol> symbols = Arrays.asList(
            sym(ID, "x"), sym(COLON), sym(INT), sym(COMMA),
            sym(ID, "y"), sym(COLON), sym(BOOL), sym(COMMA),
            sym(ID, "z"), sym(COLON), sym(INT), sym(COMMA),
            sym(ID, "x"), sym(COLON), sym(INT), sym(COMMA),
            sym(ID, "y"), sym(COLON), sym(BOOL), sym(COMMA),
            sym(ID, "z"), sym(COLON), sym(INT), sym(COMMA),
            sym(ID, "x"), sym(COLON), sym(INT)
        );
        Stmt<Position> stmt = decl(
            l(annotatedId(id("x"), num()),
              annotatedId(id("y"), bool()),
              annotatedId(id("z"), num()),
              annotatedId(id("x"), num()),
              annotatedId(id("y"), bool()),
              annotatedId(id("z"), num()),
              annotatedId(id("x"), num()))
        );

        stmtTestHelper(symbols, stmt);
    }

    // x:bool[]
    @Test
    public void declTest5() throws Exception {
        List<Symbol> symbols = Arrays.asList(
            sym(ID, "x"), sym(COLON),
            sym(BOOL), sym(LBRACKET), sym(RBRACKET)
        );
        Stmt<Position> stmt = decl(
            l(annotatedId(id("x"), array(bool(), Optional.empty())))
        );

        stmtTestHelper(symbols, stmt);
    }

    // _ = expr
    @Test
    public void declTest6() throws Exception {
        List<Symbol> symbols = Arrays.asList(
            sym(UNDERSCORE), sym(EQ), sym(NUM, new Long(5L))
        );
        Stmt<Position> stmt = declAsgn(
                l(underscore()),
                numLiteral(new Long(5L))
        );

        stmtTestHelper(symbols, stmt);
    }

    // _:bool = expr
    @Test
    public void declTest7() throws Exception {
        List<Symbol> symbols = Arrays.asList(
            sym(UNDERSCORE), sym(COLON), sym(BOOL),
            sym(EQ), sym(NUM, new Long(5L))
        );
        Stmt<Position> stmt = declAsgn(
            l(annotatedUnderscore(underscore(), bool())),
            numLiteral(new Long(5L))
        );

        stmtTestHelper(symbols, stmt);
    }

    // _:bool, y:bool = expr
    @Test
    public void declTest8() throws Exception {
        List<Symbol> symbols = Arrays.asList(
            sym(UNDERSCORE), sym(COLON), sym(BOOL), sym(COMMA),
            sym(ID, "y"), sym(COLON), sym(BOOL), sym(EQ),
            sym(NUM, new Long(5L))
        );
        Stmt<Position> stmt = declAsgn(
            l(annotatedUnderscore(underscore(), bool()),
              annotatedId(id("y"), bool())),
            numLiteral(new Long(5L))
        );

        stmtTestHelper(symbols, stmt);
    }

    // y:bool, _:bool = expr;
    @Test
    public void declTest9() throws Exception {
        List<Symbol> symbols = Arrays.asList(
            sym(ID, "y"), sym(COLON), sym(BOOL), sym(COMMA),
            sym(UNDERSCORE), sym(COLON), sym(BOOL), sym(EQ),
            sym(NUM, new Long(5L))
        );
        Stmt<Position> stmt = declAsgn(
            l(annotatedId(id("y"), bool()),
              annotatedUnderscore(underscore(), bool())),
            numLiteral(new Long(5L))
        );

        stmtTestHelper(symbols, stmt);
    }

    // x:bool, _ = expr;
    @Test
    public void declTest10() throws Exception {
        List<Symbol> symbols = Arrays.asList(
            sym(ID, "x"), sym(COLON), sym(BOOL), sym(COMMA),
            sym(UNDERSCORE), sym(EQ),
            sym(NUM, new Long(5L))
        );
        Stmt<Position> stmt = declAsgn(
            l(annotatedId(id("x"), bool()),
              underscore()),
            numLiteral(new Long(5L))
        );

        stmtTestHelper(symbols, stmt);
    }

    // _:int;
    @Test
    public void declTest11() throws Exception {
        List<Symbol> symbols = Arrays.asList(
            sym(UNDERSCORE), sym(COLON), sym(INT),
            sym(SEMICOLON)
        );
        Stmt<Position> stmt = decl(
                l(annotatedUnderscore(underscore(), num()))
        );

        stmtTestHelper(symbols, stmt);
    }

    // _:int, y:bool;
    @Test
    public void declTest12() throws Exception {
        List<Symbol> symbols = Arrays.asList(
            sym(UNDERSCORE), sym(COLON), sym(INT), sym(COMMA),
            sym(ID, "y"), sym(COLON), sym(BOOL),
            sym(SEMICOLON)
        );
        Stmt<Position> stmt = decl(
            l(annotatedUnderscore(underscore(), num()),
              annotatedId(id("y"), bool()))
        );

        stmtTestHelper(symbols, stmt);
    }

    // b:bool;
    @Test
    public void declTest13() throws Exception {
        List<Symbol> symbols = Arrays.asList(
            sym(ID, "b"), sym(COLON), sym(BOOL),
            sym(SEMICOLON)
        );
        Stmt<Position> stmt = decl(
            l(annotatedId(id("b"), bool()))
        );

        stmtTestHelper(symbols, stmt);
    }

    // x:int, _:int;
    @Test
    public void declTest14() throws Exception {
        List<Symbol> symbols = Arrays.asList(
            sym(ID, "x"), sym(COLON), sym(INT), sym(COMMA),
            sym(UNDERSCORE), sym(COLON), sym(INT)
        );
        Stmt<Position> stmt = decl(
            l(annotatedId(id("x"), num()),
              annotatedUnderscore(underscore(), num()))
        );

        stmtTestHelper(symbols, stmt);
    }

    // x:int, _;
    @Test
    public void declTest15() throws Exception {
        List<Symbol> symbols = Arrays.asList(
            sym(ID, "x"), sym(COLON), sym(INT), sym(COMMA),
            sym(UNDERSCORE), sym(SEMICOLON)
        );
        Stmt<Position> stmt = decl(
            l(annotatedId(id("x"), num()),
              underscore())
        );

        stmtTestHelper(symbols, stmt);
    }

    // _, _ = f()
    @Test
    public void declTest16() throws Exception {
        List<Symbol> symbols = Arrays.asList(
            sym(UNDERSCORE), sym(COMMA), sym(UNDERSCORE),
            sym(EQ), sym(ID, "f"), sym(LPAREN),
            sym(RPAREN)
        );
        Stmt<Position> stmt = declAsgn(
                l(underscore(),
                        underscore()),
                funcCall(id("f"), l())
        );

        stmtTestHelper(symbols, stmt);
    }

    // a:int[]
    @Test
    public void declTest17() throws Exception {
        List<Symbol> symbols = Arrays.asList(
            sym(ID, "a"), sym(COLON), sym(INT),
            sym(LBRACKET), sym(RBRACKET)
        );
        Stmt<Position> stmt = decl(
                l(annotatedId(id("a"), array(num(), Optional.empty())))
        );

        stmtTestHelper(symbols, stmt);
    }

    // a:int[][]
    @Test
    public void declTest18() throws Exception {
        List<Symbol> symbols = Arrays.asList(
            sym(ID, "a"), sym(COLON), sym(INT),
            sym(LBRACKET), sym(RBRACKET),
            sym(LBRACKET), sym(RBRACKET)
        );
        Stmt<Position> stmt = decl(
            l(annotatedId(id("a"), array(array(num(), Optional.empty()), Optional.empty())))
        );

        stmtTestHelper(symbols, stmt);
    }

    // a:int[n+5][f()];
    @Test
    public void declTest19() throws Exception {
        List<Symbol> symbols = Arrays.asList(
            sym(ID, "a"), sym(COLON), sym(INT),
            sym(LBRACKET), sym(ID, "n"), sym(PLUS),
            sym(NUM, new Long(5L)), sym(RBRACKET),
            sym(LBRACKET), sym(ID, "f"),
            sym(LPAREN), sym(RPAREN), sym(RBRACKET),
            sym(SEMICOLON)
        );
        Stmt<Position> stmt = decl(
            l(annotatedId(id("a"), array(
                array(
                    num(),
                    Optional.of(funcCall(id("f"), l()))),
                Optional.of(binOp(BinOpCode.PLUS, id("n"), numLiteral(new Long(5L)))))
            ))
        );

        stmtTestHelper(symbols, stmt);
    }

    // a:int[5][];
    @Test
    public void declTest20() throws Exception {
        List<Symbol> symbols = Arrays.asList(
            sym(ID, "a"), sym(COLON), sym(INT),
            sym(LBRACKET), sym(NUM, 5L),
            sym(RBRACKET),
            sym(LBRACKET), sym(RBRACKET),
            sym(SEMICOLON)
        );
        Stmt<Position> stmt = decl(
            l(annotatedId(id("a"), array(
                array(
                    num(),
                    Optional.empty()),
                Optional.of(numLiteral(5l))))
            )
        );

        stmtTestHelper(symbols, stmt);
    }

    // a:int[] = {1, 2, 3};
    @Test
    public void declTest21() throws Exception {
        List<Symbol> symbols = Arrays.asList(
            sym(ID, "a"), sym(COLON), sym(INT),
            sym(LBRACKET), sym(RBRACKET), sym(EQ),
            sym(LBRACE), sym(NUM, 1L), sym(COMMA),
            sym(NUM, 2L), sym(COMMA),
            sym(NUM, 3L), sym(COMMA),
            sym(RBRACE), sym(SEMICOLON)
        );
        Stmt<Position> stmt = declAsgn(
                l(annotatedId(id("a"), array(num(), Optional.empty()))),
                arrayLiteral(Arrays.asList(
                        numLiteral(new Long(1L)),
                        numLiteral(new Long(2L)),
                        numLiteral(new Long(3L))))
        );

        stmtTestHelper(symbols, stmt);
    }

    // a:int[][] = {{1, 2, 3}, {4}}
    @Test
    public void declTest22() throws Exception {
        List<Symbol> symbols = Arrays.asList(
            sym(ID, "a"), sym(COLON), sym(INT),
            sym(LBRACKET), sym(RBRACKET),
            sym(LBRACKET), sym(RBRACKET), sym(EQ),
            sym(LBRACE), sym(LBRACE),
            sym(NUM, new Long(1L)), sym(COMMA),
            sym(NUM, new Long(2L)), sym(COMMA),
            sym(NUM, new Long(3L)), sym(RBRACE),
            sym(COMMA), sym(LBRACE),
            sym(NUM, new Long(4L)),
            sym(RBRACE), sym(RBRACE)
        );
        Stmt<Position> stmt = declAsgn(
            l(annotatedId(id("a"), array(array(num(), Optional.empty()), Optional.empty()))),
            arrayLiteral(l(
                    arrayLiteral(l(
                            numLiteral(new Long(1L)),
                            numLiteral(new Long(2L)),
                            numLiteral(new Long(3L)))),
                    arrayLiteral(l(
                            numLiteral(new Long(4L))))))
        );

        stmtTestHelper(symbols, stmt);
    }

    // a:int[({1}[0])]
    @Test
    public void declTest23() throws Exception {
        List<Symbol> symbols = Arrays.asList(
            sym(ID, "a"), sym(COLON), sym(INT),
            sym(LBRACKET), sym(LPAREN),
            sym(LBRACE), sym(NUM, new Long(1L)),
            sym(RBRACE), sym(LBRACKET),
            sym(NUM, new Long(0L)), sym(RBRACKET),
            sym(RPAREN), sym(RBRACKET)
        );
        Stmt<Position> stmt = decl(
                l(annotatedId(id("a"), array(num(),
                        Optional.of(index(
                                arrayLiteral(l(numLiteral(new Long(1L)))),
                                numLiteral(new Long(0L)))))))
        );

        stmtTestHelper(symbols, stmt);
    }

    // _, _, _, _
    @Test
    public void declTest24() throws Exception {
        List<Symbol> symbols = Arrays.asList(
            sym(UNDERSCORE), sym(COMMA),
            sym(UNDERSCORE), sym(COMMA),
            sym(UNDERSCORE), sym(COMMA),
            sym(UNDERSCORE)
        );
        Stmt<Position> stmt = decl(
            l(underscore(), underscore(), underscore(), underscore())
        );

        stmtTestHelper(symbols, stmt);
    }

    // x:int[x]
    @Test
    public void declTest25() throws Exception {
        List<Symbol> symbols = Arrays.asList(
            sym(ID, "x"), sym(COLON),
            sym(INT), sym(LBRACKET), sym(ID, "x"),
            sym(RBRACKET)
        );
        Stmt<Position> stmt = decl(
            l(annotatedId(id("x"), array(num(), Optional.of(id("x")))))
        );

        stmtTestHelper(symbols, stmt);
    }

    // x:int[][10]
    @Test(expected=XicException.SyntaxException.class)
    public void declTest26() throws Exception {
        List<Symbol> symbols = Arrays.asList(
            sym(ID, "x"),
            sym(COLON),
            sym(INT),
            sym(LBRACKET),
            sym(RBRACKET),
            sym(LBRACKET),
            sym(ID, "x"),
            sym(RBRACKET)
        );
        stmtErrorTestHelper(symbols);
    }

    /* ASSIGNMENTS */
    // a = 5
    @Test
    public void asgnTest1() throws Exception {
        List<Symbol> symbols = Arrays.asList(
            sym(ID, "a"), sym(EQ), sym(NUM, 5l)
        );
        Stmt<Position> stmt = asgn(id("a"), numLiteral(5l));
        stmtTestHelper(symbols, stmt);
    }

    // a[5] = 5
    @Test
    public void asgnTest2() throws Exception {
        List<Symbol> symbols = Arrays.asList(
                sym(ID, "a"),
                sym(LBRACKET),
                sym(NUM, 5l),
                sym(RBRACKET),
                sym(EQ),
                sym(NUM, 5l)
        );
        Stmt<Position> stmt = asgn(
            index(id("a"), numLiteral(5l)),
            numLiteral(5l)
        );
        stmtTestHelper(symbols, stmt);
    }

    // a[5 binop 5] = 5
    @Test
    public void asgnTest3() throws Exception {
        BinOpCode[] binops = BinOpCode.values();
        for (int i = 0; i < binops.length; i++) {
            List<Symbol> symbols = Arrays.asList(
                    sym(ID, "a"),
                    sym(LBRACKET),
                    sym(NUM, 5l),
                    sym(binops[i].code),
                    sym(NUM, 5l),
                    sym(RBRACKET),
                    sym(EQ),
                    sym(NUM, 5l)
            );
            Stmt<Position> stmt = asgn(
                    index(
                        id("a"),
                        binOp(binops[i],
                                numLiteral(5l),
                                numLiteral(5l))
                    ),
                    numLiteral(5l)
            );
            stmtTestHelper(symbols, stmt);
        }
    }

    // a[f()] = 5
    @Test
    public void asgnTest4() throws Exception {
        List<Symbol> symbols = Arrays.asList(
                sym(ID, "a"),
                sym(LBRACKET),
                sym(ID, "f"),
                sym(LPAREN),
                sym(RPAREN),
                sym(RBRACKET),
                sym(EQ),
                sym(NUM, 5l)
        );
        Stmt<Position> stmt = asgn(
                index(
                    id("a"),
                    funcCall(id("f"), l())
                ),
                numLiteral(5l)
        );
        stmtTestHelper(symbols, stmt);
    }

    // "hello"[5] = 5
    @Test
    public void asgnTest5() throws Exception {
        List<Symbol> symbols = Arrays.asList(
                sym(STRING, "hello"),
                sym(LBRACKET),
                sym(NUM, 5l),
                sym(RBRACKET),
                sym(EQ),
                sym(NUM, 5l)
        );
        Stmt<Position> stmt = asgn(
                index(
                    stringLiteral("hello"),
                    numLiteral(5l)
                ),
                numLiteral(5l)
        );
        stmtTestHelper(symbols, stmt);
    }

    // a[b[5]+f()] = 5
    @Test
    public void asgnTest6() throws Exception {
        List<Symbol> symbols = Arrays.asList(
                sym(ID, "a"),
                sym(LBRACKET),
                sym(ID, "b"),
                sym(LBRACKET),
                sym(NUM, 5l),
                sym(RBRACKET),
                sym(PLUS),
                sym(ID, "f"),
                sym(LPAREN),
                sym(RPAREN),
                sym(RBRACKET),
                sym(EQ),
                sym(NUM, 5l)
        );
        Stmt<Position> stmt = asgn(
            index(
                id("a"),
                binOp(
                        BinOpCode.PLUS,
                        index(
                                id("b"),
                                numLiteral(5l)
                        ),
                        funcCall(id("f"), l())
                )
            ),
            numLiteral(5l)
        );
        stmtTestHelper(symbols, stmt);
    }

    // "["["["[0]] = 5
    @Test
    public void asgnTest7() throws Exception {
        List<Symbol> symbols = Arrays.asList(
                sym(STRING, "["),
                sym(LBRACKET),
                sym(STRING, "["),
                sym(LBRACKET),
                sym(NUM, 0l),
                sym(RBRACKET),
                sym(RBRACKET),
                sym(EQ),
                sym(NUM, 5l)
        );
        Stmt<Position> stmt = asgn(
            index(
                    stringLiteral("["),
                    index(
                            stringLiteral("["),
                            numLiteral(0l)
                    )
            ),
            numLiteral(5l)
        );
        stmtTestHelper(symbols, stmt);
    }

    // b[f(b[0])] = 5
    @Test
    public void asgnTest8() throws Exception {
        List<Symbol> symbols = Arrays.asList(
                sym(ID, "b"),
                sym(LBRACKET),
                sym(ID, "f"),
                sym(LPAREN),
                sym(ID, "b"),
                sym(LBRACKET),
                sym(NUM, 0l),
                sym(RBRACKET),
                sym(RPAREN),
                sym(RBRACKET),
                sym(EQ),
                sym(NUM, 5l)
        );
        Stmt<Position> stmt = asgn(
            index(
                id("b"),
                funcCall(
                    id("f"),
                    l(index(id("b"), numLiteral(0l)))
                )
            ),
            numLiteral(5l)
        );
        stmtTestHelper(symbols, stmt);
    }

	//////////////////////////////////////////////////////////////////////////
	// If and If-Else Statements
	/////////////////////////////////////////////////////////////////////////
    // if (b) { f() return } else { g() return };
    @Test
    public void ifTest1() throws Exception {
        List<Symbol> symbols = Arrays.asList(
            sym(IF), sym(LPAREN), sym(ID, "b"), sym(RPAREN),
            sym(LBRACE), sym(ID, "f"), sym(LPAREN), sym(RPAREN),
            sym(RETURN), sym(RBRACE),
            sym(ELSE), sym(LBRACE),
            sym(ID, "g"), sym(LPAREN), sym(RPAREN),
            sym(RETURN), sym(RBRACE), sym(SEMICOLON)
        );
        Stmt<Position> stmt = ifElse(
            id("b"),
            block(l(procCall(id("f"), l())), Optional.of(l())),
            block(l(procCall(id("g"), l())), Optional.of(l()))
        );

        stmtTestHelper(symbols, stmt);
    }

    // if (b1) if (b2) b = 5 else b = 5
    @Test
    public void ifTest2() throws Exception {
        List<Symbol> symbols = Arrays.asList(
            sym(IF),
            sym(LPAREN), sym(ID, "b1"), sym(RPAREN),
            sym(IF), sym(LPAREN), sym(ID, "b2"), sym(RPAREN),
            sym(ID, "b"), sym(EQ), sym(NUM, 5l),
            sym(ELSE),
            sym(ID, "b"), sym(EQ), sym(NUM, 5l)
        );
        Stmt<Position> stmt = if_(
            id("b1"),
            ifElse(
                id("b2"),
                asgn(id("b"), numLiteral(5l)),
                asgn(id("b"), numLiteral(5l))
            )
        );

        stmtTestHelper(symbols, stmt);
    }

    // if (b) { _; if (b) _ }
    @Test
    public void ifTest3() throws Exception {
        List<Symbol> symbols = Arrays.asList(
            sym(IF),
            sym(LPAREN), sym(ID, "b"), sym(RPAREN),
            sym(LBRACE),
            sym(UNDERSCORE), sym(SEMICOLON),
            sym(IF), sym(LPAREN), sym(ID, "b"), sym(RPAREN), sym(UNDERSCORE),
            sym(RBRACE)
        );
        Stmt<Position> stmt = if_(
            id("b"),
            block(l(
                decl(l(underscore())),
                if_(id("b"), decl(l(underscore())))
            ), Optional.empty())
        );

        stmtTestHelper(symbols, stmt);
    }

    // if (b) _; if (b) _
    @Test
    public void ifTest4() throws Exception {
        List<Symbol> symbols = Arrays.asList(
            sym(IF),
            sym(LPAREN), sym(ID, "b"), sym(RPAREN),
            sym(UNDERSCORE), sym(SEMICOLON),
            sym(IF),
            sym(LPAREN), sym(ID, "b"), sym(RPAREN),
            sym(UNDERSCORE)
        );
        List<Stmt<Position>> stmts = Arrays.asList(
            if_(
                id("b"),
                decl(l(underscore()))),
            if_(
                id("b"),
                decl(l(underscore())))
        );

        stmtsTestHelper(symbols, stmts);
    }

    // if (b)
    //  while (b)
    //   if (b) _
    //   else while(b) if (b) _
    @Test
    public void ifTest5() throws Exception {
        List<Symbol> symbols = Arrays.asList(
            sym(IF),
            sym(LPAREN), sym(ID, "b"), sym(RPAREN),
            sym(WHILE),
            sym(LPAREN), sym(ID, "b"), sym(RPAREN),
            sym(IF),
            sym(LPAREN), sym(ID, "b"), sym(RPAREN),
			sym(UNDERSCORE),
            sym(ELSE),
            sym(WHILE),
            sym(LPAREN), sym(ID, "b"), sym(RPAREN),
            sym(IF),
            sym(LPAREN), sym(ID, "b"), sym(RPAREN),
            sym(UNDERSCORE)
        );
        Stmt<Position> stmt = if_(
            id("b"),
            while_(
                id("b"),
                ifElse(
                    id("b"),
                    decl(l(underscore())),
                    while_(
                        id("b"),
                        if_(id("b"), decl(l(underscore()))))))
        );

        stmtTestHelper(symbols, stmt);
    }

    // if (b) b = 5 else b = 5
    @Test
    public void ifTest6() throws Exception {
        List<Symbol> symbols = Arrays.asList(
            sym(IF),
            sym(LPAREN), sym(ID, "b"), sym(RPAREN),
            sym(ID, "b"), sym(EQ), sym(NUM, 5l),
            sym(ELSE),
            sym(ID, "b"), sym(EQ), sym(NUM, 5l)
        );
        Stmt<Position> stmt = ifElse(
            id("b"),
            asgn(id("b"), numLiteral(5l)),
            asgn(id("b"), numLiteral(5l))
        );

        stmtTestHelper(symbols, stmt);
    }

    // if (b) {}
    @Test
    public void ifTest7() throws Exception {
        List<Symbol> symbols = Arrays.asList(
            sym(IF),
            sym(LPAREN), sym(ID, "b"), sym(RPAREN),
            sym(LBRACE), sym(RBRACE)
        );
        Stmt<Position> stmt = if_(
            id("b"),
            block(l(), Optional.empty())
        );

        stmtTestHelper(symbols, stmt);
    }

    // if (b) { if (b) f() } else { if (b) g() }
    @Test
    public void ifTest8() throws Exception {
        List<Symbol> symbols = Arrays.asList(
            sym(IF),
            sym(LPAREN), sym(ID, "b"), sym(RPAREN),
            sym(LBRACE),
            sym(IF), sym(LPAREN), sym(ID, "b"), sym(RPAREN),
            sym(ID, "f"), sym(LPAREN), sym(RPAREN),
            sym(RBRACE),
            sym(ELSE),
            sym(LBRACE),
            sym(IF), sym(LPAREN), sym(ID, "b"), sym(RPAREN),
            sym(ID, "g"), sym(LPAREN), sym(RPAREN),
            sym(RBRACE)
        );
        Stmt<Position> stmt = ifElse(
            id("b"),
            block(l(if_(id("b"), procCall(id("f"), l()))), Optional.empty()),
            block(l(if_(id("b"), procCall(id("g"), l()))), Optional.empty())
        );

        stmtTestHelper(symbols, stmt);
    }

	//////////////////////////////////////////////////////////////////////////
	// While Statements
	/////////////////////////////////////////////////////////////////////////
    @Test
    // while (true) _
    public void whileTest1() throws Exception {
        List<Symbol> symbols = Arrays.asList(
                sym(WHILE, "f"),
                sym(LPAREN),
                sym(TRUE),
                sym(RPAREN),
                sym(UNDERSCORE)
        );
        Stmt<Position> stmt = while_(true_(), decl(l(underscore())));
        stmtTestHelper(symbols, stmt);
    }

    @Test
    // while (true) {_}
    public void whileTest2() throws Exception {
        List<Symbol> symbols = Arrays.asList(
                sym(WHILE),
                sym(LPAREN),
                sym(FALSE),
                sym(RPAREN),
                sym(LBRACE),
                sym(UNDERSCORE),
                sym(RBRACE)
        );
        Stmt<Position> stmt =
            while_(false_(),
                   block(l(decl(l(underscore()))), Optional.empty()));
        stmtTestHelper(symbols, stmt);
    }

    @Test
    // while (1) {_; _; _}
    public void whileTest3() throws Exception {
        List<Symbol> symbols = Arrays.asList(
                sym(WHILE),
                sym(LPAREN),
                sym(NUM, 1l),
                sym(RPAREN),
                sym(LBRACE),
                sym(UNDERSCORE),
                sym(SEMICOLON),
                sym(UNDERSCORE),
                sym(SEMICOLON),
                sym(UNDERSCORE),
                sym(SEMICOLON),
                sym(RBRACE)
        );
        Stmt<Position> stmt =
            while_(
                numLiteral(1l),
                block(l(
                    decl(l(underscore())),
                    decl(l(underscore())),
                    decl(l(underscore()))),
                    Optional.empty()
                )
            );
        stmtTestHelper(symbols, stmt);
    }

    // while (b) {}
    @Test
    public void whileTest4() throws Exception {
        List<Symbol> symbols = Arrays.asList(
            sym(WHILE), sym(LPAREN), sym(ID, "b"), sym(RPAREN), sym(LBRACE), sym(RBRACE)
        );
        Stmt<Position> stmt = while_(
                id("b"),
                block(l(), Optional.empty())
        );

        stmtTestHelper(symbols, stmt);
    }

    // while (false) { {} {}; {} {_} }
    @Test
    public void whileTest5() throws Exception {
        List<Symbol> symbols = Arrays.asList(
            sym(WHILE),
            sym(LPAREN), sym(FALSE), sym(RPAREN),
            sym(LBRACE),
            sym(LBRACE), sym(RBRACE), sym(LBRACE), sym(RBRACE), sym(SEMICOLON),
            sym(LBRACE), sym(RBRACE), sym(LBRACE), sym(UNDERSCORE), sym(RBRACE),
            sym(RBRACE)
        );
        Stmt<Position> stmt = while_(
            false_(),
            block(
                l(
                    block(l(), Optional.empty()),
                    block(l(), Optional.empty()),
                    block(l(), Optional.empty()),
                    block(l(decl(l(underscore()))), Optional.empty())),
                Optional.empty())
        );

        stmtTestHelper(symbols, stmt);
    }

    // while (f(1, 2)) { x = f(3, 4) & true }
    @Test
    public void whileTest6() throws Exception {
        List<Symbol> symbols = Arrays.asList(
            sym(WHILE),
            sym(LPAREN), sym(ID, "f"),
            sym(LPAREN), sym(NUM, 1l), sym(COMMA), sym(NUM, 2l), sym(RPAREN), sym(RPAREN),
            sym(LBRACE), sym(ID, "x"), sym(EQ),
            sym(ID, "f"), sym(LPAREN), sym(NUM, 3l), sym(COMMA), sym(NUM, 4l), sym(RPAREN),
            sym(AMP), sym(TRUE),
            sym(RBRACE)
        );
        Stmt<Position> stmt = while_(
            funcCall(id("f"),
                 l(numLiteral(1l), numLiteral(2l))),
            block(l(asgn(id("x"),
                binOp(BinOpCode.AMP, funcCall(id("f"), l(numLiteral(3l), numLiteral(4l))), true_()))), Optional.empty())
        );

        stmtTestHelper(symbols, stmt);
    }


    private void binopHelper(BinOpCode s1) throws Exception {
        List<Symbol> symbols = Arrays.asList(
            sym(NUM, 1l),
            sym(s1.code),
            sym(NUM, 2l)
        );

        Expr<Position> e = binOp(s1, numLiteral(1l), numLiteral(2l));
        exprTestHelper(symbols, e);
    }

    @Test
    public void binopTest1() throws Exception {
        binopHelper(BinOpCode.PLUS);
    }
    @Test
    public void binopTest2() throws Exception {
        binopHelper(BinOpCode.STAR);
    }
    @Test
    public void binopTest3() throws Exception {
        binopHelper(BinOpCode.MINUS);
    }
    @Test
    public void binopTest4() throws Exception {
        binopHelper(BinOpCode.DIV);
    }
    @Test
    public void binopTest5() throws Exception {
        binopHelper(BinOpCode.MOD);
    }
    @Test
    public void binopTest6() throws Exception {
        binopHelper(BinOpCode.LT);
    }
    @Test
    public void binopTest7() throws Exception {
        binopHelper(BinOpCode.LTE);
    }
    @Test
    public void binopTest8() throws Exception {
        binopHelper(BinOpCode.GTE);
    }
    @Test
    public void binopTest9() throws Exception {
        binopHelper(BinOpCode.GT);
    }
    @Test
    public void binopTest10() throws Exception {
        binopHelper(BinOpCode.EQEQ);
    }
    @Test
    public void binopTest11() throws Exception {
        binopHelper(BinOpCode.NEQ);
    }
    @Test
    public void binopTest12() throws Exception {
        binopHelper(BinOpCode.AMP);
    }
    @Test
    public void binopTest13() throws Exception {
        binopHelper(BinOpCode.BAR);
    }
    @Test
    public void binopTest14() throws Exception {
        binopHelper(BinOpCode.HIGHMULT);
    }

    /**
     * Tests that {@code 1 op 2 op 3} == {@code (1 op 2) op 3}. You have to
     * pass in the same token twice; otherwise the parser complains about token
     * recycling.
     */
    private void assocHelper(BinOpCode c)
                 throws Exception {
        List<Symbol> symbols = Arrays.asList(
            sym(NUM, 1l),
            sym(c.code),
            sym(NUM, 2l),
            sym(c.code),
            sym(NUM, 3l)
        );

        Expr<Position> e =
            binOp(c, binOp(c, numLiteral(1l), numLiteral(2l)), numLiteral(3l));
        exprTestHelper(symbols, e);
    }

    @Test
    public void assocTest1() throws Exception {
        assocHelper(BinOpCode.PLUS);
    }
    @Test
    public void assocTest2() throws Exception {
        assocHelper(BinOpCode.STAR);
    }
    @Test
    public void assocTest3() throws Exception {
        assocHelper(BinOpCode.MINUS);
    }
    @Test
    public void assocTest4() throws Exception {
        assocHelper(BinOpCode.DIV);
    }
    @Test
    public void assocTest5() throws Exception {
        assocHelper(BinOpCode.MOD);
    }
    @Test
    public void assocTest6() throws Exception {
        assocHelper(BinOpCode.LT);
    }
    @Test
    public void assocTest7() throws Exception {
        assocHelper(BinOpCode.LTE);
    }
    @Test
    public void assocTest8() throws Exception {
        assocHelper(BinOpCode.GTE);
    }
    @Test
    public void assocTest9() throws Exception {
        assocHelper(BinOpCode.GT);
    }
    @Test
    public void assocTest10() throws Exception {
        assocHelper(BinOpCode.EQEQ);
    }
    @Test
    public void assocTest11() throws Exception {
        assocHelper(BinOpCode.NEQ);
    }
    @Test
    public void assocTest12() throws Exception {
        assocHelper(BinOpCode.AMP);
    }
    @Test
    public void assocTest13() throws Exception {
        assocHelper(BinOpCode.BAR);
    }
    @Test
    public void assocTest14() throws Exception {
        assocHelper(BinOpCode.HIGHMULT);
    }

    /**
     * Tests that {@code 1 op_1 2 op_2 3} == {@code 1 op_1 (2 op_2 3)} where
     * op_2 has higher precedence than op_1.
     */
    private void precHelper(BinOpCode c1, BinOpCode c2)
                 throws Exception {
        List<Symbol> symbols = Arrays.asList(
            sym(NUM, 1l),
            sym(c1.code),
            sym(NUM, 2l),
            sym(c2.code),
            sym(NUM, 3l)
        );

        Expr<Position> e =
            binOp(c1, numLiteral(1l), binOp(c2, numLiteral(2l), numLiteral(3l)));
        exprTestHelper(symbols, e);
    }

    @Test
    public void precTest1() throws Exception {
        precHelper(BinOpCode.BAR, BinOpCode.AMP);
        precHelper(BinOpCode.BAR, BinOpCode.EQEQ);
        precHelper(BinOpCode.BAR, BinOpCode.NEQ);
        precHelper(BinOpCode.BAR, BinOpCode.GT);
        precHelper(BinOpCode.BAR, BinOpCode.GTE);
        precHelper(BinOpCode.BAR, BinOpCode.LT);
        precHelper(BinOpCode.BAR, BinOpCode.LTE);
        precHelper(BinOpCode.BAR, BinOpCode.PLUS);
        precHelper(BinOpCode.BAR, BinOpCode.MINUS);
        precHelper(BinOpCode.BAR, BinOpCode.STAR);
        precHelper(BinOpCode.BAR, BinOpCode.DIV);
        precHelper(BinOpCode.BAR, BinOpCode.MOD);
        precHelper(BinOpCode.BAR, BinOpCode.HIGHMULT);
    }

    @Test
    public void precTest2() throws Exception {
        precHelper(BinOpCode.AMP, BinOpCode.EQEQ);
        precHelper(BinOpCode.AMP, BinOpCode.NEQ);
        precHelper(BinOpCode.AMP, BinOpCode.GT);
        precHelper(BinOpCode.AMP, BinOpCode.GTE);
        precHelper(BinOpCode.AMP, BinOpCode.LT);
        precHelper(BinOpCode.AMP, BinOpCode.LTE);
        precHelper(BinOpCode.AMP, BinOpCode.PLUS);
        precHelper(BinOpCode.AMP, BinOpCode.MINUS);
        precHelper(BinOpCode.AMP, BinOpCode.STAR);
        precHelper(BinOpCode.AMP, BinOpCode.DIV);
        precHelper(BinOpCode.AMP, BinOpCode.MOD);
        precHelper(BinOpCode.AMP, BinOpCode.HIGHMULT);
    }

    @Test
    public void precTest3() throws Exception {
        precHelper(BinOpCode.EQEQ, BinOpCode.GT);
        precHelper(BinOpCode.EQEQ, BinOpCode.GTE);
        precHelper(BinOpCode.EQEQ, BinOpCode.LT);
        precHelper(BinOpCode.EQEQ, BinOpCode.LTE);
        precHelper(BinOpCode.EQEQ, BinOpCode.PLUS);
        precHelper(BinOpCode.EQEQ, BinOpCode.MINUS);
        precHelper(BinOpCode.EQEQ, BinOpCode.STAR);
        precHelper(BinOpCode.EQEQ, BinOpCode.DIV);
        precHelper(BinOpCode.EQEQ, BinOpCode.MOD);
        precHelper(BinOpCode.EQEQ, BinOpCode.HIGHMULT);
    }

    @Test
    public void precTest4() throws Exception {
        precHelper(BinOpCode.NEQ, BinOpCode.GT);
        precHelper(BinOpCode.NEQ, BinOpCode.GTE);
        precHelper(BinOpCode.NEQ, BinOpCode.LT);
        precHelper(BinOpCode.NEQ, BinOpCode.LTE);
        precHelper(BinOpCode.NEQ, BinOpCode.PLUS);
        precHelper(BinOpCode.NEQ, BinOpCode.MINUS);
        precHelper(BinOpCode.NEQ, BinOpCode.STAR);
        precHelper(BinOpCode.NEQ, BinOpCode.DIV);
        precHelper(BinOpCode.NEQ, BinOpCode.MOD);
        precHelper(BinOpCode.NEQ, BinOpCode.HIGHMULT);
    }

    @Test
    public void precTest5() throws Exception {
        precHelper(BinOpCode.GT, BinOpCode.PLUS);
        precHelper(BinOpCode.GT, BinOpCode.MINUS);
        precHelper(BinOpCode.GT, BinOpCode.STAR);
        precHelper(BinOpCode.GT, BinOpCode.DIV);
        precHelper(BinOpCode.GT, BinOpCode.MOD);
        precHelper(BinOpCode.GT, BinOpCode.HIGHMULT);
    }

    @Test
    public void precTest6() throws Exception {
        precHelper(BinOpCode.GTE, BinOpCode.PLUS);
        precHelper(BinOpCode.GTE, BinOpCode.MINUS);
        precHelper(BinOpCode.GTE, BinOpCode.STAR);
        precHelper(BinOpCode.GTE, BinOpCode.DIV);
        precHelper(BinOpCode.GTE, BinOpCode.MOD);
        precHelper(BinOpCode.GTE, BinOpCode.HIGHMULT);
    }

    @Test
    public void precTest7() throws Exception {
        precHelper(BinOpCode.LT, BinOpCode.PLUS);
        precHelper(BinOpCode.LT, BinOpCode.MINUS);
        precHelper(BinOpCode.LT, BinOpCode.STAR);
        precHelper(BinOpCode.LT, BinOpCode.DIV);
        precHelper(BinOpCode.LT, BinOpCode.MOD);
        precHelper(BinOpCode.LT, BinOpCode.HIGHMULT);
    }

    @Test
    public void precTest8() throws Exception {
        precHelper(BinOpCode.LTE, BinOpCode.PLUS);
        precHelper(BinOpCode.LTE, BinOpCode.MINUS);
        precHelper(BinOpCode.LTE, BinOpCode.STAR);
        precHelper(BinOpCode.LTE, BinOpCode.DIV);
        precHelper(BinOpCode.LTE, BinOpCode.MOD);
        precHelper(BinOpCode.LTE, BinOpCode.HIGHMULT);
    }

    @Test
    public void precTest9() throws Exception {
        precHelper(BinOpCode.PLUS, BinOpCode.STAR);
        precHelper(BinOpCode.PLUS, BinOpCode.DIV);
        precHelper(BinOpCode.PLUS, BinOpCode.MOD);
        precHelper(BinOpCode.PLUS, BinOpCode.HIGHMULT);
    }

    @Test
    public void precTest10() throws Exception {
        precHelper(BinOpCode.MINUS, BinOpCode.STAR);
        precHelper(BinOpCode.MINUS, BinOpCode.DIV);
        precHelper(BinOpCode.MINUS, BinOpCode.MOD);
        precHelper(BinOpCode.MINUS, BinOpCode.HIGHMULT);
    }

    /**
     * Tests that {@code 1 op_high 2 op_low 3 op_high 4} == {@code (1 op_high 2)
     * op_3 (3 op_high 4)} where op_high has higher precedence than op_low.
     */
    private void precHelper(BinOpCode h1, BinOpCode l, BinOpCode h2)
                 throws Exception {
        List<Symbol> symbols = Arrays.asList(
            sym(NUM, 1l),
            sym(h1.code),
            sym(NUM, 2l),
            sym(l.code),
            sym(NUM, 3l),
            sym(h2.code),
            sym(NUM, 4l)
        );

        Expr<Position> e =
            binOp(l, binOp(h1, numLiteral(1l), numLiteral(2l)),
                     binOp(h2, numLiteral(3l), numLiteral(4l)));
        exprTestHelper(symbols, e);
    }

    @Test
    public void doublePrecTest1() throws Exception {
        BinOpCode[] codes = {
            BinOpCode.AMP,
            BinOpCode.EQEQ,
            BinOpCode.NEQ,
            BinOpCode.GT,
            BinOpCode.GTE,
            BinOpCode.LT,
            BinOpCode.LTE,
            BinOpCode.PLUS,
            BinOpCode.MINUS,
            BinOpCode.STAR,
            BinOpCode.DIV,
            BinOpCode.MOD,
            BinOpCode.HIGHMULT
        };
        for (BinOpCode c1 : codes) {
            for (BinOpCode c2 : codes) {
                precHelper(c1, BinOpCode.BAR, c2);
            }
        }
    }

    @Test
    public void doublePrecTest2() throws Exception {
        BinOpCode[] codes = {
            BinOpCode.EQEQ,
            BinOpCode.NEQ,
            BinOpCode.GT,
            BinOpCode.GTE,
            BinOpCode.LT,
            BinOpCode.LTE,
            BinOpCode.PLUS,
            BinOpCode.MINUS,
            BinOpCode.STAR,
            BinOpCode.DIV,
            BinOpCode.MOD,
            BinOpCode.HIGHMULT
        };

        for (BinOpCode c1 : codes) {
            for (BinOpCode c2 : codes) {
                precHelper(c1, BinOpCode.AMP, c2);
            }
        }
    }

    @Test
    public void doublePrecTest3() throws Exception {
        BinOpCode[] codes = {
            BinOpCode.GT,
            BinOpCode.GTE,
            BinOpCode.LT,
            BinOpCode.LTE,
            BinOpCode.PLUS,
            BinOpCode.MINUS,
            BinOpCode.STAR,
            BinOpCode.DIV,
            BinOpCode.MOD,
            BinOpCode.HIGHMULT
        };

        BinOpCode[] lows = {
            BinOpCode.EQEQ,
            BinOpCode.NEQ
        };

        for (BinOpCode l : lows) {
            for (BinOpCode c1 : codes) {
                for (BinOpCode c2 : codes) {
                    precHelper(c1, l, c2);
                }
            }
        }
    }

    @Test
    public void doublePrecTest4() throws Exception {
        BinOpCode[] codes = {
            BinOpCode.PLUS,
            BinOpCode.MINUS,
            BinOpCode.STAR,
            BinOpCode.DIV,
            BinOpCode.MOD,
            BinOpCode.HIGHMULT
        };

        BinOpCode[] lows = {
            BinOpCode.GT,
            BinOpCode.GTE,
            BinOpCode.LT,
            BinOpCode.LTE,
        };

        for (BinOpCode l : lows) {
            for (BinOpCode c1 : codes) {
                for (BinOpCode c2 : codes) {
                    precHelper(c1, l, c2);
                }
            }
        }
    }

    @Test
    public void doublePrecTest5() throws Exception {
        BinOpCode[] codes = {
            BinOpCode.STAR,
            BinOpCode.DIV,
            BinOpCode.MOD,
            BinOpCode.HIGHMULT
        };

        BinOpCode[] lows = {
            BinOpCode.PLUS,
            BinOpCode.MINUS,
        };

        for (BinOpCode l : lows) {
            for (BinOpCode c1 : codes) {
                for (BinOpCode c2 : codes) {
                    precHelper(c1, l, c2);
                }
            }
        }
    }

    /** Tests that {@code uop 1 bop 2} == {@code uop (1 bop 2)}. */
    private void unaryHelper(UnOpCode u, BinOpCode b) throws Exception {
        List<Symbol> symbols = Arrays.asList(
            sym(u.code),
            sym(NUM, 1l),
            sym(b.code),
            sym(NUM, 2l)
        );

        Expr<Position> e = binOp(b, unOp(u, numLiteral(1l)), numLiteral(2l));
        exprTestHelper(symbols, e);
    }

    @Test
    public void unopTest() throws Exception {
        for (UnOpCode u : UnOpCode.values()) {
            for (BinOpCode b : BinOpCode.values()) {
                unaryHelper(u, b);
            }
        }
    }

    private void hardUnaryHelper(UnOpCode u, BinOpCode b) throws Exception {
        List<Symbol> symbols;
        Expr<Position> e;

        symbols = Arrays.asList(
            sym(u.code),
            sym(u.code),
            sym(ID, "a"),
            sym(b.code),
            sym(u.code),
            sym(u.code),
            sym(ID, "b")
        );
        e = binOp(b, unOp(u, unOp(u, id("a"))),
                     unOp(u, unOp(u, id("b"))));
        exprTestHelper(symbols, e);

        symbols = Arrays.asList(
            sym(u.code),
            sym(u.code),
            sym(ID, "a"),
            sym(b.code),
            sym(u.code),
            sym(u.code),
            sym(ID, "b"),
            sym(b.code),
            sym(u.code),
            sym(u.code),
            sym(ID, "c")
        );
        e = binOp(b, binOp(b, unOp(u, unOp(u, id("a"))),
                              unOp(u, unOp(u, id("b")))),
                     unOp(u, unOp(u, id("c"))));
        exprTestHelper(symbols, e);

        symbols = Arrays.asList(
            sym(u.code),
            sym(ID, "a"),
            sym(LPAREN),
            sym(RPAREN)
        );
        e = unOp(u, funcCall(id("a"), l()));
        exprTestHelper(symbols, e);

        symbols = Arrays.asList(
            sym(u.code),
            sym(ID, "a"),
            sym(LBRACKET),
            sym(ID, "a"),
            sym(RBRACKET)
        );
        e = unOp(u, index(id("a"), id("a")));
        exprTestHelper(symbols, e);
    }

    @Test
    public void hardUnopTest() throws Exception {
        for (UnOpCode u : UnOpCode.values()) {
            for (BinOpCode b : BinOpCode.values()) {
                hardUnaryHelper(u, b);
            }
        }
    }

    /**
     * Tests that {@code a op b[1]} == {@code a op (b[1])}
     */
    private void indexPrecHelper(BinOpCode c) throws Exception {
        List<Symbol> symbols = Arrays.asList(
            sym(ID, "a"),
            sym(c.code),
            sym(ID, "b"),
            sym(LBRACKET),
            sym(NUM, 1l),
            sym(RBRACKET)
        );

        Expr<Position> e = binOp(c, id("a"), index(id("b"), numLiteral(1l)));
        exprTestHelper(symbols, e);
    }

    @Test
    public void indexPrecTest() throws Exception {
        for (BinOpCode b : BinOpCode.values()) {
            indexPrecHelper(b);
        }
    }

    /**
     * Tests that op {@code a op b()} == {@code a op (b())}
     */
    private void callPrecHelper(BinOpCode c) throws Exception {
        List<Symbol> symbols = Arrays.asList(
            sym(ID, "a"),
            sym(c.code),
            sym(ID, "b"),
            sym(LPAREN),
            sym(RPAREN)
        );

        Expr<Position> e = binOp(c, id("a"), funcCall(id("b"), l()));
        exprTestHelper(symbols, e);
    }

    @Test
    public void callPrecTest() throws Exception {
        for (BinOpCode b : BinOpCode.values()) {
            callPrecHelper(b);
        }
    }

	//////////////////////////////////////////////////////////////////////////
	// Testing Exceptions
	/////////////////////////////////////////////////////////////////////////

	// if (b) else _
	@Test(expected=Exception.class)
	public void ifElseErrorTest1() throws Exception {
		List<Symbol> symbols = Arrays.asList(
			sym(IF),
			sym(LPAREN), sym(ID, "b"), sym(RPAREN),
			sym(ELSE),
			sym(ID, "b"), sym(EQ), sym(NUM,5)
		);

		stmtErrorTestHelper(symbols);
	}

	// if b else _
	@Test(expected=Exception.class)
	public void ifElseErrorTest2() throws Exception {
		List<Symbol> symbols = Arrays.asList(
			sym(IF),
			sym(ID, "b"),
			sym(ELSE),
			sym(ID, "b"), sym(EQ), sym(NUM,5)
		);

		stmtErrorTestHelper(symbols);
	}

	// if (b else _
	@Test(expected=Exception.class)
	public void ifElseErrorTest3() throws Exception {
		List<Symbol> symbols = Arrays.asList(
			sym(IF),
			sym(LPAREN), sym(ID, "b"),
			sym(ELSE),
			sym(ID, "b"), sym(EQ), sym(NUM,5)
		);

		stmtErrorTestHelper(symbols);
	}

	// if b) else _
	@Test(expected=Exception.class)
	public void ifElseErrorTest4() throws Exception {
		List<Symbol> symbols = Arrays.asList(
			sym(IF),
			sym(ID, "b"), sym(RPAREN),
			sym(ELSE),
			sym(ID, "b"), sym(EQ), sym(NUM,5)
		);

		stmtErrorTestHelper(symbols);
	}

	// if (b) else
	@Test(expected=Exception.class)
	public void ifElseErrorTest5() throws Exception {
		List<Symbol> symbols = Arrays.asList(
			sym(IF),
			sym(LPAREN), sym(ID, "b"), sym(RPAREN),
			sym(ELSE)
		);

		stmtErrorTestHelper(symbols);
	}

	// if (b) _ else
	@Test(expected=Exception.class)
	public void ifElseErrorTest6() throws Exception {
		List<Symbol> symbols = Arrays.asList(
			sym(IF),
			sym(LPAREN), sym(ID, "b"), sym(RPAREN),
			sym(ID, "b"), sym(EQ), sym(NUM,5),
			sym(ELSE)
		);

		stmtErrorTestHelper(symbols);
	}

	// if b _ else
	@Test(expected=Exception.class)
	public void ifElseErrorTest7() throws Exception {
		List<Symbol> symbols = Arrays.asList(
			sym(IF),
			sym(ID, "b"),
			sym(ID, "b"), sym(EQ), sym(NUM,5),
			sym(ELSE)
		);

		stmtErrorTestHelper(symbols);
	}

	// if (b _ else
	@Test(expected=Exception.class)
	public void ifElseErrorTest8() throws Exception {
		List<Symbol> symbols = Arrays.asList(
			sym(IF),
			sym(LPAREN), sym(ID, "b"),
			sym(ID, "b"), sym(EQ), sym(NUM,5),
			sym(ELSE)
		);

		stmtErrorTestHelper(symbols);
	}

	// if b) _ else
	@Test(expected=Exception.class)
	public void ifElseErrorTest9() throws Exception {
		List<Symbol> symbols = Arrays.asList(
			sym(IF),
			sym(ID, "b"), sym(RPAREN),
			sym(ID, "b"), sym(EQ), sym(NUM,5),
			sym(ELSE)
		);

		stmtErrorTestHelper(symbols);
	}

	// if b _ else _
	@Test(expected=Exception.class)
	public void ifElseErrorTest10() throws Exception {
		List<Symbol> symbols = Arrays.asList(
			sym(IF),
			sym(ID, "b"),
			sym(ID, "b"), sym(EQ), sym(NUM,5),
			sym(ELSE),
			sym(ID, "b"), sym(EQ), sym(NUM,5)
		);

		stmtErrorTestHelper(symbols);
	}

	// if (b _ else _
	@Test(expected=Exception.class)
	public void ifElseErrorTest11() throws Exception {
		List<Symbol> symbols = Arrays.asList(
			sym(IF),
			sym(LPAREN), sym(ID, "b"),
			sym(ID, "b"), sym(EQ), sym(NUM,5),
			sym(ELSE),
			sym(ID, "b"), sym(EQ), sym(NUM,5)
		);

		stmtErrorTestHelper(symbols);
	}

	// if b) _ else _
	@Test(expected=Exception.class)
	public void ifElseErrorTest12() throws Exception {
		List<Symbol> symbols = Arrays.asList(
			sym(IF),
			sym(ID, "b"), sym(RPAREN),
			sym(ID, "b"), sym(EQ), sym(NUM,5),
			sym(ELSE),
			sym(ID, "b"), sym(EQ), sym(NUM,5)
		);

		stmtErrorTestHelper(symbols);
	}

    @Test
    public void bigNumTest() throws Exception {
        Expr<Position> e;
        List<Symbol> symbols;

        symbols = Arrays.asList(
            sym(MINUS),
            sym(BIG_NUM)
        );
        e = numLiteral(Long.MIN_VALUE);
        exprTestHelper(symbols, e);

        symbols = Arrays.asList(
            sym(MINUS),
            sym(MINUS),
            sym(BIG_NUM)
        );
        e = unOp(UnOpCode.UMINUS, numLiteral(Long.MIN_VALUE));
        exprTestHelper(symbols, e);

        symbols = Arrays.asList(
            sym(MINUS),
            sym(MINUS),
            sym(MINUS),
            sym(MINUS),
            sym(BIG_NUM)
        );
        e = unOp(UnOpCode.UMINUS,
                 unOp(UnOpCode.UMINUS,
                      unOp(UnOpCode.UMINUS, numLiteral(Long.MIN_VALUE))));
        exprTestHelper(symbols, e);

        symbols = Arrays.asList(
            sym(NUM, 1l),
            sym(MINUS),
            sym(MINUS),
            sym(BIG_NUM)
        );
        e = minus(numLiteral(1l), numLiteral(Long.MIN_VALUE));
        exprTestHelper(symbols, e);
    }

    @Test(expected=Exception.class)
    public void failingBigNumTest1() throws Exception {
        List<Symbol> symbols = Arrays.asList(
            sym(BIG_NUM)
        );
        Expr<Position> e = id("dummy");
        exprTestHelper(symbols, e);
    }

    @Test(expected=Exception.class)
    public void failingBigNumTest2() throws Exception {
        List<Symbol> symbols = Arrays.asList(
            sym(MINUS),
            sym(LPAREN),
            sym(BIG_NUM),
            sym(RPAREN)
        );
        Expr<Position> e = id("dummy");
        exprTestHelper(symbols, e);
    }

    @Test(expected=Exception.class)
    public void failingBigNumTest3() throws Exception {
        List<Symbol> symbols = Arrays.asList(
            sym(NUM, 1l),
            sym(PLUS),
            sym(BIG_NUM)
        );
        Expr<Position> e = id("dummy");
        exprTestHelper(symbols, e);
    }

    @Test
    public void callTest() throws Exception {
        int numTests = 25;
        int maxArgs = 10;
        Random rand = new Random();

        for (int i = 0; i < numTests; ++i) {
            Util.Tuple<List<List<Symbol>>, List<Expr<Position>>> ses
                = Util.unzip(Util.choose(exprs, rand.nextInt(maxArgs)));
            List<List<Symbol>> ss = ses.fst;
            List<Expr<Position>> es = ses.snd;

            List<Symbol> symbols = new ArrayList<Symbol>();
            symbols.add(sym(ID, "foo"));
            symbols.add(sym(LPAREN));
            for (int j = 0; j < ss.size(); ++j) {
                for (Symbol s : ss.get(j)) {
                    symbols.add(sym(s));
                }
                if (j + 1 != ss.size()) {
                    symbols.add(sym(COMMA));
                }
            }
            symbols.add(sym(RPAREN));

            exprTestHelper(symbols, funcCall(id("foo"), es));
        }
    }

    @Test
    public void indexTest() throws Exception {
        int numTests = 25;
        int maxArgs = 10;
        Random rand = new Random();

        for (int i = 0; i < numTests; ++i) {
            Util.Tuple<List<Symbol>, Expr<Position>> lhs = Util.choose(exprs);
            Expr<Position> e = lhs.snd;
            List<Util.Tuple<List<Symbol>, Expr<Position>>> ses
                = Util.choose(exprs, rand.nextInt(maxArgs) + 1);

            List<Symbol> symbols = new ArrayList<Symbol>();
            symbols.add(sym(LPAREN));
            for (Symbol s : lhs.fst) {
                symbols.add(sym(s));
            }
            symbols.add(sym(RPAREN));
            for (Util.Tuple<List<Symbol>, Expr<Position>> t : ses) {
                symbols.add(sym(LBRACKET));
                for (Symbol s : t.fst) {
                    symbols.add(sym(s));
                }
                symbols.add(sym(RBRACKET));
                e = index(e, t.snd);
            }

            exprTestHelper(symbols, e);
        }
    }

    @Test
    public void arrayLiteralTest() throws Exception {
        int numTests = 25;
        int maxArgs = 10;
        Random rand = new Random();

        for (int i = 0; i < numTests; ++i) {
            Util.Tuple<List<List<Symbol>>, List<Expr<Position>>> ses
                = Util.unzip(Util.choose(exprs, rand.nextInt(maxArgs)));
            List<List<Symbol>> ss = ses.fst;
            List<Expr<Position>> es = ses.snd;

            List<Symbol> symbols = new ArrayList<Symbol>();
            symbols.add(sym(LBRACE));
            for (int j = 0; j < ss.size(); ++j) {
                for (Symbol s : ss.get(j)) {
                    symbols.add(sym(s));
                }
                if (j + 1 != ss.size()) {
                    symbols.add(sym(COMMA));
                }
            }
            symbols.add(sym(RBRACE));

            exprTestHelper(symbols, arrayLiteral(es));
        }
    }

    @Test
    public void arrayLiteralTrailTest() throws Exception {
        int numTests = 25;
        int maxArgs = 10;
        Random rand = new Random();

        for (int i = 0; i < numTests; ++i) {
            Util.Tuple<List<List<Symbol>>, List<Expr<Position>>> ses
                = Util.unzip(Util.choose(exprs, rand.nextInt(maxArgs)));
            List<List<Symbol>> ss = ses.fst;
            List<Expr<Position>> es = ses.snd;

            List<Symbol> symbols = new ArrayList<Symbol>();
            symbols.add(sym(LBRACE));
            for (List<Symbol> syms : ss) {
                for (Symbol s : syms) {
                    symbols.add(sym(s));
                }
                symbols.add(sym(COMMA));
            }
            symbols.add(sym(RBRACE));

            exprTestHelper(symbols, arrayLiteral(es));
        }
    }

    @Test
    public void parenTest() throws Exception {
        int numTests = 25;

        for (int i = 0; i < numTests; ++i) {
            Util.Tuple<List<Symbol>, Expr<Position>> es =
                Util.choose(exprs);

            List<Symbol> symbols = new ArrayList<Symbol>();
            symbols.add(sym(LPAREN));
            for (Symbol s : es.fst) {
                symbols.add(sym(s));
            }
            symbols.add(sym(RPAREN));

            exprTestHelper(symbols, es.snd);
        }
    }

    @Test
    public void lengthTest() throws Exception {
        int numTests = 25;

        for (int i = 0; i < numTests; ++i) {
            Util.Tuple<List<Symbol>, Expr<Position>> es =
                Util.choose(exprs);

            List<Symbol> symbols = new ArrayList<Symbol>();
            symbols.add(sym(LENGTH));
            symbols.add(sym(LPAREN));
            for (Symbol s : es.fst) {
                symbols.add(sym(s));
            }
            symbols.add(sym(RPAREN));

            exprTestHelper(symbols, length(es.snd));
        }
    }

    // x x
    // x[]
    // _
    // _[1]
    // foo(x
    // foo x)
    // foo
    // 1()
    // length[x]
    // length()
    // length(1, 2)
    // (1, 2)
    // 1 2
    // 1 2 +
    // 1 -maxint
    // ;
    // ;;
    // 1;;
    // {_}
    // {{}
    // 1()
    // length[x]
    // length()
    // length(1, 2)
    // use
    // while
    // if
    // (1, 2)
    // 1 2
    // 1 2 +
    // 1 -maxint
    // ;
    // ;;
    // 1;;
    // {_}
    // {{}
    // {1,,}
    // {,1,}
    @Test
    public void invalidExprTest() throws Exception {
        List<List<Symbol>> ss = Arrays.asList(
            Arrays.asList(
                sym(ID, "x"),
                sym(ID, "x")
            ),
            Arrays.asList(
                sym(UNDERSCORE)
            ),
            Arrays.asList(
                sym(UNDERSCORE),
                sym(LBRACKET),
                sym(ID, "x"),
                sym(RBRACKET)
            ),
            Arrays.asList(
                sym(ID, "x"),
                sym(LBRACKET),
                sym(RBRACKET)
            ),
            Arrays.asList(
                sym(ID, "x"),
                sym(LBRACKET),
                sym(RBRACKET)
            ),
            Arrays.asList(
                sym(ID, "foo"),
                sym(LPAREN),
                sym(ID, "foo")
            ),
            Arrays.asList(
                sym(ID, "foo"),
                sym(LPAREN),
                sym(ID, "foo")
            ),
            Arrays.asList(
                sym(NUM, 1l),
                sym(LPAREN),
                sym(RPAREN)
            ),
            Arrays.asList(
                sym(LENGTH),
                sym(LPAREN),
                sym(RPAREN)
            ),
            Arrays.asList(
                sym(LENGTH),
                sym(LPAREN),
                sym(ID, "a"),
                sym(COMMA),
                sym(ID, "b"),
                sym(RPAREN)
            ),
            Arrays.asList(
                sym(USE)
            ),
            Arrays.asList(
                sym(WHILE)
            ),
            Arrays.asList(
                sym(IF)
            ),
            Arrays.asList(
                sym(LPAREN),
                sym(NUM, 1l),
                sym(COMMA),
                sym(NUM, 2l),
                sym(RPAREN)
            ),
            Arrays.asList(
                sym(NUM, 1l),
                sym(NUM, 2l)
            ),
            Arrays.asList(
                sym(NUM, 1l),
                sym(NUM, 2l),
                sym(PLUS, 2l)
            ),
            Arrays.asList(
                sym(NUM, 1l),
                sym(MINUS),
                sym(BIG_NUM)
            ),
            Arrays.asList(
                sym(SEMICOLON)
            ),
            Arrays.asList(
                sym(SEMICOLON),
                sym(SEMICOLON)
            ),
            Arrays.asList(
                sym(NUM, 1l),
                sym(SEMICOLON),
                sym(SEMICOLON)
            ),
            Arrays.asList(
                sym(LBRACE),
                sym(UNDERSCORE),
                sym(RBRACE)
            ),
            Arrays.asList(
                sym(LBRACE),
                sym(LBRACE),
                sym(RBRACE)
            ),
            Arrays.asList(
                sym(NUM, 1l),
                sym(COMMA),
                sym(COMMA)
            ),
            Arrays.asList(
                sym(COMMA),
                sym(NUM, 1l),
                sym(COMMA)
            )
        );
        for (List<Symbol> s : ss) {
            try {
                exprErrorTestHelper(s);
                fail("Should have failed.");
            } catch (XicException.SyntaxException e) {}
        }
    }

    @Test
    public void funcTest1() throws Exception {
        List<Symbol> declSymbols = Arrays.asList(
            sym(ID, "foo") ,
            sym(LPAREN),
            sym(RPAREN)
        );
        List<Symbol> typeSymbols = Arrays.asList(
            sym(INT)
        );
        Func<Position> f = func(
            id("foo"),
            l(),
            l(num()),
            block(l(), Optional.empty())
        );
        callableTestHelper(declSymbols, typeSymbols, f);
    }

    @Test
    public void funcTest2() throws Exception {
        List<Symbol> declSymbols = Arrays.asList(
            sym(ID, "foo") ,
            sym(LPAREN),
            sym(RPAREN)
        );
        List<Symbol> typeSymbols = Arrays.asList(
            sym(BOOL)
        );
        Func<Position> f = func(
            id("foo"),
            l(),
            l(bool()),
            block(l(), Optional.empty())
        );
        callableTestHelper(declSymbols, typeSymbols, f);
    }

    @Test
    public void funcTest3() throws Exception {
        List<Symbol> declSymbols = Arrays.asList(
            sym(ID, "foo") ,
            sym(LPAREN),
            sym(ID, "foo"),
            sym(COLON),
            sym(INT),
            sym(RPAREN)
        );
        List<Symbol> typeSymbols = Arrays.asList(
            sym(BOOL)
        );
        Func<Position> f = func(
            id("foo"),
            l(annotatedId(id("foo"), num())),
            l(bool()),
            block(l(), Optional.empty())
        );
        callableTestHelper(declSymbols, typeSymbols, f);
    }

    @Test
    public void funcTest4() throws Exception {
        List<Symbol> declSymbols = Arrays.asList(
            sym(ID, "foo") ,
            sym(LPAREN),
            sym(ID, "foo"),
            sym(COLON),
            sym(INT),
            sym(COMMA),
            sym(ID, "foo"),
            sym(COLON),
            sym(BOOL),
            sym(RPAREN)
        );
        List<Symbol> typeSymbols = Arrays.asList(
            sym(BOOL)
        );
        Func<Position> f = func(
            id("foo"),
            l(annotatedId(id("foo"), num()),
              annotatedId(id("foo"), bool())),
            l(bool()),
            block(l(), Optional.empty())
        );
        callableTestHelper(declSymbols, typeSymbols, f);
    }

    @Test
    public void funcTest5() throws Exception {
        List<Symbol> declSymbols = Arrays.asList(
            sym(ID, "foo") ,
            sym(LPAREN),
            sym(ID, "foo"), sym(COLON), sym(INT),
            sym(COMMA),
            sym(ID, "foo"), sym(COLON), sym(BOOL),
            sym(COMMA),
            sym(ID, "foo"), sym(COLON), sym(INT), sym(LBRACKET), sym(RBRACKET),
            sym(RPAREN)
        );
        List<Symbol> typeSymbols = Arrays.asList(
            sym(BOOL)
        );
        Func<Position> f = func(
            id("foo"),
            l(annotatedId(id("foo"), num()),
              annotatedId(id("foo"), bool()),
              annotatedId(id("foo"), array(num(), Optional.empty()))),
            l(bool()),
            block(l(), Optional.empty())
        );
        callableTestHelper(declSymbols, typeSymbols, f);
    }

    @Test
    public void funcTest6() throws Exception {
        List<Symbol> declSymbols = Arrays.asList(
            sym(ID, "foo"),
            sym(LPAREN),
            sym(ID, "foo"), sym(COLON), sym(INT),
            sym(COMMA),
            sym(ID, "foo"), sym(COLON), sym(BOOL),
            sym(COMMA),
            sym(ID, "foo"), sym(COLON), sym(INT), sym(LBRACKET), sym(RBRACKET),
            sym(COMMA),
            sym(ID, "foo"), sym(COLON), sym(INT), sym(LBRACKET), sym(RBRACKET),
                                                  sym(LBRACKET), sym(RBRACKET),
            sym(RPAREN)
        );
        List<Symbol> typeSymbols = Arrays.asList(
            sym(INT)
        );
        Func<Position> f = func(
            id("foo"),
            l(annotatedId(id("foo"), num()),
              annotatedId(id("foo"), bool()),
              annotatedId(id("foo"), array(num(), Optional.empty())),
              annotatedId(id("foo"),
                  array(array(num(), Optional.empty()), Optional.empty()))
              ),
            l(num()),
            block(l(), Optional.empty())
        );
        callableTestHelper(declSymbols, typeSymbols, f);
    }

    @Test
    public void funcTest7() throws Exception {
        List<Symbol> declSymbols = Arrays.asList(
            sym(ID, "foo"),
            sym(LPAREN),
            sym(RPAREN)
        );
        List<Symbol> typeSymbols = Arrays.asList(
            sym(INT),
            sym(COMMA),
            sym(INT)
        );
        Func<Position> f = func(
            id("foo"),
            l(),
            l(num(), num()),
            block(l(), Optional.empty())
        );
        callableTestHelper(declSymbols, typeSymbols, f);
    }

    @Test
    public void funcTest8() throws Exception {
        List<Symbol> declSymbols = Arrays.asList(
            sym(ID, "foo"),
            sym(LPAREN),
            sym(RPAREN)
        );
        List<Symbol> typeSymbols = Arrays.asList(
            sym(BOOL),
            sym(COMMA),
            sym(BOOL)
        );
        Func<Position> f = func(
            id("foo"),
            l(),
            l(bool(), bool()),
            block(l(), Optional.empty())
        );
        callableTestHelper(declSymbols, typeSymbols, f);
    }

    @Test
    public void funcTest9() throws Exception {
        List<Symbol> declSymbols = Arrays.asList(
            sym(ID, "foo"),
            sym(LPAREN),
            sym(RPAREN)
        );
        List<Symbol> typeSymbols = Arrays.asList(
            sym(INT),
            sym(COMMA),
            sym(BOOL),
            sym(COMMA),
            sym(INT),
            sym(LBRACKET),
            sym(RBRACKET)
        );
        Func<Position> f = func(
            id("foo"),
            l(),
            l(num(), bool(), array(num(), Optional.empty())),
            block(l(), Optional.empty())
        );
        callableTestHelper(declSymbols, typeSymbols, f);
    }

    @Test
    public void funcTest10() throws Exception {
        List<Symbol> declSymbols = Arrays.asList(
            sym(ID, "foo"),
            sym(LPAREN),
            sym(RPAREN)
        );
        List<Symbol> typeSymbols = Arrays.asList(
            sym(BOOL),
            sym(COMMA),
            sym(INT),
            sym(LBRACKET),
            sym(RBRACKET),
            sym(COMMA),
            sym(INT),
            sym(LBRACKET),
            sym(RBRACKET),
            sym(LBRACKET),
            sym(RBRACKET),
            sym(COMMA),
            sym(INT)
        );
        Func<Position> f = func(
            id("foo"),
            l(),
            l(bool(),
              array(num(), Optional.empty()),
              array(array(num(), Optional.empty()), Optional.empty()),
              num()),
            block(l(), Optional.empty())
        );
        callableTestHelper(declSymbols, typeSymbols, f);
    }

    // foo():{}
    // foo(x):int{}
    // foo(_):int{}
    // foo(_:int):int{}
    // foo(,x:int):int{}
    // foo(x:int,):int{}
    @Test
    public void funcTest11() throws Exception {
        List<List<Symbol>> ss = Arrays.asList(
            Arrays.asList(
                sym(ID, "foo"),
                sym(LPAREN),
                sym(RPAREN),
                sym(COLON),
                sym(LBRACE),
                sym(RBRACE)
            ),
            Arrays.asList(
                sym(ID, "foo"),
                sym(LPAREN),
                sym(ID, "a"),
                sym(RPAREN),
                sym(COLON),
                sym(INT),
                sym(LBRACE),
                sym(RBRACE)
            ),
            Arrays.asList(
                sym(ID, "foo"),
                sym(LPAREN),
                sym(COMMA),
                sym(ID, "x"),
                sym(COLON),
                sym(INT),
                sym(RPAREN),
                sym(COLON),
                sym(INT),
                sym(LBRACE),
                sym(RBRACE)
            ),
            Arrays.asList(
                sym(ID, "foo"),
                sym(LPAREN),
                sym(ID, "x"),
                sym(COLON),
                sym(INT),
                sym(COMMA),
                sym(RPAREN),
                sym(COLON),
                sym(INT),
                sym(LBRACE),
                sym(RBRACE)
            )
        );
        for (List<Symbol> s : ss) {
            try {
                Program<Position> prog = parse(s);
                System.out.println(prog);
                fail("Should have failed.");
            } catch (XicException.SyntaxException e) {}
        }
    }
}
