package com.craftinginterpreters.lox;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class Lox {
    private static final Interpreter interpreter = new Interpreter();
    // field used to make sure no code with a known error in it gets executed
    static boolean hadError = false;

    static boolean hadRuntimeError = false;

    public static void main(String[] args) throws IOException {
        if (args.length > 1) {
            System.out.println("Usage: jlox [script]");

            // Exit code indicates that: "The command was used incorrectly, e.g., with the
            //			   wrong number of arguments, a bad flag, a bad syntax
            //			   in a parameter, or whatever."
            System.exit(64);
        } else if (args.length == 1) {
            runFile(args[0]);
        } else {
            runPrompt();
        }
    }

    /**
     * Wrapper function that reads in a file from its path (given as command line argument later).
     * The wrapper function then calls the run function with the program as a String extracted from the file.
     *
     * @param path
     * @throws IOException
     */
    private static void runFile(String path) throws IOException {
        byte[] bytes = Files.readAllBytes(Paths.get(path));
        run(new String(bytes, Charset.defaultCharset()), false);

        // Exit code indicates that: "The input data was incorrect in some way."
        if (hadError) {
            System.exit(65);
        }
        if (hadRuntimeError) {
            System.exit(70);
        }
    }

    /**
     * Wrapper function that opens up a prompt where a user can enter some line of code.
     * This line then gets read in and the function calls the run function with the line of code as an argument.
     *
     * @throws IOException
     */
    private static void runPrompt() throws IOException {
        InputStreamReader input = new InputStreamReader(System.in);
        BufferedReader reader = new BufferedReader(input);

        for (; ; ) {
            System.out.print("> ");
            String line = reader.readLine();
            if (line == null) break;
            run(line, true);

            // Do not kill the session because one line of code had some error
            hadError = false;
        }
    }

    /**
     * The run function runs the scanner and produces tokens.
     *
     * @param source
     */
    private static void run(String source, boolean printExpr) {
        // Scanning phase.
        Scanner scanner = new Scanner(source);
        List<Token> tokens = scanner.scanTokens();

        // Parsing phase.
        Parser parser = new Parser(tokens);
        List<Stmt> statements = parser.parse();

        // Stop if there was a syntax error.
        if (hadError) {
            return;
        }

        interpreter.interpret(statements, new Interpreter.Config(printExpr));
    }

    /**
     * Function for error handling for the Scanner (line number).
     *
     * @param line
     * @param message
     */
    static void error(int line, String message) {
        report(line, "", message);
    }

    /**
     * Function for RuntimeError handling for the Interpreter.
     *
     * @param error
     */
    static void runtimeError(RuntimeError error) {
        System.err.println(error.getMessage() + "\n[line " + error.token.line + "]");
        hadRuntimeError = true;
    }

    // Some helper function for the error function above.
    private static void report(int line, String where, String message) {
        System.err.println("[line " + line + "] Error" + where + ": " + message);
        hadError = true;
    }

    /**
     * Function for error handling for the Parser (Token).
     *
     * @param token
     * @param message
     */
    static void error(Token token, String message) {
        if (token.type == TokenType.EOF) {
            report(token.line, " at end", message);
        } else {
            report(token.line, " at '" + token.lexeme + "'", message);
        }
    }
}
