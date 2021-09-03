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
    // field used to make sure no code with a known error in it gets executed
    static boolean hadError = false;

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
        run(new String(bytes, Charset.defaultCharset()));

        // Exit code indicates that: "The input data was incorrect in some way."
        if (hadError) System.exit(65);
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

        for (;;) {
            System.out.print("> ");
            String line = reader.readLine();
            if (line == null) break;
            run(line);

            // Do not kill the session because one line of code had some error
            hadError = false;
        }
    }

    /**
     * The run function runs the scanner and produces tokens.
     *
     * @param source
     */
    private static void run(String source) {
        Scanner scanner = new Scanner(source);
        List<Token> tokens = scanner.scanTokens();

        // print tokens for testing purposes
        for (Token token : tokens) {
            System.out.println(token);
        }
    }

    /**
     * Function for error handling.
     *
     * @param line
     * @param message
     */
    static void error(int line, String message) {
        report(line, "", message);
    }

    // Some helper function for the error function above.
    private static void report(int line, String where, String message) {
        System.err.println("[line " + line + "] Error" + where + ": " + message);
        hadError = true;
    }

}
