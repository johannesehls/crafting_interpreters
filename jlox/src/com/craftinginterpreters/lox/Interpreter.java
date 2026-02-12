package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Interpreter implements Expr.Visitor<Object>, Stmt.Visitor<Void> {
    // Exception for 'break' control flow statements in loops.
    private static class BreakException extends RuntimeException {
        final Token token;

        BreakException(Token token) {
            super();
            this.token = token;
        }
    };

    static record Config(boolean printExpr){};
    private static final Config defaultConf = new Config(false);
    private Config config = defaultConf;

    final Environment globals = new Environment();
    private Environment environment = globals;
    private final Map<Expr, Integer> locals = new HashMap<>();

    Interpreter() {
        // Native functions.
        globals.define("clock", new LoxCallable() {
            @Override
            public int arity() { return 0; }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                return (double)System.currentTimeMillis() / 1000.0;
            }

            @Override
            public String toString() { return "<native fn>"; }
        });
    }

    // Interpreters public API method.
    void interpret(List<Stmt> statements, Config config) {
        this.config = (config != null) ? config : defaultConf;      // Set config.
        try {
            for (Stmt statement : statements) {
                try {
                    execute(statement);
                } catch (BreakException b) {
                    throw new RuntimeError(b.token, "Usage of keyword 'break' outside of loop context.");
                }
            }
        } catch (RuntimeError error) {
            Lox.runtimeError(error);
        }
    }

    // --------------------------- Visitor Pattern Methods ---------------------------

    // Statements:

    // Variable statement.
    @Override
    public Void visitVarStmt(Stmt.Var stmt) {
        Object value = null;
        if (stmt.initializer != null) {
            value = evaluate(stmt.initializer);
            environment.setInitialized(stmt.name.lexeme);
        }

        environment.define(stmt.name.lexeme, value);
        return null;
    }

    // Expression statement.
    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt) {
        Object value = evaluate(stmt.expression);
        if (config.printExpr) System.out.println(stringify(value)); // Print if in REPL.
        // Following line is required due to the special "Void" return type.
        return null;
    }

    // If statement.
    @Override
    public Void visitIfStmt(Stmt.If stmt) {
        if (isTruthy(evaluate(stmt.condition))) {
            execute(stmt.thenBranch);
        } else if (stmt.elseBranch != null) {
            execute(stmt.elseBranch);
        }
        return null;
    }

    // Function statement.
    @Override
    public Void visitFunctionStmt(Stmt.Function stmt) {
        LoxFunction function = new LoxFunction(stmt, environment);
        environment.define(stmt.name.lexeme, function);
        return null;
    }

    // Print statement.
    @Override
    public Void visitPrintStmt(Stmt.Print stmt) {
        Object value = evaluate(stmt.expression);
        System.out.println(stringify(value));
        // Following line is required due to the special "Void" return type.
        return null;
    }

    // Return statement.
    @Override
    public Void visitReturnStmt(Stmt.Return stmt) {
        Object value = null;
        if (stmt.value != null) value = evaluate(stmt.value);

        throw new Return(value);
    }

    // While statement.
    @Override
    public Void visitWhileStmt(Stmt.While stmt) {
        while (isTruthy(evaluate(stmt.condition))) {
            try {
                execute(stmt.body);
            } catch (BreakException b) {
                break;
            }
        }
        return null;
    }

    // Break statement.
    @Override
    public Void visitBreakStmt(Stmt.Break stmt) {
        throw new BreakException(stmt.token);
    }

    // Block statement.
    @Override
    public Void visitBlockStmt(Stmt.Block stmt) {
        executeBlock(stmt.statements, new Environment(environment));
        return null;
    }

    // Expressions:

    // Literal expression.
    @Override
    public Object visitLiteralExpr(Expr.Literal expr) {
        return expr.value;
    }

    // Unary expression.
    @Override
    public Object visitUnaryExpr(Expr.Unary expr) {
        Object right = evaluate(expr.right);

        switch (expr.operator.type) {
            case BANG:
                return !isTruthy(right);
            case MINUS:
                checkNumberOperand(expr.operator, right);
                return -(double) right;
        }

        // Unreachable.
        return null;
    }

    // Call expression.
    @Override
    public Object visitCallExpr(Expr.Call expr) {
        Object callee = evaluate(expr.callee);

        List<Object> arguments = new ArrayList<>();
        for (Expr argument : expr.arguments) {
            arguments.add(evaluate(argument));
        }

        if (!(callee instanceof LoxCallable)) {
            throw new RuntimeError(expr.paren, "Can only call functions and classes.");
        }
        LoxCallable function = (LoxCallable)callee;

        if (arguments.size() != function.arity()) {
            throw new RuntimeError(expr.paren, "Expected " +
                    function.arity() + " arguments but got " +
                    arguments.size() + ".");
        }

        return function.call(this, arguments);
    }

    // Binary expression.
    @Override
    public Object visitBinaryExpr(Expr.Binary expr) {
        // Evaluation of operands in left-to-right order (caution if operands have side effects).
        Object left = evaluate(expr.left);
        Object right = evaluate(expr.right);

        switch (expr.operator.type) {
            case BANG_EQUAL:
                return !isEqual(left, right);
            case EQUAL_EQUAL:
                return isEqual(left, right);
            case GREATER:
                checkNumberOperands(expr.operator, left, right);
                return (double)left > (double)right;
            case GREATER_EQUAL:
                checkNumberOperands(expr.operator, left, right);
                return (double)left >= (double)right;
            case LESS:
                checkNumberOperands(expr.operator, left, right);
                return (double)left < (double)right;
            case LESS_EQUAL:
                checkNumberOperands(expr.operator, left, right);
                return (double)left <= (double)right;
            case MINUS:
                checkNumberOperands(expr.operator, left, right);
                return (double)left - (double)right;
            case PLUS:
                // String concatenation if at least one operand is of type string.
                if (left instanceof String || right instanceof String) {
                    return stringify(left) + stringify(right);
                }

                if (left instanceof Double && right instanceof Double) {
                    return (double)left + (double)right;
                }

                // Error if the Operands are of the wrong type.
                throw new RuntimeError(expr.operator, "Operands must be two numbers or two strings.");
            case SLASH:
                checkNumberOperands(expr.operator, left, right);
                if ((double)right != 0.0) {
                    return (double)left / (double)right;
                }
                throw new RuntimeError(expr.operator, "Division by zero error. (Value: "
                        + stringify((double)left / (double)right) + ")");
            case STAR:
                checkNumberOperands(expr.operator, left, right);
                return (double)left * (double)right;
        }

        // Unreachable.
        return null;
    }

    // Ternary expression.
    @Override
    public Object visitTernaryExpr(Expr.Ternary expr) {
        if (isTruthy(evaluate(expr.expr))) {
            return evaluate(expr.thenBranch);
        } else {
            return evaluate(expr.elseBranch);
        }
    }

    // Logical expression.
    @Override
    public Object visitLogicalExpr(Expr.Logical expr) {
        Object left = evaluate(expr.left);

        if (expr.operator.type == TokenType.OR) {
            // OR: short-circuit to left if already true
            if (isTruthy(left)) return left;
        } else {
            // AND: short-circuit to left if already false
            if (!isTruthy(left)) return left;
        }

        // Other cases.
        return evaluate(expr.right);
    }

    // Variable expression.
    @Override
    public Object visitVariableExpr(Expr.Variable expr) {
        return lookUpVariable(expr.name, expr);
    }

    private Object lookUpVariable(Token name, Expr expr) {
        Integer distance = locals.get(expr);
        if (distance != null) {
            return environment.getAt(distance, name.lexeme);
        } else {
            return globals.get(name);
        }
    }

    // Assignment expression.
    @Override
    public Object visitAssignExpr(Expr.Assign expr) {
        Object value = evaluate(expr.value);

        Integer distance = locals.get(expr);
        if (distance != null) {
            environment.assignAt(distance, expr.name, value);
        } else {
            environment.assign(expr.name, value);
        }

        return value;
    }

    // Lambda expression.
    @Override
    public Object visitLambdaExpr(Expr.Lambda expr) {
        return new LoxFunction((Stmt.Function)expr.function, environment);
    }

    // --------------------------- Private Helper Methods ---------------------------

    // Method for executing a block.
    void executeBlock(List<Stmt> statements, Environment environment) {
        Environment previous = this.environment;    // save current env.
        try {
            this.environment = environment;         // set current to new env.

            for (Stmt statement : statements) {
                execute(statement);
            }
        } finally {                                 // Executes even if exception thrown.
            this.environment = previous;            // restore to previous env.
        }
    }

    // Method for dynamic type-checking unary expressions.
    private void checkNumberOperand(Token operator, Object operand) {
        if (operand instanceof Double) {
            return;
        }
        throw new RuntimeError(operator, "Operand must be a number.");
    }

    // Method for dynamic type-checking binary expressions.
    private void checkNumberOperands(Token operator, Object left, Object right) {
        if (left instanceof Double && right instanceof Double) {
            return;
        }
        throw new RuntimeError(operator, "Operands must be a numbers.");
    }

    // Just "false" and "nil" are falsey, everything else is truthy (like in Ruby).
    private boolean isTruthy(Object object) {
        if (object == null) {
            return false;
        }
        if (object instanceof Boolean) {
            return (boolean) object;
        }
        return true;
    }

    // Handle cases where first object is null, then use Java's ".equals()" method.
    private boolean isEqual(Object a, Object b) {
        if (a == null && b == null) {
            return true;
        }
        if (a == null) {
            return false;
        }
        return a.equals(b);
    }

    private String stringify(Object object) {
        if (object == null) {
            return "nil";
        }
        if (object instanceof Double) {
            String text = object.toString();
            if (text.endsWith(".0")) {
                text = text.substring(0, text.length() - 2);
            }
            return text;
        }

        return object.toString();
    }

    // Grouping Expression.
    @Override
    public Object visitGroupingExpr(Expr.Grouping expr) {
        return evaluate(expr.expression);
    }

    private Object evaluate(Expr expr) {
        return expr.accept(this);
    }

    private void execute(Stmt stmt) {
        stmt.accept(this);
    }

    void resolve(Expr expr, int depth) {
        locals.put(expr, depth);
    }
}
