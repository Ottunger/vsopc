package be.ac.ulg.vsop.analyzer;

import be.ac.ulg.vsop.lexer.Symbol;
import be.ac.ulg.vsop.parser.ASTNode;
import be.ac.ulg.vsop.parser.SymbolValue;


public class ScopeItem {
   
   public static final int FIELD = 0;
   public static final int METHOD = 1;
   public static final int CLASS = 2;
   public static final int CTYPE = 5;
   public static final int VOID = -1;
   public static final char PUBLIC = 0;
   public static final char PROTECTED = 1;
   public static final char PRIVATE = 2;
   
   public char sh;
   public int type, stype, level;
   public ASTNode userType, formals;
   
   /**
    * Creates a ScopeItem.
    * @param type Type.
    * @param userType Definition of class.
    * @param level Level at which was reg'd.
    */
   public ScopeItem(int type, ASTNode userType, int level) {
      this.type = type;
      this.stype = 0;
      this.level = level;
      this.userType = userType;
      this.formals = null;
      this.sh = ScopeItem.PUBLIC;
   }
   
   /**
    * Creates a ScopeItem.
    * @param type Type.
    * @param stype Sub type.
    * @param userType Definition of custom type if uses so.
    * @param level Level at which was reg'd.
    * @param sh Is public.
    */
   public ScopeItem(int type, int stype, ASTNode userType, int level, char sh) {
      this.type = type;
      this.stype = stype;
      this.level = level;
      this.userType = userType;
      this.formals = null;
      this.sh = sh;
   }
   
   /**
    * Creates a ScopeItem.
    * @param type Type.
    * @param stype Sub type.
    * @param userType Definition of custom type if uses so.
    * @param formals Definition of expected arguments.
    * @param level Level at which was reg'd.
    * @param sh Is public.
    */
   public ScopeItem(int type, int stype, ASTNode userType, ASTNode formals, int level, char sh) {
      this.type = type;
      this.stype = stype;
      this.level = level;
      this.userType = userType;
      this.formals = formals;
      this.sh = sh;
   }

   public static char fromSymbol(Symbol prop) {
      switch(prop.sym) {
         case SymbolValue.PLUS:
            return ScopeItem.PUBLIC;
         case SymbolValue.MINUS:
            return ScopeItem.PRIVATE;
         case SymbolValue.TILDE:
            return ScopeItem.PROTECTED;
         default:
            return ScopeItem.PUBLIC;
      }
   }

}
