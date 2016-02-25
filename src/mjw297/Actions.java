package mjw297;

import java_cup.runtime.Symbol;

import javax.swing.text.html.Option;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@SuppressWarnings("deprecation")
public class Actions {
    /**
     * A {@code Lexed} instance represents the result of lexing a stream of
     * characters. Lexing results in a list of symbols and optionally an
     * exception.
     */
    static class Lexed {
        public final List<Symbol> symbols;
        public final Optional<XicException> exception;

        public Lexed(List<Symbol> symbols, Optional<XicException> exception) {
            this.symbols = symbols;
            this.exception = exception;
        }
    }

    /**
     * {@code lex(s)} uses the {@code Lexer} class to repeatedly lex tokens
     * from {@code s} until the EOF token is reached. The EOF token <i>is</i>
     * included in the returned list.
     */
    public static Lexed lex(Reader r) throws IOException {
        List<Symbol> result = new ArrayList<>();
        Lexer l = new Lexer(r);

        try {
            Symbol sym = l.next_token();
            while (sym.sym != Sym.EOF) {
                result.add(sym);
                sym = l.next_token();
            }
            result.add(sym);
            return new Lexed(result, Optional.empty());
        } catch (XicException e) {
            return new Lexed(result, Optional.of(e));
        }
    }

    public static class Parsed {
        public final Optional<Ast.Program<Position>> prog;
        public final Optional<Exception> exception;

        Parsed(Ast.Program<Position> p) {
            this.prog = Optional.of(p);
            this.exception = Optional.empty();
        }

        Parsed(Exception e) {
            this.prog = Optional.empty();
            this.exception = Optional.of(e);
        }

    }

    public static Parsed parse(Reader r) {
        Parser parser = new Parser(new Lexer(r));
        try {
            @SuppressWarnings("unchecked")
            Ast.Program<Position> p = (Ast.Program<Position>) parser.parse().value;
            return new Parsed(p);
        } catch (Exception e) {
            return new Parsed(e);
        }
    }
}
