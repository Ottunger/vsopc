package be.ac.ulg.vsop.analyzer;

import java.util.HashMap;

import be.ac.ulg.vsop.parser.ASTNode;
import be.ac.ulg.vsop.parser.SymbolValue;


public class Analyzer {
   
   public static final String EMPTY = "vsopEMPTY";
   private HashMap<String, HashMap<String, ScopeItem>> prim;
   private HashMap<String, String> ext;
   

   /**
    * Creates an analyzer.
    */
   public Analyzer() {
      ASTNode fms, tmp;
      
      ext = new HashMap<String, String>();
      prim = new HashMap<String, HashMap<String, ScopeItem>>();
      ext.put("Object", Analyzer.EMPTY);
      ext.put("String", Analyzer.EMPTY);
      ext.put("int32", Analyzer.EMPTY);
      ext.put("bool", Analyzer.EMPTY);
      ext.put("unit", Analyzer.EMPTY);
      ext.put("IO", "Object");
      //There is one entry in $prim per recorded class, and it only registers their methods.
      //This table is only used at first to ensure basic knowledge of everything.
      prim.put("Object", new HashMap<String, ScopeItem>());
      prim.put("String", new HashMap<String, ScopeItem>());
      prim.put("int32", new HashMap<String, ScopeItem>());
      prim.put("bool", new HashMap<String, ScopeItem>());
      prim.put("unit", new HashMap<String, ScopeItem>());
      prim.put("IO", new HashMap<String, ScopeItem>());
      //Rgister the methods of the well known IO class
      //Method print
      fms = new ASTNode("formals", "__dummy__");
      tmp = new ASTNode("formal", "__dummy__");
      tmp.addProp("type", "String");
      fms.getChildren().add(0, tmp);
      tmp = new ASTNode("block", "__dummy__");
      tmp.addProp("type", "IO");
      prim.get("IO").put("print", new ScopeItem(ScopeItem.METHOD, SymbolValue.TYPE_IDENTIFIER, tmp, fms, 3));
      //Method printInt
      fms = new ASTNode("formals", "__dummy__");
      tmp = new ASTNode("formal", "__dummy__");
      tmp.addProp("type", "int32");
      fms.getChildren().add(0, tmp);
      tmp = new ASTNode("block", "__dummy__");
      tmp.addProp("type", "IO");
      prim.get("IO").put("printInt", new ScopeItem(ScopeItem.METHOD, SymbolValue.TYPE_IDENTIFIER, tmp, fms, 3));
   }
   
   /**
    * Returns the extends.
    * @return Extensions.
    */
   public HashMap<String, String> getExt() {
      return ext;
   }
   
   /**
    * Registers the scopes, then performs checks.
    * @param root The root of program.
    * @throws Exception
    */
   public void regScope(ASTNode root) throws Exception {
      ScopeItem si;
      root.scope.put(ScopeItem.CLASS, "Object", new ScopeItem(ScopeItem.CLASS, new ASTNode(SymbolValue.CLASS, "Object"), 0));
      root.scope.put(ScopeItem.CLASS, "String", new ScopeItem(ScopeItem.CLASS, new ASTNode(SymbolValue.CLASS, "String"), 0));
      root.scope.put(ScopeItem.CLASS, "int32", new ScopeItem(ScopeItem.CLASS, new ASTNode(SymbolValue.CLASS, "int32"), 0));
      root.scope.put(ScopeItem.CLASS, "bool", new ScopeItem(ScopeItem.CLASS, new ASTNode(SymbolValue.CLASS, "bool"), 0));
      root.scope.put(ScopeItem.CLASS, "unit", new ScopeItem(ScopeItem.CLASS, new ASTNode(SymbolValue.CLASS, "unit"), 0));
      root.scope.put(ScopeItem.CLASS, "IO", new ScopeItem(ScopeItem.CLASS, new ASTNode(SymbolValue.CLASS, "IO"), 0));
      //Register all classes top domain
      regClasses(root, true);
      //Register scopes
      regScope(root, null, 1);
      //Check we have a main
      if((si = root.scope.get(ScopeItem.CLASS, "Main")) == null)
         throw new Exception("1:1: semantics error no Main class");
      if((si = si.userType.scope.get(ScopeItem.METHOD, "main")) == null)
         throw new Exception("1:1: semantics error no main method");
      if(si.formals != null || !getNodeType(si.userType, -1, false).equals("int32"))
         throw new Exception("1:1: semantics error bad main signature");
      //Check types
      checkTypes(root, root.scope);
   }
   
   /**
    * Reads the node or one of its children type.
    * @param root The node.
    * @param index -1 for itself, otherwise index of child.
    * @param fonly Check only in fields.
    * @return Type.
    */
   private String getNodeType(ASTNode root, int index, boolean fonly) throws Exception {
      ASTNode node = (index == -1)? root: root.getChildren().get(index);
      ScopeItem ret;
      if(node.itype == SymbolValue.OBJECT_IDENTIFIER) {
         ret = root.scope.get(ScopeItem.FIELD, node.getValue().toString());
         if(ret == null && fonly)
            throw new Exception(root.getProp("line") + ":" + root.getProp("col") + ": semantics error used symbol " + node.getValue().toString() +
                     " not the way it was defined");
         if(ret == null)
            ret = root.scope.get(ScopeItem.METHOD, node.getValue().toString());
         if(ret == null)
            throw new Exception(root.getProp("line") + ":" + root.getProp("col") + ": semantics error used symbol " + node.getValue().toString() +
                     " not the way it was defined");
         return ASTNode.typeValue(ret.userType);
      } else {
         return node.getProp("type").toString();
      }
   }
   
   /**
    * Check whether a type is the same or the extension of another.
    * @param ext Extension map.
    * @param a First type that could be child.
    * @param b Second type.
    * @return True if condition is met.
    */
   public static boolean isSameOrChild(HashMap<String, String> ext, String a, String b) {
      if(a.equals(b))
         return true;
      while(!((a = ext.get(a)).equals(Analyzer.EMPTY))) {
         if(a.equals(b))
            return true;
      }
      return false;
   }
   
   /**
    * Checks that the operations involve only ok types, and that a call to a method is indeed to one that exists.
    * @param root The root of program.
    * @param scope The root scope.
    * @throws Exception
    */
   private void checkTypes(ASTNode root, Scope scope) throws Exception {
      String type;
      ScopeItem s, t;
      HashMap<String, ScopeItem> meths;
      
      for(ASTNode r : root.getChildren()) {
         checkTypes(r, scope);
      }
      
      if(root.getProp("line") == null && root.getChildren().size() > 0) {
         root.addProp("line", root.getChildren().get(0).getProp("line"));
         root.addProp("col", root.getChildren().get(0).getProp("col"));
      }
      
      if(root.ending) {
         switch(root.itype) {
            case SymbolValue.NOT:
               root.addProp("type", getNodeType(root, 0, true));
               if(!getNodeType(root, 0, true).equals("int32") && !getNodeType(root, 0, true).equals("bool"))
                  throw new Exception(root.getProp("line") + ":" + root.getProp("col") + ": semantics error cannot use '!' on this type");
               break;
            case SymbolValue.EQUAL:
               root.addProp("type", "bool");
               meths = prim.get(getNodeType(root, 0, true));
               /* Basic type if no registered method for this type */
               if(meths != null && meths.size() == 0 && !getNodeType(root, 0, true).equals(getNodeType(root, 1, true)))
                  throw new Exception(root.getProp("line") + ":" + root.getProp("col") + ": semantics error cannot use '=' on different primitive types");
               break;
            case SymbolValue.AND:
               root.addProp("type", "int32");
               if(!getNodeType(root, 0, true).equals("int32") && !getNodeType(root, 0, true).equals("bool"))
                  throw new Exception(root.getProp("line") + ":" + root.getProp("col") + ": semantics error cannot use and on something not int32 or bool");
               if(!getNodeType(root, 0, true).equals(getNodeType(root, 1, true)))
                  throw new Exception(root.getProp("line") + ":" + root.getProp("col") + ": semantics error cannot use and on different types");
               break;
            case SymbolValue.LOWER_EQUAL:
            case SymbolValue.LOWER:
               root.addProp("type", "bool");
               if(!getNodeType(root, 0, true).equals("int32") || !getNodeType(root, 1, true).equals("int32"))
                  throw new Exception(root.getProp("line") + ":" + root.getProp("col") + ": semantics error cannot use binary operator on not both int32 types");
               break;
            case SymbolValue.PLUS:
            case SymbolValue.MINUS:
            case SymbolValue.TIMES:
            case SymbolValue.DIV:
            case SymbolValue.POW:
               root.addProp("type", "int32");
               if(!getNodeType(root, 0, true).equals("int32") || !getNodeType(root, 1, true).equals("int32"))
                  throw new Exception(root.getProp("line") + ":" + root.getProp("col") + ": semantics error cannot use binary operator on not both int32 types");
               break;
            case SymbolValue.ISNULL:
               root.addProp("type", "bool");
               break;
            case SymbolValue.ASSIGN:
               root.addProp("type", getNodeType(root, 0, true));
               break;
            case SymbolValue.NEW:
               root.addProp("type", root.getChildren().get(0).getValue().toString());
               break;
            default:
               break;
         }
      } else {
         switch(root.stype) {
            case "call":
               type = getNodeType(root, 0, true);
               do {
                  //Get scope of callee
                  s = scope.get(ScopeItem.CLASS, type);
                  //Check that object type has methods
                  if(s == null || s.type != ScopeItem.CLASS)
                     throw new Exception(root.getProp("line") + ":" + root.getProp("col") + ": semantics error cannot call a method on type " + getNodeType(root, 0, true));
                  //Check that this method is registered
                  if((t = prim.get(type).get(root.getChildren().get(1).getValue().toString())) != null)
                     break;
               } while(!(type = ext.get(type)).equals(Analyzer.EMPTY));
               //Check that this method is registered
               if(type.equals(Analyzer.EMPTY))
                  throw new Exception(root.getProp("line") + ":" + root.getProp("col") + ": semantics error cannot call method " +
                           root.getChildren().get(1).getValue().toString() + " on type " + getNodeType(root, 0, true));
               //Then our type is the one of the method
               root.addProp("type", getNodeType(t.userType, -1, false));
               //Check ok arguments
               if(((root.getChildren().size() == 2) != (t.formals == null)) || (root.getChildren().size() > 2 && t.formals != null && root.getChildren().get(2).getChildren().size() != t.formals.getChildren().size())) {
                  throw new Exception(root.getProp("line") + ":" + root.getProp("col") + ": semantics error wrong number of arguments to method " + root.getChildren().get(1).getValue());
               } else if(root.getChildren().size() > 2) {
                  for(int i = 0; i < root.getChildren().get(2).getChildren().size(); i++) {
                     String arg = getNodeType(root.getChildren().get(2), i, true);
                     String formal = getNodeType(t.formals, i, true);
                     if(!Analyzer.isSameOrChild(ext, arg, formal))
                        throw new Exception(root.getChildren().get(2).getChildren().get(i).getProp("line") + ":" + root.getChildren().get(2).getChildren().get(i).getProp("col") +
                                 ": semantics error expected type " + formal + " but got " + arg + " for argument " + (i+1) + " of method " + root.getChildren().get(1).getValue());
                  }
               }
               break;
            case "field":
               //Here we can check no override of field
               type = root.scope.get(ScopeItem.FIELD, "self").userType.getProp("type").toString();
               while(!(type = ext.get(type)).equals(Analyzer.EMPTY)) {
                  //If such a field already exists above...
                  if(scope.get(ScopeItem.CLASS, type).userType.scope.get(ScopeItem.FIELD, root.getChildren().get(0).getValue().toString()) != null)
                     throw new Exception(root.getProp("line") + ":" + root.getProp("col") + ": semantics error cannot redefine symbol '" + root.getChildren().get(0).getValue().toString() + "'here");
               }
               root.addProp("type", getNodeType(root, 0, true));
               if(ext.get(getNodeType(root, 0, true)) == null)
                  throw new Exception(root.getProp("line") + ":" + root.getProp("col") + ": semantics error unknown type " + getNodeType(root, 0, true));
               if(root.getChildren().size() > 2 && !getNodeType(root, 0, true).equals(getNodeType(root, 2, true)))
                  throw new Exception(root.getProp("line") + ":" + root.getProp("col") + ": semantics error cannot assign " + 
                           getNodeType(root, 2, true) + " to " + getNodeType(root, 0, true));
               break;
            case "assign":
               root.addProp("type", getNodeType(root, 0, true));
               //Check that no assign to self
               if(root.getChildren().get(0).getValue().toString().equals("self"))
                  throw new Exception(root.getProp("line") + ":" + root.getProp("col") + ": semantics error cannot assign anything to 'self'");
               //Check that assign a same typed-value or a parent-typed value
               if(!Analyzer.isSameOrChild(ext, getNodeType(root, 1, true), getNodeType(root, 0, true)))
                  throw new Exception(root.getProp("line") + ":" + root.getProp("col") + ": semantics error cannot assign " + 
                           getNodeType(root, 1, true) + " to " + getNodeType(root, 0, true));
               break;
            case "method":
               if(ext.get(getNodeType(root, -1, false)) == null)
                  throw new Exception(root.getProp("line") + ":" + root.getProp("col") + ": semantics error unknown type " + getNodeType(root, -1, false));
               //Currently, when a while is issued as last expression
               if(getNodeType(root, root.getChildren().size() - 1, true).equals(Analyzer.EMPTY))
                  throw new Exception(root.getProp("line") + ":" + root.getProp("col") + ": semantics error method cannot return anything as is");
               if(!Analyzer.isSameOrChild(ext, getNodeType(root, root.getChildren().size() - 1, true), getNodeType(root, -1, false)))
                  throw new Exception(root.getProp("line") + ":" + root.getProp("col") + ": semantics error method type is " + 
                           getNodeType(root, -1, false) + " but got " + getNodeType(root, root.getChildren().size() - 1, true));
            case "block":
               root.addProp("type", getNodeType(root, root.getChildren().size() - 1, true));
               break;
            case "if":
               if(!getNodeType(root, 0, true).equals("bool"))
                  throw new Exception(root.getProp("line") + ":" + root.getProp("col") + ": semantics error if condition must be bool");
               root.addProp("type", getNodeType(root, 1, true));
               if(root.getChildren().size() > 2) {
                  if(getNodeType(root, 1, true).equals("unit") || getNodeType(root, 2, true).equals("unit"))
                     root.addProp("type", "unit");
                  else {
                     if(Analyzer.isSameOrChild(ext, getNodeType(root, 1, true), getNodeType(root, 2, true)))
                        root.addProp("type", getNodeType(root, 2, true));
                     else if(Analyzer.isSameOrChild(ext, getNodeType(root, 2, true), getNodeType(root, 1, true)))
                        root.addProp("type", getNodeType(root, 1, true));
                     else
                        throw new Exception(root.getProp("line") + ":" + root.getProp("col") + ": semantics error if branches are typed as " + 
                                 getNodeType(root, 1, true) + " and " + getNodeType(root, 2, true));
                  }
               }
               break;
            case "while":
               root.addProp("type", Analyzer.EMPTY);
               if(!getNodeType(root, 0, true).equals("bool"))
                  throw new Exception(root.getProp("line") + ":" + root.getProp("col") + ": semantics error while condition must be bool");
               break;
            case "let":
               if(ext.get(getNodeType(root, 1, false)) == null)
                  throw new Exception(root.getProp("line") + ":" + root.getProp("col") + ": semantics error unknown type " + getNodeType(root, 1, false));
               //Check in the assign part of the "let"
               if(root.getChildren().size() > 3 && !Analyzer.isSameOrChild(ext, getNodeType(root, 2, true), getNodeType(root, 1, false)))
                  throw new Exception(root.getProp("line") + ":" + root.getProp("col") + ": semantics error cannot assign " + 
                           getNodeType(root, 2, true) + " to " + getNodeType(root, 1, false));
               root.addProp("type", getNodeType(root, root.getChildren().size() - 1, true));
               break;
            case "uminus":
               root.addProp("type", getNodeType(root, 0, true));
               break;
            default:
               break;
         }
      }
   }
   
   /**
    * Registers the scope items.
    * @param root The root of the program.
    * @param parent Parent of the current node.
    * @param level Level of scope.
    * @throws Exception
    */
   private void regScope(ASTNode root, ASTNode parent, int level) throws Exception {
      ScopeItem si;
      if(parent != null)
         root.scope.setParent(parent.scope);
      
      if(root.ending) {
         switch(root.itype) {
            case SymbolValue.CLASS:
               root.addProp("type", root.getChildren().get(0).getValue().toString());
               //Add to own scope and general scope
               root.scope.putAbove(ScopeItem.CLASS, root.getChildren().get(0).getValue().toString(), new ScopeItem(ScopeItem.CLASS, root, level), 1);
               //Check that the super class is known
               if(ext.get(root.getChildren().get(1).getValue().toString()) == null)
                  throw new Exception(root.getProp("line") + ":" + root.getProp("col") + ": semantics error unknown super class " + root.getChildren().get(1).getValue().toString());
               root.getChildren().get(0).addProp("type", root.getChildren().get(0).getValue().toString());
               //Put "self" everywhere in this class
               root.scope.put(ScopeItem.FIELD, "self", new ScopeItem(ScopeItem.FIELD, SymbolValue.OBJECT_IDENTIFIER, root.getChildren().get(0), -1));
               //Check for extends-loop
               if(Analyzer.isSameOrChild(ext, root.getChildren().get(1).getValue().toString(), root.getChildren().get(0).getValue().toString()))
                  throw new Exception(root.getProp("line") + ":" + root.getProp("col") + ": semantics error class " + root.getChildren().get(0).getValue().toString() + " defines an extends-loop");
               break;
            case SymbolValue.OBJECT_IDENTIFIER:
               //Set the type of this node from the scope
               si = root.scope.get(ScopeItem.FIELD, root.getValue().toString());
               if(si != null)
                  root.addProp("type", getNodeType(si.userType, -1, false));
               //Skip a call
               if(parent.stype.equals("call"))
                  break;
               if(root.scope.get(ScopeItem.FIELD, root.getValue().toString()) == null && root.scope.get(ScopeItem.METHOD, root.getValue().toString()) == null &&
                        root.scope.get(ScopeItem.CLASS, root.getValue().toString()) == null)
                  throw new Exception(root.getProp("line") + ":" + root.getProp("col") + ": semantics error undefined used object " + root.getValue().toString());
               break;
            default:
               break;
         }
      } else {
         if(root.stype.equals("method")) {
            //Check override higher only
            si = prim.get(parent.getChildren().get(0).getValue().toString()).get(root.getChildren().get(0).getValue().toString());
            if(differentMethodAbove(root, root.getChildren().get(0).getValue().toString(), parent.getChildren().get(0).getValue().toString()))
               throw new Exception(root.getProp("line") + ":" + root.getProp("col") + ": semantics error cannot redefine symbol '" +
                        root.getChildren().get(0).getValue().toString() + "' here");
            si.level = level;
            root.scope.putAbove(ScopeItem.METHOD, root.getChildren().get(0).getValue().toString(), si, 1);
            if(root.getChildren().get(root.getChildren().get(1).stype.equals("formals")? 2 : 1).itype == SymbolValue.TYPE_IDENTIFIER &&
                     root.scope.get(ScopeItem.CLASS, root.getChildren().get(root.getChildren().get(1).stype.equals("formals")? 2 : 1).getValue().toString()) == null)
               throw new Exception(root.getProp("line") + ":" + root.getProp("col") + ": semantics error unknown return type " +
                        root.getChildren().get(root.getChildren().get(1).stype.equals("formals")? 2 : 1).getValue() + "for method");
         } else {
            if(root.stype.equals("field")) {
               //Check override higher only
               si = root.scope.get(ScopeItem.FIELD, root.getChildren().get(0).getValue().toString());
               if(si != null && si.level == level)
                  throw new Exception(root.getProp("line") + ":" + root.getProp("col") + ": semantics error cannot redefine symbol " +
                           root.getChildren().get(0).getValue().toString() + " here");
            }
            if(root.stype.equals("formal") || root.stype.equals("field") || root.stype.equals("let")) {
               //Check do not override 'self'
               si = root.scope.get(ScopeItem.FIELD, root.getChildren().get(0).getValue().toString());
               if(si != null && si.level == -1)
                  throw new Exception(root.getProp("line") + ":" + root.getProp("col") + ": semantics error can never define symbol 'self'");
            }
            switch(root.stype) {
               case "formal":
                  root.scope.putAbove(ScopeItem.FIELD, root.getChildren().get(0).getValue().toString(),
                           new ScopeItem(ScopeItem.FIELD, root.getChildren().get(1).itype, root.getChildren().get(1), level), 2);
                  //Register the type because this is not acquired bottom-up while cross checking
                  root.addProp("type", getNodeType(root, 1, false));
                  if(ext.get(getNodeType(root, 1, false)) == null)
                     throw new Exception(root.getProp("line") + ":" + root.getProp("col") + ": semantics error unknown type " + getNodeType(root, 1, false));
                  break;
               case "field":
                  root.scope.putAbove(ScopeItem.FIELD, root.getChildren().get(0).getValue().toString(),
                           new ScopeItem(ScopeItem.FIELD, root.getChildren().get(1).itype, root.getChildren().get(1), level), 1);
                  break;
               case "let":
                  root.scope.put(ScopeItem.FIELD, root.getChildren().get(0).getValue().toString(),
                           new ScopeItem(ScopeItem.FIELD, root.getChildren().get(1).itype, root.getChildren().get(1), level));
                  if(root.getChildren().get(1).itype == SymbolValue.TYPE_IDENTIFIER &&
                           ext.get(root.getChildren().get(1).getValue().toString()) == null)
                     throw new Exception(root.getProp("line") + ":" + root.getProp("col") + ": semantics error unknown type " +
                           root.getChildren().get(1).getValue() + " for declared variable");
                  break;
               default:
                  break;
            }
         }
      }
      
      for(ASTNode r : root.getChildren()) {
         regScope(r, root, level + 1);
      }
   }
   
   /**
    * Registers the classes at top level.
    * @param root The root of the program.
    * @param pass Go further.
    * @throws Exception
    */
   private void regClasses(ASTNode root, boolean pass) throws Exception {
      if(root.ending) {
         switch(root.itype) {
            case SymbolValue.CLASS:
               //Check that this class is not known already
               if(ext.get(root.getChildren().get(0).getValue().toString()) != null)
                  throw new Exception(root.getProp("line") + ":" + root.getProp("col") + ": semantics error class " + root.getChildren().get(0).getValue().toString() + " has been defined several times");
               ext.put(root.getChildren().get(0).getValue().toString(), root.getChildren().get(1).getValue().toString());
               //Cannot extend built-in class String
               if(root.getChildren().get(1).getValue().toString().equals("String") || root.getChildren().get(1).getValue().toString().equals("int32") ||
                        root.getChildren().get(1).getValue().toString().equals("bool") || root.getChildren().get(1).getValue().toString().equals("unit"))
                  throw new Exception(root.getProp("line") + ":" + root.getProp("col") + ": semantics error class " + root.getChildren().get(0).getValue().toString() + " cannot extend built-in class or value");
               prim.put(root.getChildren().get(0).getValue().toString(), new HashMap<String, ScopeItem>());
               break;
            default:
               break;
         }
      }
      
      if(pass) {
         for(ASTNode r : root.getChildren()) {
            regClasses(r, false);
         }
      } else {
         for(ASTNode r : root.getChildren()) {
            regMethods(r, root);
         }
      }
   }
   
   /**
    * Registers the methods at class level.
    * @param root The root of a method.
    * @param scope The scope of the class, to add it.
    * @throws Exception
    */
   private void regMethods(ASTNode root, ASTNode scope) throws Exception {
      boolean has;
      ScopeItem si;
      HashMap<String, ScopeItem> meths;
      
      if(root.stype.equals("method")) {
         meths = prim.get(scope.getChildren().get(0).getValue().toString());
         has = root.getChildren().get(1).stype.equals("formals");
         si = new ScopeItem(ScopeItem.METHOD,
               has? root.getChildren().get(2).itype : root.getChildren().get(1).itype,
               has? root.getChildren().get(2) : root.getChildren().get(1),
               has? root.getChildren().get(1) : null, 3);
         meths.put(root.getChildren().get(0).getValue().toString(), si);
      }
   }
   
   /**
    * True if method override a non ocnformant one.
    * @param root ASTNode where method is defined.
    * @param m Method name.
    * @param n Name of the class were defined.
    */
   private boolean differentMethodAbove(ASTNode root, String m, String n) throws Exception {
      boolean has;
      ScopeItem t;
      
      while(!n.equals(Analyzer.EMPTY)) {
         //Get name of upper class
         n = ext.get(n);
         if(n.equals(Analyzer.EMPTY))
            return false;
         //Check that method exists in this upper class
         t = prim.get(n).get(m);
         if(t != null) {
            has = root.getChildren().size() > 3;
            //Check same return & same formals at both ends, otherwise bad override
            if(!getNodeType(root, has? 2 : 1, true).equals(getNodeType(t.userType, -1, true)))
               return false;
            if(((root.getChildren().size() == 3) != (t.formals == null)) || (has && t.formals != null && root.getChildren().get(2).getChildren().size() != t.formals.getChildren().size())) {
               return true;
            } else if(has) {
               for(int i = 0; i < root.getChildren().get(2).getChildren().size(); i++) {
                  String arg = getNodeType(root.getChildren().get(2), i, true);
                  String formal = getNodeType(t.formals, i, true);
                  if(!Analyzer.isSameOrChild(ext, arg, formal))
                     return true;
               }
            }
         }
      }
      
      return false;      
   }

}
