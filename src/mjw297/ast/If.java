package mjw297.ast;

public final class If implements Stmt {
    public final Expr b;
    public final Stmt body;
    public If(Expr b, Stmt body) {
        this.b = b;
        this.body = body;
    }
    public <R> R accept(StmtVisitor<R> v) {
        return v.visit(this);
    }
}
