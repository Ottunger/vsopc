/* VSOP Parser */
package be.ac.ulg.vsop.parser;

import java_cup.runtime.*;
import be.ac.ulg.vsop.lexer.Symbol;
import be.ac.ulg.vsop.parser.SymbolValue;
import be.ac.ulg.vsop.parser.ASTNode;
import be.ac.ulg.vsop.parser.Parser;

/* Overrides */
parser code {:
   public void report_error(String message, Object info) {
      System.err.println(Parser.name + ":" + Parser.lastLine + ":" + Parser.lastColumn + ": syntax error " + message);
   }
   public void syntax_error(Symbol cur_token) throws Exception {
      Parser.lastLine = cur_token.line;
      Parser.lastColumn = cur_token.col;
      throw new Exception("symbol not within expected ones");
   }
:};

/* Terminals (tokens returned by the scanner). */
terminal Symbol AND, BOOL, CLASS, DO, ELSE, EXTENDS, FALSE, IF, IN, INT32, ISNULL;
terminal Symbol LET, NEW, NOT, STRING, THEN, TRUE, WHILE, LBRACE, RBRACE, LPAR, RPAR;
terminal Symbol COLON, SEMICOLON, COMMA, PLUS, MINUS, TIMES, DIV, POW, EQUAL;
terminal Symbol LOWER, LOWER_EQUAL, ASSIGN, DOT;
terminal Symbol INTEGER_LITERAL;
terminal Symbol OBJECT_IDENTIFIER, TYPE_IDENTIFIER, STRING_LITERAL;
terminal Symbol NULL, UNIT, UNIT_VALUE; /* Newly defined */

/* Non terminals */
non terminal ASTNode types, lit, program, class_all, class_body;
non terminal ASTNode field, may_assign, assign, method, formals, formals_full, formal;
non terminal ASTNode block, block_full, expression, args, args_full;

/* Precedences */
precedence right ASSIGN;
precedence left AND;
precedence right NOT;
precedence nonassoc LOWER, LOWER_EQUAL, EQUAL;
precedence left PLUS, MINUS;
precedence left TIMES, DIV;
precedence left ISNULL;
precedence right POW;
precedence left DOT;
precedence nonassoc LPAR, RPAR;
precedence nonassoc LBRACE, RBRACE;

/* Start symbol */
start with program;

/* The grammar */
types ::= BOOL:t {: Parser.lastLine = t.line; Parser.lastColumn = t.col; RESULT = new ASTNode(SymbolValue.BOOL, null); RESULT.addProp("line", t.line + ""); RESULT.addProp("col", t.col + ""); RESULT.addProp("type", "bool"); :}
        | UNIT:t {: Parser.lastLine = t.line; Parser.lastColumn = t.col; RESULT = new ASTNode(SymbolValue.UNIT, null); RESULT.addProp("line", t.line + ""); RESULT.addProp("col", t.col + ""); RESULT.addProp("type", "unit"); :}
        | STRING:t {: Parser.lastLine = t.line; Parser.lastColumn = t.col; RESULT = new ASTNode(SymbolValue.STRING, null); RESULT.addProp("line", t.line + ""); RESULT.addProp("col", t.col + ""); RESULT.addProp("type", "string"); :}
        | INT32:t {: Parser.lastLine = t.line; Parser.lastColumn = t.col; RESULT = new ASTNode(SymbolValue.INT32, null); RESULT.addProp("line", t.line + ""); RESULT.addProp("col", t.col + ""); RESULT.addProp("type", "int32"); :}
        | TYPE_IDENTIFIER:t {: Parser.lastLine = t.line; Parser.lastColumn = t.col; RESULT = new ASTNode(SymbolValue.TYPE_IDENTIFIER, t.val); RESULT.addProp("line", t.line + ""); RESULT.addProp("col", t.col + ""); RESULT.addProp("type", ASTNode.typeValue(RESULT)); :};
lit ::= INTEGER_LITERAL:t {: Parser.lastLine = t.line; Parser.lastColumn = t.col; RESULT = new ASTNode(SymbolValue.INTEGER_LITERAL, t.val); RESULT.addProp("line", t.line + ""); RESULT.addProp("col", t.col + ""); RESULT.addProp("type", "int32"); :}
      | STRING_LITERAL:t  {: Parser.lastLine = t.line; Parser.lastColumn = t.col; RESULT = new ASTNode(SymbolValue.STRING_LITERAL, t.val); RESULT.addProp("line", t.line + ""); RESULT.addProp("col", t.col + ""); RESULT.addProp("type", "string"); :}
      | TRUE:t {: Parser.lastLine = t.line; Parser.lastColumn = t.col; RESULT = new ASTNode(SymbolValue.TRUE, null); RESULT.addProp("line", t.line + ""); RESULT.addProp("col", t.col + ""); RESULT.addProp("type", "bool"); :}
      | FALSE:t {: Parser.lastLine = t.line; Parser.lastColumn = t.col; RESULT = new ASTNode(SymbolValue.FALSE, null); RESULT.addProp("line", t.line + ""); RESULT.addProp("col", t.col + ""); RESULT.addProp("type", "bool"); :}
      | NULL:t {: Parser.lastLine = t.line; Parser.lastColumn = t.col; RESULT = new ASTNode(SymbolValue.NULL, null); RESULT.addProp("line", t.line + ""); RESULT.addProp("col", t.col + ""); RESULT.addProp("type", "object"); :};

program ::= class_all:t program:c {: c.pushChild(t); t.shuffleClass(); RESULT = c; :}
          | class_all:t {: RESULT = new ASTNode("program", null); RESULT.addChild(t); t.shuffleClass(); :};
class_all ::= CLASS TYPE_IDENTIFIER:t LBRACE class_body:c RBRACE {: RESULT = c; ASTNode b = new ASTNode(SymbolValue.TYPE_IDENTIFIER, "Object"); b.addProp("line", t.line + ""); b.addProp("col", t.col + ""); c.addChild(b); ASTNode a = new ASTNode(SymbolValue.TYPE_IDENTIFIER, t.val); a.addProp("line", t.line + ""); a.addProp("col", t.col + ""); c.addChild(a); :}
             | CLASS TYPE_IDENTIFIER:t EXTENDS TYPE_IDENTIFIER:e LBRACE class_body:c RBRACE {: RESULT = c; ASTNode b = new ASTNode(SymbolValue.TYPE_IDENTIFIER, e.val); b.addProp("line", e.line + ""); b.addProp("col", e.col + ""); c.addChild(b); ASTNode a = new ASTNode(SymbolValue.TYPE_IDENTIFIER, t.val); a.addProp("line", t.line + ""); a.addProp("col", t.col + ""); c.addChild(a); :};
class_body ::= field:t class_body:c {: c.pushChild(t); RESULT = c; :}
             | method:t class_body:c {: c.pushChild(t); RESULT = c; :}
             | {: RESULT = new ASTNode(SymbolValue.CLASS, null); :};

field ::= OBJECT_IDENTIFIER:o COLON types:t may_assign:m SEMICOLON {: RESULT = new ASTNode("field", null); ASTNode a = new ASTNode(SymbolValue.OBJECT_IDENTIFIER, o.val); a.addProp("line", o.line + ""); a.addProp("col", o.col + ""); a.addProp("type", ASTNode.typeValue(t)); RESULT.addChild(a); RESULT.addChild(t); if(m != null) RESULT.addChild(m); :} ;
may_assign ::= assign:t {: RESULT = t; :}
             | {: RESULT = null; :};
assign ::= ASSIGN expression:t {: RESULT = new ASTNode(SymbolValue.ASSIGN, null); RESULT.addChild(t); :};

method ::= OBJECT_IDENTIFIER:o LPAR formals:f RPAR COLON types:t block:b {: RESULT = new ASTNode("method", null); RESULT.addProp("type", ASTNode.typeValue(t)); ASTNode a = new ASTNode(SymbolValue.OBJECT_IDENTIFIER, o.val); a.addProp("line", o.line + ""); a.addProp("col", o.col + ""); RESULT.addChild(a); if(f != null) RESULT.addChild(f); RESULT.addChild(t); RESULT.addChild(b); :};
formals ::= formal:t COMMA formals_full:c {: RESULT = c; c.pushChild(t); :}
          | formal:t {: RESULT = new ASTNode("formals", null); RESULT.pushChild(t); :}
          | {: RESULT = null; :};
formals_full ::= formal:t COMMA formals_full:c {: RESULT = c; c.pushChild(t); :}
               | formal:t {: RESULT = new ASTNode("formals", null); RESULT.pushChild(t); :};
formal ::= OBJECT_IDENTIFIER:o COLON types:t {: RESULT = new ASTNode("formal", null); ASTNode a = new ASTNode(SymbolValue.OBJECT_IDENTIFIER, o.val); a.addProp("line", o.line + ""); a.addProp("col", o.col + ""); a.addProp("type", ASTNode.typeValue(t)); RESULT.addChild(a); RESULT.addChild(t); :}
         | OBJECT_IDENTIFIER:o COLON types:t COLON lit:l {: RESULT = new ASTNode("formal", null); ASTNode a = new ASTNode(SymbolValue.OBJECT_IDENTIFIER, o.val); a.addProp("line", o.line + ""); a.addProp("col", o.col + ""); a.addProp("type", ASTNode.typeValue(t)); RESULT.addChild(a); RESULT.addChild(t); RESULT.addChild(l); :};

block ::= LBRACE expression:e SEMICOLON block_full:b RBRACE {: RESULT = b; b.pushChild(e); :}
        | LBRACE expression:e RBRACE {: RESULT = new ASTNode("block", null); RESULT.addChild(e); :};
block_full ::= expression:e {: RESULT = new ASTNode("block", null); RESULT.addChild(e); :}
             | expression:e SEMICOLON block_full:b {: RESULT = b; b.pushChild(e); :};
args ::= expression:e COMMA args_full:b {: RESULT = b; b.pushChild(e); :}
       | expression:e {: RESULT = new ASTNode("args", null); RESULT.addChild(e); :}
       | {: RESULT = null; :};
args_full ::= expression:e COMMA args_full:b {: RESULT = b; b.pushChild(e); :}
            | expression:e {: RESULT = new ASTNode("args", null); RESULT.addChild(e); :};

expression ::= IF expression:e THEN expression:f {: RESULT = new ASTNode("if", null); RESULT.addChild(e); RESULT.addChild(f); :}
             | IF expression:e THEN expression:f ELSE expression:g {: RESULT = new ASTNode("if", null); RESULT.addChild(e); RESULT.addChild(f); RESULT.addChild(g); :}
             | WHILE expression:e DO expression:f {: RESULT = new ASTNode("while", null); RESULT.addChild(e); RESULT.addChild(f); :}
             | LET OBJECT_IDENTIFIER:o COLON types:t may_assign:m IN expression:e {: RESULT = new ASTNode("let", null); ASTNode a = new ASTNode(SymbolValue.OBJECT_IDENTIFIER, o.val); a.addProp("line", o.line + ""); a.addProp("col", o.col + ""); a.addProp("type", ASTNode.typeValue(t)); RESULT.addChild(a); RESULT.addChild(t); if(m != null) RESULT.addChild(m); RESULT.addChild(e); :}
             | OBJECT_IDENTIFIER:o assign:m {: RESULT = new ASTNode("assign", null); ASTNode a = new ASTNode(SymbolValue.OBJECT_IDENTIFIER, o.val); a.addProp("line", o.line + ""); a.addProp("col", o.col + ""); RESULT.addChild(a); RESULT.addChild(m); :}
             | NOT expression:e {: RESULT = new ASTNode(SymbolValue.NOT, null); RESULT.addChild(e); :}
             | expression:e EQUAL expression:f {: RESULT = new ASTNode(SymbolValue.EQUAL, null); RESULT.addChild(e); RESULT.addChild(f); :}
             | expression:e LOWER expression:f {: RESULT = new ASTNode(SymbolValue.LOWER, null); RESULT.addChild(e); RESULT.addChild(f); :}
             | expression:e LOWER_EQUAL expression:f {: RESULT = new ASTNode(SymbolValue.LOWER_EQUAL, null); RESULT.addChild(e); RESULT.addChild(f); :}
             | expression:e AND expression:f {: RESULT = new ASTNode(SymbolValue.AND, null); RESULT.addChild(e); RESULT.addChild(f); :}
             | expression:e PLUS expression:f {: RESULT = new ASTNode(SymbolValue.PLUS, null); RESULT.addChild(e); RESULT.addChild(f); :}
             | expression:e MINUS expression:f {: RESULT = new ASTNode(SymbolValue.MINUS, null); RESULT.addChild(e); RESULT.addChild(f); :}
             | expression:e TIMES expression:f {: RESULT = new ASTNode(SymbolValue.TIMES, null); RESULT.addChild(e); RESULT.addChild(f); :}
             | expression:e DIV expression:f {: RESULT = new ASTNode(SymbolValue.DIV, null); RESULT.addChild(e); RESULT.addChild(f); :}
             | expression:e POW expression:f {: RESULT = new ASTNode(SymbolValue.POW, null); RESULT.addChild(e); RESULT.addChild(f); :}
             | MINUS expression:e {: RESULT = new ASTNode("uminus", null); RESULT.addChild(e); :} %prec ISNULL
             | ISNULL expression:e {: RESULT = new ASTNode(SymbolValue.ISNULL, null); RESULT.addChild(e); :}
             | OBJECT_IDENTIFIER:o LPAR args:m RPAR {: RESULT = new ASTNode("call", null); ASTNode b = new ASTNode(SymbolValue.OBJECT_IDENTIFIER, "self"); b.addProp("line", o.line + ""); b.addProp("col", o.col + ""); RESULT.addChild(b); ASTNode a = new ASTNode(SymbolValue.OBJECT_IDENTIFIER, o.val); a.addProp("line", o.line + ""); a.addProp("col", o.col + ""); RESULT.addChild(a); if(m != null) RESULT.addChild(m); :}
             | expression:e DOT OBJECT_IDENTIFIER:o LPAR args:m RPAR {: RESULT = new ASTNode("call", null); RESULT.addChild(e); ASTNode a = new ASTNode(SymbolValue.OBJECT_IDENTIFIER, o.val); a.addProp("line", o.line + ""); a.addProp("col", o.col + ""); RESULT.addChild(a); if(m != null) RESULT.addChild(m); :}
             | expression:e LOWER TYPE_IDENTIFIER:c DOT OBJECT_IDENTIFIER:o LPAR args:m RPAR {: RESULT = new ASTNode("call", null); RESULT.addChild(e); ASTNode a = new ASTNode(SymbolValue.OBJECT_IDENTIFIER, o.val); a.addProp("line", o.line + ""); a.addProp("col", o.col + ""); RESULT.addChild(a); if(m != null) RESULT.addChild(m); RESULT.addProp("cast", c.val); :}
             | NEW TYPE_IDENTIFIER:t {: RESULT = new ASTNode(SymbolValue.NEW, null); ASTNode a = new ASTNode(SymbolValue.TYPE_IDENTIFIER, t.val); a.addProp("line", t.line + ""); a.addProp("col", t.col + ""); RESULT.addChild(a); :}
             | OBJECT_IDENTIFIER:o {: RESULT = new ASTNode(SymbolValue.OBJECT_IDENTIFIER, o.val); RESULT.addProp("line", o.line + ""); RESULT.addProp("col", o.col + ""); :}
             | lit:l {: RESULT = l; :}
             | LPAR:t RPAR {: Parser.lastLine = t.line; Parser.lastColumn = t.col; RESULT = new ASTNode(SymbolValue.UNIT_VALUE, null); RESULT.addProp("line", t.line + ""); RESULT.addProp("col", t.col + ""); RESULT.addProp("type", "unit"); :}
             | LPAR expression:e RPAR {: RESULT = e; :}
             | block:b {: RESULT = b; :};