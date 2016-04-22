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
		int findex = (args.length > 1)? 1 : 0;
		boolean ext = (findex == 1 && args[1].equals("-ext")) || ((args.length > 1) && args[0].equals("-ext"));
		if(ext) findex++;
		FileReader file = null;
		
		if(args.length < 1) {
			System.err.println("Not enough params");
			System.err.println("Usage: java -jar Compiler.jar [-lex | -parse | -check | -c | -llvm | -ext] <source_file>");
			System.exit(1);
		}
		if(!args[findex].endsWith(".vsop") && !args[findex].endsWith(".ve")) {
		   System.err.println("Input file is not VSOP(.vsop) nor VSOPExtended(.ve)");
         System.exit(1);
		}
		try {
			file = new ReaderWrapper(new File(args[findex]));
		} catch (FileNotFoundException e) {
			System.err.println("File opening error");
			System.err.println("Usage: java -jar Compiler.jar [-lex | -parse | -check | -c | -llvm | -ext] <source_file>");
			System.exit(1);
		}
		
		Lexer lexer = new Lexer(file, args[findex], ext);
		lexer.parse();
		if(findex > 0 && args[0].equals("-lex")) {
			lexer.dumpTokens();
			System.exit(lexer.isParseFailed()? 1 : 0);
		}
		if(lexer.isParseFailed()) {
         System.err.println("Lexing error: quitting.");
         System.exit(1);
      }
		
		Parser parser = new Parser(lexer, ext);
		parser.parse();
      if(findex > 0 && args[0].equals("-parse")) {
         parser.dumpTree(false);
         System.exit(parser.isParseFailed()? 1 : 0);
      }
      if(parser.isParseFailed()) {
         System.err.println("Parse error: quitting.");
         System.exit(1);
      }
      
      Analyzer a = null;
      try {
         a = new Analyzer(ext);
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
      
      CGen gen = new CGen(parser.getRoot(), a.getExt(), ext);
      try {
         if(findex > 0 && args[0].equals("-llvm")) {
            gen.emit(System.out, null, CGen.LLVM);
            System.exit(0);
         } else if(findex > 0 && args[0].equals("-c")) {
            gen.emit(System.out, null, CGen.C);
            System.exit(0);
         }
         gen.emit(System.out, args[findex].substring(0, args[findex].length() - 5), CGen.EXE);
         System.out.println("Code generation completed !");
      } catch(Exception e) {
         e.printStackTrace();
         System.err.println("Code emission error");
         System.exit(1);
      }
      System.exit(0);
	}

}
