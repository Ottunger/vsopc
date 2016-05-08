package be.ac.ulg.vsop;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Stack;
import java.util.function.Consumer;

import be.ac.ulg.vsop.analyzer.Analyzer;
import be.ac.ulg.vsop.codegen.CGen;
import be.ac.ulg.vsop.lexer.Lexer;
import be.ac.ulg.vsop.lexer.ReaderWrapper;
import be.ac.ulg.vsop.lexer.Symbol;
import be.ac.ulg.vsop.parser.Parser;
import be.ac.ulg.vsop.parser.SymbolValue;

public class Compiler {

   /**
    * Runs the full compiler, or a subset of functionalities if called with an argument.
    * @param args Arguments.
    */
	public static void main(String[] args) {
		int findex = 0, i;
		for(i = 0; i < args.length; i++)
		   if(!args[i].startsWith("-"))
		      findex = i;
		boolean ext = ((findex == 1 && args[0].equals("-ext")) || findex == 2 && args[1].equals("-ext"));
		String f;
		File ft;
		Stack<String> add = new Stack<String>();
		FileReader file = null;
		Lexer lexer = null;
		HashSet<Integer> lf =  new HashSet<Integer>();
		ArrayList<Symbol> ws = new ArrayList<Symbol>(), wf;
		
		//Check given inputs
		if(args.length < 1) {
			System.err.println("Not enough params");
			System.err.println("Usage: java -jar Compiler.jar [-lex | -parse | -check | -c | -llvm | -ext] <source_file> [-ci<c_include> | -cl<c_lib>]*");
			System.exit(1);
		}
		if(!args[findex].endsWith(".vsop") && !args[findex].endsWith(".ve")) {
		   System.err.println("Input file is not VSOP(.vsop) nor VSOPExtended(.ve)");
         System.exit(1);
		}
		
		//Open the files...
		add.push(args[findex]);
		while(!add.isEmpty()) {
		   f = add.pop();
		   ft = new File(f);
		   if(!lf.contains(ft.hashCode())) {
		      lf.add(ft.hashCode());
		      
      		try {
      			file = new ReaderWrapper(ft);
      		} catch (FileNotFoundException e) {
      			System.err.println("File opening error (" + f + ")");
      			System.exit(1);
      		}
      		
      		lexer = new Lexer(file, f, ext);
      		lexer.parse();
      		if(lexer.isParseFailed()) {
               System.err.println("Lexing error: quitting.");
               ws.addAll(lexer.getTokens());
               add.clear();
            }
      		wf = lexer.getTokens();
      		for(i = 0; i < wf.size(); i += 2) {
      		   if(wf.get(i).sym == SymbolValue.INCLUDE && wf.get(i + 1).sym == SymbolValue.STRING_LITERAL)
      		      add.push(wf.get(i + 1).val.toString().replaceAll("\"", ""));
      		   else
      		      break;
      		}
      		if(!f.equals(args[findex])) {
      		   wf.forEach(new Consumer<Symbol>() {
                  @Override
                  public void accept(Symbol s) {
                     s.col = 1;
                     s.line = 1;
                  }
      		   });
      		}
      		ws.addAll(wf.subList(i, wf.size() - 1));
		   }
		}
		ws.add(new Symbol(SymbolValue.EOF));
		if(findex > 0 && args[0].equals("-lex")) {
		   lexer.setTokens(ws);
         lexer.dumpTokens();
         System.exit(lexer.isParseFailed()? 1 : 0);
      }
		if(lexer.isParseFailed()) {
         System.err.println("Lexing error: quitting.");
         System.exit(1);
      }
		
		Parser parser = new Parser(args[findex], ws, ext);
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
      
      CGen gen = new CGen(parser.getRoot(), a.getExt(), ext, args, findex + 1);
      try {
         if(findex > 0 && args[0].equals("-llvm")) {
            gen.emit(System.out, null, CGen.LLVM);
            System.exit(0);
         } else if(findex > 0 && args[0].equals("-c")) {
            gen.emit(System.out, null, CGen.C);
            System.exit(0);
         }
         gen.emit(System.out, args[findex].substring(0, args[findex].length() - (args[findex].endsWith(".ve")? 3 : 5)), CGen.EXE);
         System.out.println("Code generation completed !");
      } catch(Exception e) {
         e.printStackTrace();
         System.err.println("Code emission error");
         System.exit(1);
      }
      System.exit(0);
	}

}
