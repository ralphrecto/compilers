package mjw297;

import java_cup.runtime.Symbol;

import javax.swing.text.html.Option;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static mjw297.XicException.*;

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
    public static Lexed lex(Reader r) {
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
        } catch (IOException e) {
            XicException.XiIOException e2 = new XicException.XiIOException(e.getMessage());
            return new Lexed(result, Optional.of(e2));
        }
    }

    public static class Parsed {
        public final Optional<Ast.Program<Position>> prog;
        public final Optional<Ast.Interface<Position>> inter;
        public final Optional<XicException> exception;

        Parsed(Ast.Program<Position> p) {
            this.prog = Optional.of(p);
            this.inter = Optional.empty();
            this.exception = Optional.empty();
        }

        Parsed(Ast.Interface<Position> i) {
            this.prog = Optional.empty();
            this.inter = Optional.of(i);
            this.exception = Optional.empty();
        }

        Parsed(XicException e) {
            this.prog = Optional.empty();
            this.inter = Optional.empty();
            this.exception = Optional.of(e);
        }

    }

    public static Parsed parse(Reader r) {
        Parser parser = new Parser(new Lexer(r));
        try {
            @SuppressWarnings("unchecked")
            Ast.Program<Position> prog = (Ast.Program<Position>) parser.parse().value;
            return new Parsed(prog);
        } catch (Exception e) {
            if (e instanceof XicException) {
                return new Parsed((XicException) e);
            } else {
                return new Parsed(new GenericParserException(-1, -1, e.getMessage()));
            }
        }
    }

    public static Parsed parseInterface(Reader r) {
        InterfaceParser parser = new InterfaceParser(new Lexer(r));
        try {
            @SuppressWarnings("unchecked")
            Ast.Interface<Position> inter = (Ast.Interface<Position>) parser.parse().value;
            return new Parsed(inter);
        } catch (Exception e) {
            if (e instanceof XicException) {
                return new Parsed((XicException) e);
            } else {
                return new Parsed(new GenericParserException(-1, -1, e.getMessage()));
            }
        }
    }
}

