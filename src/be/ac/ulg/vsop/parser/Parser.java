package be.ac.ulg.vsop.parser;

import java.util.ArrayList;

import java_cup.runtime.ComplexSymbolFactory;
import java_cup.runtime.ScannerBuffer;
import be.ac.ulg.vsop.lexer.Symbol;

public class Parser implements java_cup.runtime.Scanner {
   
   private boolean failed, extd;
   private int index;
   private ArrayList<Symbol> tokens;
   private ASTNode root;
   private ScannerBuffer sb;
   public static String name;
   public static int lastLine, lastColumn;
   
   /**
    * Creates a parser by getting the list of tokens.
    * @param args Original file name.
    * @param ws Symbols.
    * @param ext Whether to consider VSOPExtended.
    */
   public Parser(String args, ArrayList<Symbol> ws, boolean ext) {
      root = null;
      failed = true;
      tokens = ws;
      Parser.name = args;
      sb = new ScannerBuffer(this);
      Parser.lastLine = Parser.lastColumn = 0;
      extd = ext;
   }

   /**
    * Parse the list of tokens and updated failed field.
    */
   public void parse() {
      index = 0;
      failed = false;
      
      java_cup.runtime.lr_parser p = extd? new VSOPExtendedParser(sb, new ComplexSymbolFactory()): new VSOPParser(sb, new ComplexSymbolFactory());
      try {
         root = (ASTNode)p.parse().value;
      } catch(Exception e) {
         System.err.println(Parser.name + ":" + Parser.lastLine + ":" + Parser.lastColumn + ": syntax error " + e.getMessage());
         failed = true;
      }
   }

   /**
    * Returns whether parsing is done and suceeded.
    * @return Indicated.
    */
   public boolean isParseFailed() {
      return failed;
   }

   /**
    * Prints the created tree of tokens to standard output.
    * @param All Print type info.
    */
   public void dumpTree(boolean all) {
      if(root != null) {
         root.dump(all);
         System.out.println("");
      }
   }
   
   /**
    * Returns the AST built.
    * @return AST.
    */
   public ASTNode getRoot() {
      if(failed == false)
         return root;
      return null;
   }
   
   /**
    * Returns the next token to the parser.
    * @return Token.
    */
   @Override
   public java_cup.runtime.Symbol next_token() throws Exception {
      if(index == tokens.size())
         return null;
      return tokens.get(index++);
   }

}
