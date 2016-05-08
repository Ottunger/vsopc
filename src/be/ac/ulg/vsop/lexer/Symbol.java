package be.ac.ulg.vsop.lexer;

import be.ac.ulg.vsop.parser.SymbolValue;

public class Symbol extends java_cup.runtime.ComplexSymbolFactory.ComplexSymbol {
	
	public static final String NAMES[] = {"", "", "and", "bool", "class", "do", "else", "extends", "false", "if", "in", "int32", "isnull",
		"let", "new", "not", "string", "then", "true", "while", "lbrace", "rbrace", "lpar", "rpar", "colon", "semicolon", "comma", "plus",
		"minus", "times", "div", "pow", "equal", "lower", "lower-equal", "assign", "dot", "integer-literal", "object-identifier", "type-identifier",
		"string-literal", "null", "unit", "unit-value", "greater", "greater-equal", "or", "float", "float-literal", "switch", "lbrk", "rbrk", "tilde",
		"include", "for", "erase"};
   public int line, col;
   public Object val;
	
	/**
    * Creates a symbol.
    * @param type Type.
    */
   public Symbol(int type) {
      super(Symbol.NAMES[type], type);
      super.value = this;
   }
	
	/**
	 * Creates a symbol.
	 * @param type Type.
	 * @param line Line.
	 * @param col Column.
	 */
	public Symbol(int type, int line, int col) {
	   super(Symbol.NAMES[type], type);
		this.line = line;
		this.col = this.left = col;
		super.value = this;
	}
	
	/**
    * Creates a symbol.
    * @param type Type.
    * @param line Line.
    * @param col Column.
    * @param val Value.
    */
	public Symbol(int type, int line, int col, String val) {
	   super(Symbol.NAMES[type], type);
		this.line = line;
		this.col = this.left = col;
		this.val = val;
		super.value = this;
	}
	
	/**
    * Creates a symbol.
    * @param type Type.
    * @param line Line.
    * @param col Column.
    * @param val Value.
    */
	public Symbol(int type, int line, int col, int val) {
		super(Symbol.NAMES[type], type);
		this.line = line;
		this.col = this.left = col;
		this.val = new Integer(val);
		this.value = this;
	}
	
	/**
    * Creates a symbol.
    * @param type Type.
    * @param line Line.
    * @param col Column.
    * @param val Value.
    */
   public Symbol(int type, int line, int col, float val) {
      super(Symbol.NAMES[type], type);
      this.line = line;
      this.col = this.left = col;
      this.val = new Float(val);
      this.value = this;
   }
	
	/**
	 * Returns true if the type has a String value.
	 * @param type Type to check.
	 * @return Specified.
	 */
	private static boolean hasValString(int type) {
		if(type == SymbolValue.STRING_LITERAL)
			return true;
		else if(type == SymbolValue.OBJECT_IDENTIFIER)
			return true;
		else if(type == SymbolValue.TYPE_IDENTIFIER)
			return true;
		return false;
	}
	
	/**
    * Returns true if the type has a integer value.
    * @param type Type to check.
    * @return Specified.
    */
	private static boolean hasValInt(int type) {
		if(type == SymbolValue.INTEGER_LITERAL)
			return true;
		return false;
	}
	
	/**
	 * Returns a description of the symbol.
	 * @return The description.
	 */
	public String toString() {
		String ret = line + "," + col + "," + Symbol.NAMES[this.sym];
		if(Symbol.hasValString(this.sym))
			ret += "," + (String)this.val;
		else if(Symbol.hasValInt(this.sym))
			ret += "," + (Integer)this.val;
		return ret;
	}

}
