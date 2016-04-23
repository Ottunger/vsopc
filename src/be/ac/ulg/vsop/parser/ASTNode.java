package be.ac.ulg.vsop.parser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

import be.ac.ulg.vsop.analyzer.Scope;
import be.ac.ulg.vsop.lexer.Symbol;


public class ASTNode implements Cloneable {
   
   private ArrayList<ASTNode> children;
   public boolean ending;
   public int itype;
   public String stype;
   private Object value;
   private HashMap<String, Object> prop;
   public Scope scope; //Keys ought to be prefixed by field or method
   
   /**
    * Creates a node from a terminal type.
    * @param itype Type.
    * @param value Value.
    */
   public ASTNode(int itype, Object value) {
      children = new ArrayList<ASTNode>();
      prop = new HashMap<String, Object>();
      scope = new Scope();
      ending = true;
      this.itype = itype;
      this.stype = "";
      this.value = value;
      if(itype == SymbolValue.CLASS)
         addProp("type", value);
   }
   
   /**
    * Creates a node from a non terminal type.
    * @param stype Type.
    * @param value Value.
    */
   public ASTNode(String stype, Object value) {
      children = new ArrayList<ASTNode>();
      prop = new HashMap<String, Object>();
      scope = new Scope();
      ending = false;
      this.itype = -1;
      this.stype = stype;
      this.value = value;
   }
   
   /**
    * Clones this instance. Does not copy the scope.
    * @return Copy.
    */
   public ASTNode clone() {
      ASTNode r = new ASTNode(itype, value);
      r.stype = stype;
      r.ending = ending;
      r.prop = new HashMap<String, Object>(prop);
      for(ASTNode a : children)
         r.children.add(a.clone());
      return r;
   }
   
   /**
    * Add a child node to this node.
    * @param a Node.
    */
   public void addChild(ASTNode a) {
      children.add(a);
   }
   
   /**
    * Add as first child a node.
    * @param a Node.
    */
   public void pushChild(ASTNode a) {
      children.add(0, a);
   }
   
   /**
    * Get list of nodes.
    * @return List of children nodes.
    */
   public ArrayList<ASTNode> getChildren() {
      return children;
   }
   
   /**
    * Get the registered value.
    * @return Value.
    */
   public Object getValue() {
      return value;
   }
   
   /**
    * Register a property.
    * @param k Key.
    * @param v Value.
    */
   public void addProp(String k, Object v) {
      prop.put(k, v);
   }
   
   /**
    * Get a property.
    * @param k Key.
    * @return Value.
    */
   public Object getProp(String k) {
      return prop.get(k);
   }

   /**
    * Prints to stdout this node and its children.
    * @param All Print type info.
    */
   public void dump(boolean all) {
      ASTNode n;
      int idx;
      
      if(ending) {
         switch(itype) {
            case SymbolValue.CLASS:
               idx = 2;
               System.out.print("Class(" + children.get(0).value + ", " + children.get(1).value + ", [");
               for(; idx < children.size() && (n = children.get(idx)).stype.equals("field"); idx++) {
                     n.dump(all);
                  if(idx + 1 < children.size() && children.get(idx + 1).stype.equals("field")) {
                     System.out.print(", ");
                  }
               }
               System.out.print("], [");
               for(; idx < children.size() - 1; idx++) {
                  children.get(idx).dump(all);
                  System.out.print(", ");
               }
               if(idx < children.size()) {
                  children.get(idx).dump(all);
               }
               System.out.print("])");
               break;
            case SymbolValue.NOT:
               System.out.print("UnOp(not, ");
               children.get(0).dump(all);
               System.out.print(")");
               if(all) System.out.print(" : " + getProp("type"));
               break;
            case SymbolValue.ISNULL:
               System.out.print("UnOp(isnull, ");
               children.get(0).dump(all);
               System.out.print(")");
               if(all) System.out.print(" : " + getProp("type"));
               break;
            case SymbolValue.AND:
               System.out.print("BinOp(and, ");
               children.get(0).dump(all);
               System.out.print(", ");
               children.get(1).dump(all);
               System.out.print(")");
               if(all) System.out.print(" : " + getProp("type"));
               break;
            case SymbolValue.EQUAL:
               System.out.print("BinOp(=, ");
               children.get(0).dump(all);
               System.out.print(", ");
               children.get(1).dump(all);
               System.out.print(")");
               if(all) System.out.print(" : " + getProp("type"));
               break;
            case SymbolValue.LOWER_EQUAL:
               System.out.print("BinOp(<=, ");
               children.get(0).dump(all);
               System.out.print(", ");
               children.get(1).dump(all);
               System.out.print(")");
               if(all) System.out.print(" : " + getProp("type"));
               break;
            case SymbolValue.OR:
               System.out.print("BinOp(or, ");
               children.get(0).dump(all);
               System.out.print(", ");
               children.get(1).dump(all);
               System.out.print(")");
               if(all) System.out.print(" : " + getProp("type"));
               break;
            case SymbolValue.GREATER:
               System.out.print("BinOp(>, ");
               children.get(0).dump(all);
               System.out.print(", ");
               children.get(1).dump(all);
               System.out.print(")");
               if(all) System.out.print(" : " + getProp("type"));
               break;
            case SymbolValue.GREATER_EQUAL:
               System.out.print("BinOp(>=, ");
               children.get(0).dump(all);
               System.out.print(", ");
               children.get(1).dump(all);
               System.out.print(")");
               if(all) System.out.print(" : " + getProp("type"));
               break;
            case SymbolValue.LOWER:
               System.out.print("BinOp(<, ");
               children.get(0).dump(all);
               System.out.print(", ");
               children.get(1).dump(all);
               System.out.print(")");
               if(all) System.out.print(" : " + getProp("type"));
               break;
            case SymbolValue.PLUS:
               System.out.print("BinOp(+, ");
               children.get(0).dump(all);
               System.out.print(", ");
               children.get(1).dump(all);
               System.out.print(")");
               if(all) System.out.print(" : " + getProp("type"));
               break;
            case SymbolValue.MINUS:
               System.out.print("BinOp(-, ");
               children.get(0).dump(all);
               System.out.print(", ");
               children.get(1).dump(all);
               System.out.print(")");
               if(all) System.out.print(" : " + getProp("type"));
               break;
            case SymbolValue.TIMES:
               System.out.print("BinOp(*, ");
               children.get(0).dump(all);
               System.out.print(", ");
               children.get(1).dump(all);
               System.out.print(")");
               if(all) System.out.print(" : " + getProp("type"));
               break;
            case SymbolValue.DIV:
               System.out.print("BinOp(/, ");
               children.get(0).dump(all);
               System.out.print(", ");
               children.get(1).dump(all);
               System.out.print(")");
               if(all) System.out.print(" : " + getProp("type"));
               break;
            case SymbolValue.POW:
               System.out.print("BinOp(^, ");
               children.get(0).dump(all);
               System.out.print(", ");
               children.get(1).dump(all);
               System.out.print(")");
               if(all) System.out.print(" : " + getProp("type"));
               break;
            case SymbolValue.OBJECT_IDENTIFIER:
            case SymbolValue.STRING_LITERAL:
            case SymbolValue.INTEGER_LITERAL:
            case SymbolValue.FLOAT_LITERAL:
               System.out.print(value);
               if(all) System.out.print(" : " + getProp("type"));
               break;
            case SymbolValue.TRUE:
               System.out.print("true");
               if(all) System.out.print(" : " + getProp("type"));
               break;
            case SymbolValue.FALSE:
               System.out.print("false");
               if(all) System.out.print(" : " + getProp("type"));
               break;
            case SymbolValue.UNIT_VALUE:
               System.out.print("()");
               if(all) System.out.print(" : " + getProp("type"));
               break;
            case SymbolValue.NULL:
               System.out.print("null");
               if(all) System.out.print(" : " + getProp("type"));
               break;
            case SymbolValue.NEW:
               System.out.print("New(" + children.get(0).value + ")");
               if(all) System.out.print(" : " + getProp("type"));
               break;
            case SymbolValue.ASSIGN:
               children.get(0).dump(all);
               break;
            default:
               break;
         }
      } else {
         switch(stype) {
            case "field":
               System.out.print("Field(" + children.get(0).value + ", " + ASTNode.typeValue(children.get(1)));
               if(children.size() > 2) {
                  System.out.print(", ");
                  children.get(2).dump(all);
               }
               System.out.print(")");
               break;
            case "method":
               idx = 1;
               System.out.print("Method(" + children.get(0).value);
               if(children.get(1).stype.equals("formals")) {
                  idx = 2;
                  System.out.print(", ");
                  children.get(1).dump(all);
               } else {
                  System.out.print(", []");
               }
               System.out.print(", " + ASTNode.typeValue(children.get(idx)) + ", ");
               children.get(idx + 1).dump(all);
               System.out.print(")");
               break;
            case "program":
            case "formals":
            case "args":
               System.out.print("[");
               children.get(0).dump(all);
               for(int i = 1; i < children.size(); i++) {
                  System.out.print(", ");
                  children.get(i).dump(all);
               }
               System.out.print("]");
               break;
            case "formal":
               System.out.print(children.get(0).value + " : " + ASTNode.typeValue(children.get(1)));
               break;
            case "block":
               if(children.size() > 1) {
                  System.out.print("Block([");
                  children.get(0).dump(all);
                  for(int i = 1; i < children.size(); i++) {
                     System.out.print(", ");
                     children.get(i).dump(all);
                  }
                  System.out.print("])");
                  if(all) System.out.print(" : " + getProp("type"));
               } else
                  children.get(0).dump(all);
               break;
            case "if":
               System.out.print("If(");
               children.get(0).dump(all);
               System.out.print(", ");
               children.get(1).dump(all);
               if(children.size() > 2) {
                  System.out.print(", ");
                  children.get(2).dump(all);
               }
               System.out.print(")");
               if(all) System.out.print(" : " + getProp("type"));
               break;
            case "while":
               System.out.print("While(");
               children.get(0).dump(all);
               System.out.print(", ");
               children.get(1).dump(all);
               System.out.print(")");
               break;
            case "let":
               idx = 2;
               System.out.print("Let(" + children.get(0).value + ", " + ASTNode.typeValue(children.get(1)));
               if(children.size() > 3) {
                  idx = 3;
                  System.out.print(", ");
                  children.get(2).dump(all);
               }
               System.out.print(", ");
               children.get(idx).dump(all);
               System.out.print(")");
               if(all) System.out.print(" : " + getProp("type"));
               break;
            case "assign":
               System.out.print("Assign(" + children.get(0).value + ", ");
               children.get(1).dump(all);
               System.out.print(")");
               if(all) System.out.print(" : " + getProp("type"));
               break;
            case "uminus":
               System.out.print("UnOp(-, ");
               children.get(0).dump(all);
               System.out.print(")");
               if(all) System.out.print(" : " + getProp("type"));
               break;
            case "call":
               System.out.print("Call(");
               children.get(0).dump(all);
               System.out.print(", " + children.get(1).value + ", ");
               if(children.size() > 2) {
                  children.get(2).dump(all);
               } else {
                  System.out.print("[]");
               }
               System.out.print(")");
               if(all) System.out.print(" : " + getProp("type"));
               break;
            default:
               break;
         }
      }
   }
   
   /**
    * Find displayable string for identifier.
    * @param a Node.
    * @return Identifier.
    */
   public static String typeValue(ASTNode a) {
      if(!a.stype.equals(""))
         return a.stype;
      if(a.itype == SymbolValue.TYPE_IDENTIFIER)
         return a.value.toString();
      else
         return Symbol.NAMES[a.itype];
   }
   
   /**
    * Orders this node if is a class.
    */
   public void shuffleClass() {
      if(itype != SymbolValue.CLASS)
         return;
      ASTNode t = children.remove(children.size() - 1);
      ASTNode e = children.remove(children.size() - 1);
      Collections.sort(children, new Comparator<ASTNode>() {
         @Override
         public int compare(ASTNode a, ASTNode b) {
            if(a.stype.equals("field") && b.stype.equals("method"))
               return -1;
            if(a.stype.equals("method") && b.stype.equals("field"))
               return 1;
            return 0;
         }
      });
      children.add(0, e);
      children.add(0, t);
   }

}
