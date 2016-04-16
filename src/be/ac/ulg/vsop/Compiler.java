package be.ac.ulg.vsop;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;

import be.ac.ulg.vsop.analyzer.Analyzer;
import be.ac.ulg.vsop.codegen.CGen;
import be.ac.ulg.vsop.lexer.Lexer;
import be.ac.ulg.vsop.lexer.ReaderWrapper;
import be.ac.ulg.vsop.parser.Parser;

public class Compiler {

   /**
    * Runs the full compiler, or a subset of functionalities if called with an argument.
    * @param args Arguments.
    */
	public static void main(String[] args) {
		final int findex = (args.length > 1)? 1 : 0;
		FileReader file = null;
		
		if(args.length < 1) {
			System.out.println("Not enough params");
			System.out.println("Usage: java -jar Compiler.jar <source_file>");
			System.exit(1);
		}
		try {
			file = new ReaderWrapper(new File(args[findex]));
		} catch (FileNotFoundException e) {
			System.out.println("File opening error");
			System.out.println("Usage: java -jar Compiler.jar <source_file>");
			System.exit(1);
		}
		
		Lexer lexer = new Lexer(file, args[findex]);
		lexer.parse();
		if(findex == 1 && args[0].equals("-lex")) {
			lexer.dumpTokens();
			System.exit(lexer.isParseFailed()? 1 : 0);
		}
		if(lexer.isParseFailed()) {
         System.err.println("Lexing error: quitting.");
         System.exit(1);
      }
		
		Parser parser = new Parser(lexer);
		parser.parse();
      if(findex == 1 && args[0].equals("-parse")) {
         parser.dumpTree(false);
         System.exit(parser.isParseFailed()? 1 : 0);
      }
      if(parser.isParseFailed()) {
         System.err.println("Parse error: quitting.");
         System.exit(1);
      }
      
      Analyzer a = null;
      try {
         a = new Analyzer();
         a.regScope(parser.getRoot());
      } catch(Exception e) {
         System.err.println(lexer.name + ":" + e.getMessage());e.printStackTrace();
         System.err.println("Semantics error: quitting.");
         System.exit(1);
      }
      if(findex == 1 && args[0].equals("-check")) {
         parser.dumpTree(true);
         System.exit(0);
      }
      
      CGen gen = new CGen(parser.getRoot(), a.getExt());
      gen.emit(System.out);
      System.exit(0);
	}

}
