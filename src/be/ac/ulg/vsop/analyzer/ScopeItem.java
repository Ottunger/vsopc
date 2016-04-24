package be.ac.ulg.vsop.analyzer;

import be.ac.ulg.vsop.parser.ASTNode;


public class ScopeItem {
   
   public static final int FIELD = 0;
   public static final int METHOD = 1;
   public static final int CLASS = 2;
   public static final int CTYPE = 5;
   public static final int VOID = -1;
   
   public boolean sh;
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
      this.sh = true;
   }
   
   /**
    * Creates a ScopeItem.
    * @param type Type.
    * @param stype Sub type.
    * @param userType Definition of custom type if uses so.
    * @param level Level at which was reg'd.
    * @param sh Is public.
    */
   public ScopeItem(int type, int stype, ASTNode userType, int level, boolean sh) {
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
   public ScopeItem(int type, int stype, ASTNode userType, ASTNode formals, int level, boolean sh) {
      this.type = type;
      this.stype = stype;
      this.level = level;
      this.userType = userType;
      this.formals = formals;
      this.sh = sh;
   }

}
