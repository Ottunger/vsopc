package be.ac.ulg.vsop.lexer;

import java.io.FileReader;
import java.util.ArrayList;

import be.ac.ulg.vsop.parser.SymbolValue;

public class Lexer {
	
	private boolean failed;
	public String name;
	private FileReader file;
	private ArrayList<Symbol> symbs;
	
	/**
	 * Creates the Lexer.
	 * @param in Opened file.
	 * @param name Name of this file.
	 */
	public Lexer(FileReader in, String name) {
		failed = true;
		file = in;
		this.name = name;
		symbs = new ArrayList<Symbol>();
	}
	
	/**
	 * Runs the parsing step and saves array of tokens.
	 */
	public void parse() {
		boolean ok = true, esc;
		Symbol s = null;
		
		if(file == null)
			return;
		VSOPParser p = new VSOPParser(file);
		do {
			esc = false;
			try {
				s = p.yylex();
			} catch(Exception e) {
				esc = true;
				ok = false;
				System.err.println(name + ":" + tryGuess(e.getMessage()));
			}
			if(s == null)
				continue;
			symbs.add(s);
		} while(s != null || esc == true);
		if(p.yystate() == VSOPParser.COMMENT) {
			System.err.println(name + ":" + (p.rcomment.pop() + 1) + ":" + (p.ccomment.pop() + 1) + ": lexical error file ends with comment still open.");
			return;
		} else if(p.yystate() == VSOPParser.STRING || p.yystate() == VSOPParser.BYTESTRING) {
			System.err.println(name + ":" + (p.rbegin + 1) + ":" + (p.cbegin + 1) + ": lexical error file ends with string still open.");
			return;
		}
		if(ok) {
		   symbs.add(new Symbol(SymbolValue.EOF, 0, 0));
			failed = false;
		}
	}
	
	/**
	 * Tries to guess what parsing error happened based on resulting string.
	 * @param err The caught string.
	 * @return Displayable error.
	 */
	private String tryGuess(String err) {
		String ret[] = err.split(":");
		for(int i = 3; i < ret.length; i++)
			ret[2] += ret[i];
		
		//25 is message "is not a valid VSOP syntax" length
		if(ret[2].endsWith("is not a valid VSOP syntax") && ret[2].startsWith(" lexical error <0"))
			ret[2] = ret[2].substring(0, ret[2].length() - 25) + "is not a valid number notation.";
		
		return ret[0] + ":" + ret[1] + ":" + ret[2];
	}
	
	/**
	 * Checks whether the parsing has been done and is valid.
	 * @return True if OK.
	 */
	public boolean isParseFailed() {
		return failed;
	}
	
	/**
    * Gets all saved tokens.
    */
   public ArrayList<Symbol> getTokens() {
      return symbs;
   }
	
	/**
	 * Prints all saved tokens to standard output.
	 */
	public void dumpTokens() {
		for(Symbol s : symbs)
		   if(s.sym != SymbolValue.EOF)
		      System.out.println(s);
	}

}
