package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.List;

import static com.craftinginterpreters.lox.TokenType.*;

/*
 * program       → declaration* EOF ;
 *
 * declaration   → varDecl | statement ;
 *
 * varDecl       → "var" IDENTIFIER ( "=" expression )? ";" ;
 *
 * statement     → exprStmt | printStmt ;
 *
 * exprStmt      → expression ";" ;
 * printStmt     → "print" expression ";" ;
 *
 * expression    → comma ;
 * comma         → assignment ( "," assignment)* ;
 * assignment    → IDENTIFIER "=" assignment
 *               | ternary ;
 * ternary       → equality ( "?" expression ":" ternary )?
 * equality      → comparison ( ( "!=" | "==" ) comparison )* ;
 * comparison    → term ( ( ">" | ">=" | "<" | "<=" ) term )* ;
 * term          → factor ( ( "-" | "+" ) factor )* ;
 * factor        → unary ( ( "/" | "*" ) unary )* ;
 * unary         → ("!"|"-")unary | primary ;
 * primary       → NUMBER | STRING | "true" | "false" | "nil"
 *                 | "(" expression ")" | IDENTIFIER
 *                 // Error productions...
 *                 | ( "!=" | "==" ) equality
 *                 | ( ">" | ">=" | "<" | "<=" ) comparison
 *                 | ( "+" ) term
 *                 | ( "/" | "*" ) factor ;
 *
 * */


// Interface needed for referencing functions to simplify some code.
interface BinaryOperand {
    Expr eval();
}

class Parser {
    private static class ParseError extends RuntimeException {
    }

    private final List<Token> tokens;
    private int current = 0;

    Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    // Method that kicks off parsing an expression.
    List<Stmt> parse() {
        List<Stmt> statements = new ArrayList<>();
        while (!isAtEnd()) {
            statements.add(declaration());
        }

        return statements;
    }

    private Stmt declaration() {
        try {
            if (match(VAR)) return varDeclaration();
            return statement();
        } catch (ParseError error) {
            synchronize();
            return null;
        }
    }

    private Stmt varDeclaration() {
        Token name = consume(IDENTIFIER, "Expect variable name.");

        Expr initializer = null;
        if (match(EQUAL)) {
            initializer = expression();
        }

        consume(SEMICOLON, "Expect ';' after variable declaration.");
        return new Stmt.Var(name, initializer);
    }

    private Stmt statement() {
        if (match(PRINT)) {
            return printStatement();
        }
        return expressionStatement();
    }

    private Stmt expressionStatement() {
        Expr expr = expression();
        consume(SEMICOLON, "Expect ';' after value.");
        return new Stmt.Expression(expr);
    }

    private Stmt printStatement() {
        Expr value = expression();
        consume(SEMICOLON, "Expect ';' after value.");
        return new Stmt.Print(value);
    }

    private Expr expression() {
        return comma();
    }

    private Expr comma() {
        // Try to handle with "binaryHelper" method
        return binaryHelper(this::assignment, COMMA);
    }

    private Expr assignment() {
        Expr expr = ternary();

        if (match(EQUAL)) {
            Token equals = previous();
            Expr value = ternary();

            if (expr instanceof Expr.Variable) {
                Token name = ((Expr.Variable)expr).name;
                return new Expr.Assign(name, value);
            }

            error(equals, "Invalid assignment target.");
        }

        return expr;
    }

    private Expr ternary() {
        Expr expr = equality();

        if (match(QUESTION)) {
            Expr thenBranch = expression();
            consume(COLON, "Expect ':' after then branch of conditional expression.");
            Expr elseBranch = ternary();
            expr = new Expr.Ternary(expr, thenBranch, elseBranch);
        }

        return expr;
    }

    private Expr equality() {
        // Try to handle with "binaryHelper" method
        return binaryHelper(this::comparison, BANG_EQUAL, EQUAL_EQUAL);

        /*
        Expr expr = comparison();

        while (match(BANG_EQUAL, EQUAL_EQUAL)) {
            Token operator = previous();
            Expr right = comparison();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
        */
    }

    private Expr comparison() {
        // Try to handle with "binaryHelper" method
        return binaryHelper(this::term, GREATER, GREATER_EQUAL, LESS, LESS_EQUAL);

        /*
        Expr expr = term();

        while (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
            Token operator = previous();
            Expr right = term();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
        */
    }

    private Expr term() {
        // Try to handle with "binaryHelper" method
        return binaryHelper(this::factor, MINUS, PLUS);

        /*
        Expr expr = factor();

        while (match(MINUS, PLUS)) {
            Token operator = previous();
            Expr right = factor();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
        */
    }

    private Expr factor() {
        // Try to handle with "binaryHelper" method
        return binaryHelper(this::unary, SLASH, STAR);

        /*
        Expr expr = unary();

        while (match(SLASH, STAR)) {
            Token operator = previous();
            Expr right = unary();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
        */
    }

    private Expr binaryHelper(BinaryOperand operand, TokenType... types) {
        Expr expr = operand.eval();

        while (match(types)) {
            Token operator = previous();
            Expr right = operand.eval();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr unary() {
        if (match(BANG, MINUS)) {
            Token operator = previous();
            Expr right = unary();
            return new Expr.Unary(operator, right);
        }

        return primary();
    }

    private Expr primary() {
        if (match(FALSE)) {
            return new Expr.Literal(false);
        }
        if (match(TRUE)) {
            return new Expr.Literal(true);
        }
        if (match(NIL)) {
            return new Expr.Literal(null);
        }

        if (match(NUMBER, STRING)) {
            return new Expr.Literal(previous().literal);
        }

        if (match(IDENTIFIER)) {
            return new Expr.Variable(previous());
        }

        if (match(LEFT_PAREN)) {
            Expr expr = expression();
            consume(RIGHT_PAREN, "Expect ')' after expression.");
            return new Expr.Grouping(expr);
        }

        // Error productions.
        if (match(BANG_EQUAL, EQUAL_EQUAL)) {
            error(previous(), "Missing left-hand operand.");
            equality();
            return null;
        }
        if (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
            error(previous(), "Missing left-hand operand.");
            comparison();
            return null;
        }
        if (match(PLUS)) {
            error(previous(), "Missing left-hand operand.");
            term();
            return null;
        }
        if (match(STAR, SLASH)) {
            error(previous(), "Missing left-hand operand.");
            factor();
            return null;
        }

        // Standard error.
        throw error(peek(), "Expect expression.");
    }

    // Checks to see if the current token has any of the given types.
    private boolean match(TokenType... types) {
        for (TokenType type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }

        return false;
    }

    private Token consume(TokenType type, String message) {
        if (check(type)) { return advance(); }
        throw error(peek(), message);
    }

    // Returns true if the current token is of the given type.
    private boolean check(TokenType type) {
        if (isAtEnd()) { return false; }
        return peek().type == type;
    }

    // Consumes the current token and returns it.
    private Token advance() {
        if (!isAtEnd()) { current++; }
        return previous();
    }

    // Checks if we have run out of tokens to parse.
    private boolean isAtEnd() {
        return peek().type == EOF;
    }

    // Returns current token we have yet to consume.
    private Token peek() {
        return tokens.get(current);
    }

    // Returns the most recently consumed token.
    private Token previous() {
        return tokens.get(current - 1);
    }

    private ParseError error(Token token, String message) {
        Lox.error(token, message);
        return new ParseError();
    }

    private void synchronize() {
        advance();

        while (!isAtEnd()) {
            if (previous().type == SEMICOLON) {
                return;
            }
            switch (peek().type) {
                case CLASS:
                case FUN:
                case VAR:
                case FOR:
                case IF:
                case WHILE:
                case PRINT:
                case RETURN:
                    return;
            }
            advance();
        }
    }
}
