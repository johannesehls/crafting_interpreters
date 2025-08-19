# Crafting Interpreters

My implementations of the `lox` programming language from the book [*Crafting Interpreters*](https://craftinginterpreters.com/) by Robert Nystrom. This includes two implementations: 
- one in `Java` called `Jlox` and 
- another one in `C` called `Clox` focusing more on performance than the former. 

## Jlox

`Jlox` is the high-level language interpreter built using a tree-walk approach. It emphasizes readability and simplicity, guiding the foundational stages of lexical analysis, language parsing, and execution.

Completed so far (up to Chapter 8):
- Scanner (lexical analysis)
- Parser (syntax analysis)
- Expression evaluation
- Statements and State (Variables, Blocks, etc.)

Planned:
- Complete Jlox with tree-walk interpreter and runtime (Chapters 9–13)

---

## Clox

Next phase: building a bytecode virtual machine and compiler in `C` for the lox language (`Clox`).

Planned:
- Implement Clox VM and compiler following Chapters 14–30

---

This project reflects my passion for compiler engineering, language design, and systems programming, with ongoing progress as time allows.
