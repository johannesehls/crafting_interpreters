package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.craftinginterpreters.lox.TokenType.*;

/*
 * program       → declaration* EOF ;
 *
 * declaration   → funDecl
 *                | varDecl
 *                | statement ;
 *
 * funcDecl      → "fun" function ;
 * function      → IDENTIFIER "(" parameters? ")" block ;
 * parameters    → IDENTIFIER ( "," IDENTIFIER )* ;
 *
 * varDecl       → "var" IDENTIFIER ( "=" expression )? ";" ;
 *
 * statement     → exprStmt
 *                 | ifStmt
 *                 | printStmt
 *                 | returnStmt
 *                 | whileStmt
 *                 | forStmt
 *                 | block
 *                 | breakStmt;
 *
 * exprStmt      → expression ";" ;
 * ifStmt        → "if" "(" expression ")" statement ( "else" statement )? ;
 * printStmt     → "print" expression ";" ;
 * returnStmt    → "return" expression? ";" ;
 * whileStmt     → "while" "(" expression ")" statement ;
 * forStmt       → "for" "(" ( varDecl | exprStmt | ";" )
 *                   expression? ";"
 *                   expression? ")" statement ;
 * block         → "{" declaration* "}" ;
 * breakStmt     → "break" ";" ;
 *
 * expression    → comma ;
 * comma         → assignment ( "," assignment)* ;
 * assignment    → IDENTIFIER "=" assignment
 *               | ternary ;
 * ternary       → logic_or ( "?" expression ":" ternary )?
 * logic_or      → logic_and ( "or" logic_and )* ;
 * logic_and     → equality ( "and" equality )* ;
 * equality      → comparison ( ( "!=" | "==" ) comparison )* ;
 * comparison    → term ( ( ">" | ">=" | "<" | "<=" ) term )* ;
 * term          → factor ( ( "-" | "+" ) factor )* ;
 * factor        → unary ( ( "/" | "*" ) unary )* ;
 * unary         → ("!"|"-")unary | call ;
 * call          → primary ( "(" arguments? ")" )* ;
 * primary       → NUMBER | STRING | "true" | "false" | "nil"
 *                 | "(" expression ")" | IDENTIFIER
 *                 // Error productions...
 *                 | ( "!=" | "==" ) equality
 *                 | ( ">" | ">=" | "<" | "<=" ) comparison
 *                 | ( "+" ) term
 *                 | ( "/" | "*" ) factor
 *                 | lambda;
 * arguments     → assignment ( "," assignment )* ;
 * lambda        → "fun" "(" parameters? ")" block ;
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
            if (check(FUN) && checkNext(IDENTIFIER)) {
                advance();
                return function("function");
            }
            if (match(VAR)) return varDeclaration();
            return statement();
        } catch (ParseError error) {
            synchronize();
            return null;
        }
    }

    private Stmt function(String kind) {
        Token name = null;
        if (kind.equals("lambda")) {
            consume(LEFT_PAREN, "Expect '(' after 'fun' keyword in lambda.");
        } else {
            name = consume(IDENTIFIER, "Expect " + kind + " name.");
            consume(LEFT_PAREN, "Expect '(' after " + kind + " name.");
        }

        List<Token> parameters = new ArrayList<>();
        if (!check(RIGHT_PAREN)) {
            do {
                if (parameters.size() >= 255) {
                    error(peek(), "Can't have more than 255 parameters.");
                }

                parameters.add(consume(IDENTIFIER, "Expect parameter name."));
            } while (match(COMMA));
        }
        consume(RIGHT_PAREN, "Expect ')' after parameters.");
        consume(LEFT_BRACE, "Expect '{' before " + kind + " body.");
        List<Stmt> body = block();

        return new Stmt.Function(name, parameters, body);
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
        if (match(IF)) return ifStatement();
        if (match(PRINT)) return printStatement();
        if (match(RETURN)) return returnStatement();
        if (match(WHILE)) return whileStatement();
        if (match(FOR)) return forStatement();
        if (match(BREAK)) return breakStatement();
        if (match(LEFT_BRACE)) return new Stmt.Block(block());
        return expressionStatement();
    }

    private Stmt ifStatement() {
        consume(LEFT_PAREN, "Expect '(' after 'if'.");
        Expr condition = expression();
        consume(RIGHT_PAREN, "Expect ')' after 'if condition'.");

        Stmt thenBranch = statement();
        Stmt elseBranch = null;
        if (match(ELSE)) {
            elseBranch = statement();
        }

        return new Stmt.If(condition, thenBranch, elseBranch);
    }

    private Stmt printStatement() {
        Expr value = expression();
        consume(SEMICOLON, "Expect ';' after value.");
        return new Stmt.Print(value);
    }

    private Stmt returnStatement() {
        Token keyword = previous();
        Expr value = null;
        if (!check(SEMICOLON)) {
            value = expression();
        }

        consume(SEMICOLON, "Expect ';' after return value.");
        return new Stmt.Return(keyword, value);
    }

    private Stmt whileStatement() {
        consume(LEFT_PAREN, "Expect '(' after 'while'.");
        Expr condition = expression();
        consume(RIGHT_PAREN, "Expect ')' after condition.");
        Stmt body = statement();

        return new Stmt.While(condition, body);
    }

    private Stmt forStatement() {
        consume(LEFT_PAREN, "Expect '(' after 'while'.");

        Stmt initializer;
        if (match(SEMICOLON)) {
            initializer = null;
        } else if (match(VAR)) {
            initializer = varDeclaration();
        } else {
            initializer = expressionStatement();
        }
        // here: first semicolon is consumed in any case.

        Expr condition = null;
        if (!check(SEMICOLON)) {
            condition = expression();
        }
        consume(SEMICOLON, "Expect ';' after loop condition");

        Expr increment = null;
        if (!check(RIGHT_PAREN)) {
            increment = expression();
        }
        consume(RIGHT_PAREN, "Expect ')' after for clauses");

        Stmt body = statement();

        if (increment != null) {
            body = new Stmt.Block(Arrays.asList(body, new Stmt.Expression(increment)));
        }

        if (condition == null) condition = new Expr.Literal(true);
        body = new Stmt.While(condition, body);

        if (initializer != null) {
            body = new Stmt.Block(Arrays.asList(initializer, body));
        }

        return body;
    }

    private Stmt breakStatement() {
        Token t = previous();
        consume(SEMICOLON, "Expect ';' after 'break'.");
        return new Stmt.Break(t);
    }

    private Stmt expressionStatement() {
        Expr expr = expression();
        consume(SEMICOLON, "Expect ';' after value.");
        return new Stmt.Expression(expr);
    }

    private List<Stmt> block() {
        List<Stmt> statements = new ArrayList<>();
        while (!check(RIGHT_BRACE) && !isAtEnd()) {
            statements.add(declaration());
        }

        consume(RIGHT_BRACE, "Expect '}' after block.");
        return statements;
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
        Expr expr = logicOr();

        if (match(QUESTION)) {
            Expr thenBranch = expression();
            consume(COLON, "Expect ':' after then branch of conditional expression.");
            Expr elseBranch = ternary();
            expr = new Expr.Ternary(expr, thenBranch, elseBranch);
        }

        return expr;
    }

    private Expr logicOr() {
        Expr expr = logicAnd();

        while (match(OR)) {
            Token operator = previous();
            Expr right = logicAnd();
            expr = new Expr.Logical(expr, operator, right);
        }

        return expr;
    }

    private Expr logicAnd() {
        Expr expr = equality();

        while (match(OR)) {
            Token operator = previous();
            Expr right = equality();
            expr = new Expr.Logical(expr, operator, right);
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

        return call();
    }

    private Expr call() {
        Expr expr = primary();

        while (true) {
            if (match(LEFT_PAREN)) {
                expr = finishCall(expr);
            } else {
                break;
            }
        }

        return expr;
    }

    private Expr finishCall(Expr callee) {
        List<Expr> arguments = new ArrayList<>();
        if (!check(RIGHT_PAREN)) {
            do {
                if (arguments.size() >= 255) {
                    error(peek(), "Can't have more than 255 arguments.");
                }
                arguments.add(assignment());
            } while (match(COMMA));
        }

        Token paren = consume(RIGHT_PAREN, "Expect ')' after arguments.");

        return new Expr.Call(callee, paren, arguments);
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

        if (match(FUN)) {
            return new Expr.Lambda(function("lambda"));
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

    // Returns true if the next token is of the given type.
    private boolean checkNext(TokenType type) {
        if (isAtEnd()) { return false; }
        TokenType nextType = tokens.get(current + 1).type;
        return nextType != EOF && nextType == type;
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
