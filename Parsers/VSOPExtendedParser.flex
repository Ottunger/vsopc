/*VSOP Parser*/
package be.ac.ulg.vsop.lexer;
import be.ac.ulg.vsop.parser.SymbolValue;
import java.util.Stack;
%%

%class VSOPExtendedParser
%type Symbol
%unicode
%line
%column

%{
   StringBuffer string = new StringBuffer();
   int nesting = 0, cbegin = 0, rbegin = 0;
   Stack<Integer> ccomment = new Stack<Integer>(), rcomment = new Stack<Integer>();

   public Symbol symbol(int type) {
      return new Symbol(type, yyline + 1, yycolumn + 1);
   }
   public Symbol symbol(int type, String value) {
      return new Symbol(type, yyline + 1, yycolumn + 1, value);
   }
   public Symbol symbol(int type, String value, int y, int x) {
      return new Symbol(type, y + 1, x + 1, value);
   }
   public Symbol symbol(int type, int value) {
      return new Symbol(type, yyline + 1, yycolumn + 1, value);
   }
   public Symbol symbol(int type, int value, int y, int x) {
      return new Symbol(type, y + 1, x + 1, value);
   }
   public Symbol symbol(int type, float value) {
      return new Symbol(type, yyline + 1, yycolumn + 1, value);
   }
   public Symbol symbol(int type, float value, int y, int x) {
      return new Symbol(type, y + 1, x + 1, value);
   }
   public String toPoint(int pt) {
      if(pt < 10)
         return pt + "";
      else if(pt == 10)
         return "a";
      else if(pt == 11)
         return "b";
      else if(pt == 12)
         return "c";
      else if(pt == 13)
         return "d";
      else if(pt == 14)
         return "e";
      else if(pt == 15)
         return "f";
      return "0";
   }
   public String toVSOPString(String text) {
      int point = text.charAt(0);
      if(point > 126 || point < 32) {
         return "\\x" + toPoint(point / 16) + toPoint(point % 16);
      } else
         return text;
   }
%}

LineTerminator = \r\n|\n
White = [ \t\f\r]
WhiteSpace = {LineTerminator} | {White}
EndOfLineComment = "//" [^\n]* {LineTerminator}?
Operators = [\ {\ } \( \) : ; , \+ - \* \/ \^ = \< \.]
AfterToken = {WhiteSpace} | {Operators}
AfterDigit = {AfterToken} | [_a-zA-Z]

Identifier = [_a-z][_a-zA-Z0-9]*
Type = [A-Z][_a-zA-Z0-9]*
DecIntegerLiteral = [0-9]+

%xstate STRING, COMMENT, ERROR, HEX, BIN, BYTESTRING

%%

<YYINITIAL> {
   /* keywords */
   "and"/{AfterToken} { return symbol(SymbolValue.AND); }
   "bool"/{AfterToken} { return symbol(SymbolValue.BOOL); }
   "class"/{AfterToken} { return symbol(SymbolValue.CLASS); }
   "do"/{AfterToken} { return symbol(SymbolValue.DO); }
   "else"/{AfterToken} { return symbol(SymbolValue.ELSE); }
   "extends"/{AfterToken} { return symbol(SymbolValue.EXTENDS); }
   "false"/{AfterToken} { return symbol(SymbolValue.FALSE); }
   "if"/{AfterToken} { return symbol(SymbolValue.IF); }
   "in"/{AfterToken} { return symbol(SymbolValue.IN); }
   "int32"/{AfterToken} { return symbol(SymbolValue.INT32); }
   "isnull"/{AfterToken} { return symbol(SymbolValue.ISNULL); }
   "let"/{AfterToken} { return symbol(SymbolValue.LET); }
   "new"/{AfterToken} { return symbol(SymbolValue.NEW); }
   "not"/{AfterToken} { return symbol(SymbolValue.NOT); }
   "string"/{AfterToken} { return symbol(SymbolValue.STRING); }
   "then"/{AfterToken} { return symbol(SymbolValue.THEN); }
   "true"/{AfterToken} { return symbol(SymbolValue.TRUE); }
   "while"/{AfterToken} { return symbol(SymbolValue.WHILE); }
   "null"/{AfterToken} { return symbol(SymbolValue.NULL); }
   "unit"/{AfterToken} { return symbol(SymbolValue.UNIT); }
   "or"/{AfterToken} { return symbol(SymbolValue.OR); }
   "float"/{AfterToken} { return symbol(SymbolValue.FLOAT); }
   
   /* identifiers */ 
   {Identifier}/{AfterToken} { return symbol(SymbolValue.OBJECT_IDENTIFIER, yytext()); }
   {Type}/{AfterToken} { return symbol(SymbolValue.TYPE_IDENTIFIER, yytext()); }
  
   /* literals */
   "0x" { cbegin = yycolumn; rbegin = yyline; yybegin(HEX); }
   "0b" { cbegin = yycolumn; rbegin = yyline; yybegin(BIN); }
   {DecIntegerLiteral}/{AfterDigit} { return symbol(SymbolValue.INTEGER_LITERAL, Integer.parseInt(yytext())); }
   {DecIntegerLiteral}"."{DecIntegerLiteral}"f"/{AfterDigit} { return symbol(SymbolValue.FLOAT_LITERAL, Float.parseFloat(yytext())); }
   \" { cbegin = yycolumn; rbegin = yyline; string.setLength(0); yybegin(STRING); string.append('"'); }

   /* operators */
   "{" { return symbol(SymbolValue.LBRACE); }
   "}" { return symbol(SymbolValue.RBRACE); }
   "(" { return symbol(SymbolValue.LPAR); }
   ")" { return symbol(SymbolValue.RPAR); }
   ":" { return symbol(SymbolValue.COLON); }
   ";" { return symbol(SymbolValue.SEMICOLON); }
   "," { return symbol(SymbolValue.COMMA); }
   "+" { return symbol(SymbolValue.PLUS); }
   "-" { return symbol(SymbolValue.MINUS); }
   "*" { return symbol(SymbolValue.TIMES); }
   "/" { return symbol(SymbolValue.DIV); }
   "^" { return symbol(SymbolValue.POW); }
   "=" { return symbol(SymbolValue.EQUAL); }
   "<" { return symbol(SymbolValue.LOWER); }
   "<=" { return symbol(SymbolValue.LOWER_EQUAL); }
   "<-" { return symbol(SymbolValue.ASSIGN); }
   ">" { return symbol(SymbolValue.GREATER); }
   ">=" { return symbol(SymbolValue.GREATER_EQUAL); }
   "." { return symbol(SymbolValue.DOT); }
   "@" { return symbol(SymbolValue.SWITCH); }

   /* comments */
   {EndOfLineComment} {  }
   "(*" { ccomment.push(yycolumn); rcomment.push(yyline); nesting++; yybegin(COMMENT); }
  
   /* whitespace */
   {WhiteSpace} { /* ignore */ }
}

<HEX> {
   [0-9a-fA-F]+/{AfterToken} { yybegin(YYINITIAL); return symbol(SymbolValue.INTEGER_LITERAL, Integer.parseInt(yytext(), 16), rbegin, cbegin); }
   [0-9a-fA-F]*[^0-9a-fA-F] { yybegin(YYINITIAL); throw new java.io.IOException((rbegin+1) + ":" + (cbegin+1) + ": lexical error <0x" + yytext() + "> is not a valid non-decimal number notation."); }
}

<BIN> {
   [0-1]+/{AfterToken} { yybegin(YYINITIAL); return symbol(SymbolValue.INTEGER_LITERAL, Integer.parseInt(yytext(), 2), rbegin, cbegin); }
   [0-9a-fA-F]*[^0-9a-fA-F] { yybegin(YYINITIAL); throw new java.io.IOException((rbegin+1) + ":" + (cbegin+1) + ": lexical error <0b" + yytext() + "> is not a valid non-decimal number notation."); }
}

<STRING> {
   \" { yybegin(YYINITIAL); string.append('"'); return symbol(SymbolValue.STRING_LITERAL, string.toString(), rbegin, cbegin); }
   \\{White}*{LineTerminator}{White}* { /* ignore still in string multi-line */ }
   "\\x" { yybegin(BYTESTRING); string.append("\\x"); }
   \\[^xrntbf\"\\] { throw new java.io.IOException((yyline+1) + ":" + (yycolumn+1) + ": lexical error <" + yytext() + "> is not a valid espace character in a string."); }
   \x00 { throw new java.io.IOException((yyline+1) + ":" + (yycolumn+1) + ": lexical error <" + yytext() + "> is a raw null character in a string."); }
   \\\" { string.append("\\\""); }
   \\r { string.append("\\x0d"); }
   \\n { string.append("\\x0a"); }
   \\t { string.append("\\x09"); }
   \\f { string.append("\\x0c"); }
   \\b { string.append("\\x08"); }
   \\\\ { string.append("\\\\"); }
   . { string.append(toVSOPString(yytext())); }
   [^(\\\")]*[^\\\"]{LineTerminator} { String err = yytext(); err = err.replace("\r", ""); err = err.replace("\n", ""); throw new java.io.IOException((yyline+1) + ":" + (yycolumn+1+yytext().trim().length()) + ": lexical error <" + err + "> is not a well-escaped possibly multi-line string."); }
}

<BYTESTRING> {
   [0-9a-fA-F]{2} { yybegin(STRING); string.append(yytext()); }
   . { yypushback(1); yybegin(STRING); throw new java.io.IOException((yyline+1) + ":" + (yycolumn-1) + ": lexical error <\\x" + yytext() + "> is not a valid ASCII byte description."); }
}

<COMMENT> {
   "(*" { ccomment.push(yycolumn); rcomment.push(yyline); nesting++; }
   "*)" { ccomment.pop(); rcomment.pop(); nesting--; if(nesting == 0) yybegin(YYINITIAL); }
   [^*)] { /* ignore */ }
   [*)] { /* ignore */ }
}

/* error fallback */
[^] { yybegin(ERROR); yypushback(1); }

<ERROR> {
   .+{WhiteSpace}? { String err = yytext(); err = err.replace("\r", ""); err = err.replace("\n", ""); yybegin(YYINITIAL); throw new java.io.IOException((yyline+1) + ":" + (yycolumn+1) + ": lexical error <" + err + "> is not valid VSOP syntax."); }
}
