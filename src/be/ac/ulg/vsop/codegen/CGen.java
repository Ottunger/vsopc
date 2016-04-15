package be.ac.ulg.vsop.codegen;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import be.ac.ulg.vsop.analyzer.Analyzer;
import be.ac.ulg.vsop.analyzer.ScopeItem;
import be.ac.ulg.vsop.parser.ASTNode;
import be.ac.ulg.vsop.parser.SymbolValue;


public class CGen {
   
   private int where;
   private ASTNode ast;
   private HashMap<String, String> ext, strs;
   StringBuilder sb;
   private Set<String> classes;
   
   /**
    * Build a C generator.
    * @param root AST.
    * @param ext Inheritances.
    */
   public CGen(ASTNode root, HashMap<String, String> ext) {
      ast = root;
      this.ext = ext;
   }
   
   /**
    * Emit C to a stream.
    * @param o PrintWriter to stream.
    */
   public void emit(OutputStream o) {
      classes = ext.keySet();
      classes.remove("Object");
      classes.remove("String");
      classes.remove("int32");
      classes.remove("bool");
      classes.remove("unit");
      
      strs = new HashMap<String, String>();
      sb = new StringBuilder();
      //Create pow function, for pow in VSOP
      sb.append("int __pow(int a, int b) {if(b == 0) return 1; if(b == 1) return a; int rec = __pow(a, b/2); "
               + "if(b % 2) return a * rec * rec; return rec * rec;} ");
      where = sb.length();
      
      //From a first pass, generate the structures that classes rely are
      while(classes.size() > 0)
         genStructures(ast, true, false);
      //From a second pass, generate the methods that are functions that have a pointer *self
      //Second pass is actually deeper in first pass
      genStructures(ast, true, true);
      
      //Create global strings constants.
      for(Map.Entry<String, String> ent : strs.entrySet()) {
         sb.insert(0, ent.getKey() + " = \"" + ent.getValue() + "\"; ");
      }
      
      try {
         o.write(sb.toString().getBytes());
      } catch(IOException e) {
         System.err.println("Could not emit C code");
      }
   }
   
   /**
    * Generate structures into builder.
    * @param root AST.
    * @param pass Reached classes.
    * @param second Second pass.
    */
   private void genStructures(ASTNode root, boolean pass, boolean second) {
      String type = "";
      ASTNode vclass;
      ArrayList<ArrayList<String>> fields, types;
      ArrayList<String> methods;
      ArrayList<CSignature> sigs;
      
      if(!second && root.ending) {
         switch(root.itype) {
            case SymbolValue.CLASS:
               //Create a structure for this type if all parent types already defined
               type = root.getChildren().get(0).getValue().toString();
               if(!classes.contains(ext.get(type))) {
                  classes.remove(type);
                  //Prepare vtable definition
                  methods = new ArrayList<String>();
                  sigs = new ArrayList<CSignature>();
                  do {
                     vclass = ast.scope.get(ScopeItem.CLASS, type).userType;
                     for(ASTNode a : root.getChildren().get(2).getChildren()) {
                        if(type.equals(root.getChildren().get(0).getValue().toString())) {
                           tryAddSig(a, root.getChildren().get(0).getValue().toString(), methods, sigs, true);
                        } else {
                           tryAddSig(a, root.getChildren().get(0).getValue().toString(), methods, sigs, false);
                        }
                     }
                  } while(!(type = ext.get(type)).equals(Analyzer.EMPTY));
                  type = root.getChildren().get(0).getValue().toString();
                  //vtable definition and one static instance
                  sb.append("typedef struct " + type + "_vtable {");
                  //Write the fields into the C code
                  for(int i = 0; i < methods.size(); i++) {
                     sb.append(sigs.get(i).ret + " (" + methods.get(i) + "*)(");
                     for(int j = 0; j < sigs.get(i).pnames.size(); j++) {
                        sb.append(sigs.get(i).ptypes.get(j) + ",");
                     }
                     if(sigs.get(i).pnames.size() > 0)
                        sb.deleteCharAt(sb.length() - 1);
                     sb.append("); ");
                  }
                  sb.append("} " + type + "_vtable; ");
                  //Create the static instance
                  sb.append(type + "_vtable " + type + "_static_vtable = {");
                  //Write the fields into the C code
                  for(int i = 0; i < methods.size(); i++) {
                     sb.append("." + methods.get(i) + " = " + type + "_" + methods.get(i) + "; ");
                  }
                  sb.append("}; ");
                  
                  //Now getting over to the structure type
                  //Place the full structure
                  type = root.getChildren().get(0).getValue().toString();
                  sb.append("typedef struct " + type + "_struct {" + type + "_vtable* _vtable; ");
                  fields = new ArrayList<ArrayList<String>>();
                  types = new ArrayList<ArrayList<String>>();
                  do {
                     fields.add(0, new ArrayList<String>());
                     types.add(0, new ArrayList<String>());
                     
                     vclass = ast.scope.get(ScopeItem.CLASS, type).userType;
                     for(String fname : vclass.scope.fieldSet()) {
                        if(!fname.equals("self") && CGen.someHas(fields, fname) == false) {
                           types.get(0).add(vclass.scope.get(ScopeItem.FIELD, fname).userType.getProp("type").toString());
                           fields.get(0).add(fname);
                        }
                     }
                  } while(!(type = ext.get(type)).equals(Analyzer.EMPTY));
                  //Write the fields into the C code
                  for(int i = 0; i < fields.size(); i++) {
                     for(int j = 0; j < fields.get(i).size(); j++) {
                        cType(fields.get(i).get(j), types.get(i).get(j));
                     }
                  }
                  type = root.getChildren().get(0).getValue().toString();
                  sb.append("} " + type + "_struct; ");
                  
                  //Now ading a new function for init
                  //Add init method for this class
                  sb.append(type + "_struct* " + type + "_init(" + type + "* self) {");
                  //Build "super();" call
                  if(!ext.get(type).equals("Object")) {
                     sb.append(ext.get(type) + "_init(self); ");
                  }
                  //Register class then close so far the init, after having saved where to insert.
                  ast.scope.put(ScopeItem.CTYPE, type, new CClassRecord(fields, types, methods, sigs, sb.length()));
                  sb.append("return self; } ");
               }
               break;
            default:
               break;
         }
      }
      
      if(pass) {
         for(ASTNode r : root.getChildren()) {
            genStructures(r, false, second);
         }
      } else if(second) {
         for(ASTNode r : root.getChildren()) {
            genFunctions(type, r);
         }
      }
   }
   
   /**
    * Type in C.
    * @param type Type.
    * @return C type.
    */
   private static String localType(String type) {
      switch(type) {
         case "int32":
            return "int";
         case "bool":
            return "char";
         case "String":
            return "char*";
         case "unit":
            return "char";
         default:
            //Is a pointer
            return "void*";
      }
   }
   
   /**
    * Add a signature if non existent.
    * @param a The node of the method.
    * @param methods Methods.
    * @param sigs Signatures.
    * @param write Write the signature.
    */
   private void tryAddSig(ASTNode a, String cname, ArrayList<String> methods, ArrayList<CSignature> sigs, boolean write) {
      boolean has;
      int i;
      String mname;
      CSignature sig;
      ASTNode fms;
      
      if(a.stype.equals("method")) {
         mname = a.getChildren().get(0).getValue().toString();
         has = a.getChildren().get(1).stype.equals("formals");
         if(!methods.contains(mname)) {
            methods.add(mname);
            sig = new CSignature(CGen.localType(a.getProp("type").toString()));
            sig.newParam("self", CGen.localType(a.getProp("type").toString()));
            if(has) {
               fms = a.getChildren().get(1);
               for(int j = 0; j < fms.getChildren().size(); j++) {
                  sig.newParam(fms.getChildren().get(j).getChildren().get(0).getValue().toString(),
                           CGen.localType(fms.getChildren().get(j).getProp("type").toString()));
               }
            }
            
            //Print out the signature if needed
            if(write) {
               i = sigs.size() - 1;
               sb.append(sigs.get(i).ret + " " + cname + "_" + methods.get(i) + "(");
               for(int j = 0; j < sigs.get(i).pnames.size(); j++) {
                  sb.append(sigs.get(i).ptypes.get(j) + ",");
               }
               if(sigs.get(i).pnames.size() > 0)
                  sb.deleteCharAt(sb.length() - 1);
               sb.append(");");
            }
         }
      }
   }
   
   /**
    * Check if a composite has the value.
    * @param c Composite.
    * @param s Value.
    * @return Described.
    */
   private static boolean someHas(ArrayList<ArrayList<String>> c, String s) {
      for(ArrayList<String> a : c) {
         if(a.contains(s))
            return true;
      }
      return false;
   }

   /**
    * Saves C type for our types.
    * @param fname Name of field.
    * @param type Our kind of type.
    */
   private void cType(String fname, String type) {
      switch(type) {
         case "int32":
            sb.append("int " + fname + "; ");
         case "bool":
            sb.append("char " + fname + "; ");
         case "String":
            sb.append("char* " + fname + "; ");
         case "unit":
            sb.append("char " + fname + "; ");
         default:
            //Is a pointer
            sb.append("void* " + fname + "; ");
      }
   }

   /**
    * Generate functions into builder.
    * @param cname Class name for the following methods.
    * @param root Where we are.
    */
   private void genFunctions(String cname, ASTNode root) {
      StringBuilder save, tmp;
      
      if(root.ending == false) {
         switch(root.stype) {
            case "method":
               //Start building method
               tryAddSig(root, cname, new ArrayList<String>(), new ArrayList<CSignature>(), true);
               sb.deleteCharAt(sb.length() - 1);
               sb.append(" {");
               buildBody(cname, root.getChildren().size() > 3? root.getChildren().get(3) : root.getChildren().get(2));
               sb.append("} ");
               break;
            case "field":
               if(root.getChildren().size() > 2) {
                  //Initialized
                  sb.insert(where, root.getChildren().get(0).getValue().toString() + " = ");
                  where += (root.getChildren().get(0).getValue().toString() + " = ").length();
                  //Save and run
                  save = sb;
                  tmp = sb = new StringBuilder();
                  buildBody(cname, root.getChildren().get(2));
                  sb = save;
                  sb.insert(where, tmp.toString() + "; ");
                  where += (tmp.toString() + "; ").length();
               } else {
                  //Unitialized!
                  sb.append(getDefaultForType(root.getChildren().get(0).getValue().toString(), root.getChildren().get(1).getProp("type").toString()));
                  where += getDefaultForType(root.getChildren().get(0).getValue().toString(), root.getChildren().get(1).getProp("type").toString()).length();
               }
               break;
            default:
               break;
         }
      }
   }

   /**
    * Returns the default value for a type.
    * @param field The field to initialize.
    * @param type The type.
    */
   private String getDefaultForType(String field, String type) {
      switch (type) {
         case "int32":
            return field + " = 0; ";
         case "bool":
            return field + " = 0; ";
         case "String":
            return field + " = malloc(sizeof(char)); " + field + "[0] = '\0'; "; 
         case "unit":
            return field + " = 0; ";
         default:
            //Is a pointer, assume we are 32-bit arch
            return field + " = NULL; ";
      }
   }

  /**
   * Add function body.
   * @param cname Class name.
   * @param root Current node.
   */
   private void buildBody(String cname, ASTNode root) {
      boolean has;
      
      if(root.ending) {
         switch(root.itype) {
            case SymbolValue.NOT:
               sb.append("(!");
               buildBody(cname, root.getChildren().get(0));
               sb.append(")");
               break;
            case SymbolValue.EQUAL:
               sb.append("(");
               buildBody(cname, root.getChildren().get(0));
               sb.append(" == ");
               buildBody(cname, root.getChildren().get(1));
               sb.append(")");
               break;
            case SymbolValue.AND:
               sb.append("(");
               buildBody(cname, root.getChildren().get(0));
               sb.append(" & ");
               buildBody(cname, root.getChildren().get(1));
               sb.append(")");
               break;
            case SymbolValue.LOWER_EQUAL:
               sb.append("(");
               buildBody(cname, root.getChildren().get(0));
               sb.append(" <= ");
               buildBody(cname, root.getChildren().get(1));
               sb.append(")");
               break;
            case SymbolValue.LOWER:
               sb.append("(");
               buildBody(cname, root.getChildren().get(0));
               sb.append(" < ");
               buildBody(cname, root.getChildren().get(1));
               sb.append(")");
               break;
            case SymbolValue.PLUS:
               sb.append("(");
               buildBody(cname, root.getChildren().get(0));
               sb.append(" + ");
               buildBody(cname, root.getChildren().get(1));
               sb.append(")");
               break;
            case SymbolValue.MINUS:
               sb.append("(");
               buildBody(cname, root.getChildren().get(0));
               sb.append(" - ");
               buildBody(cname, root.getChildren().get(1));
               sb.append(")");
               break;
            case SymbolValue.TIMES:
               sb.append("(");
               buildBody(cname, root.getChildren().get(0));
               sb.append(" * ");
               buildBody(cname, root.getChildren().get(1));
               sb.append(")");
               break;
            case SymbolValue.DIV:
               sb.append("(");
               buildBody(cname, root.getChildren().get(0));
               sb.append(" / ");
               buildBody(cname, root.getChildren().get(1));
               sb.append(")");
               break;
            case SymbolValue.POW:
               sb.append("__pow(");
               buildBody(cname, root.getChildren().get(0));
               sb.append(", ");
               buildBody(cname, root.getChildren().get(1));
               sb.append(")");
               break;
            case SymbolValue.ISNULL:
               sb.append("(");
               buildBody(cname, root.getChildren().get(0));
               sb.append(" == 0)");
               break;
            case SymbolValue.ASSIGN:
               sb.append("(");
               buildBody(cname, root.getChildren().get(0));
               sb.append(" = ");
               buildBody(cname, root.getChildren().get(1));
               sb.append(")");
               break;
            case SymbolValue.NEW:
               sb.append("(" + root.getProp("type").toString() + "_init(malloc(sizeof(" + root.getProp("type").toString() + "_struct))))");
               break;
            case SymbolValue.INTEGER_LITERAL:
               sb.append("(" + (int)root.getValue() + ")");
               break;
            case SymbolValue.STRING_LITERAL:
               sb.append("(" + regName(root.getValue().toString()) + ")");
               break;
            case SymbolValue.OBJECT_IDENTIFIER:
               sb.append("(" + root.getValue().toString() + ")");
               break;
            case SymbolValue.FALSE:
               sb.append("(0)");
               break;
            case SymbolValue.TRUE:
               sb.append("(1)");
               break;
            case SymbolValue.UNIT_VALUE:
               sb.append("(0)");
               break;
            case SymbolValue.INT32:
               sb.append("(int)");
               break;
            case SymbolValue.BOOL:
               sb.append("(char)");
               break;
            case SymbolValue.UNIT:
               sb.append("(char)");
               break;
            case SymbolValue.STRING:
               sb.append("(char*)");
               break;
            default:
               System.err.println("code generation error: should never get here: type " + ASTNode.typeValue(root));
               break;
         }
      } else {
         switch(root.stype) {
            case "call":
               has = root.getChildren().size() > 2;
               sb.append("(" + root.getChildren().get(0).getProp("type").toString() + "_" + root.getChildren().get(1) + "(");
               buildBody(cname, root.getChildren().get(0));
               sb.append(", ");
               if(has) {
                  for(int i = 0; i < root.getChildren().get(2).getChildren().size(); i++) {
                     buildBody(cname, root.getChildren().get(2).getChildren().get(i));
                     sb.append(",");
                  }
               }
               sb.deleteCharAt(sb.length() - 1);
               sb.append("))");
               break;
            case "formal":
               buildBody(cname, root.getChildren().get(0));
               break;
            case "assign":
               buildBody(cname, root.getChildren().get(0));
               sb.append(" = ");
               buildBody(cname, root.getChildren().get(1));
               break;
            case "block":
               for(int i = 0; i < root.getChildren().size() - 1; i++)
                  buildBody(cname, root.getChildren().get(i));
               buildBody(cname, root.getChildren().get(root.getChildren().size() - 1));
               break;
            case "if":
               sb.append("if(");
               buildBody(cname, root.getChildren().get(0));
               sb.append(") {");
               buildBody(cname, root.getChildren().get(1));
               sb.append("} ");
               if(root.getChildren().size() > 2) {
                  sb.append("else {");
                  buildBody(cname, root.getChildren().get(2));
                  sb.append("} ");
               }
               break;
            case "while":
               sb.append("while(");
               buildBody(cname, root.getChildren().get(0));
               sb.append(") {");
               buildBody(cname, root.getChildren().get(1));
               sb.append("} ");
               break;
            case "let":
               if(root.getChildren().size() > 3) {
                  //Initialized
                  sb.append(root.getChildren().get(0).getValue().toString() + " = ");
                  buildBody(cname, root.getChildren().get(2));
                  sb.append("; ");
               } else {
                  //Unitialized!
                  sb.append(getDefaultForType(root.getChildren().get(0).getValue().toString(), root.getChildren().get(1).getProp("type").toString()));
               }
               buildBody(cname, root.getChildren().size() > 3? root.getChildren().get(3) : root.getChildren().get(2));
               break;
            case "uminus":
               sb.append("(-");
               buildBody(cname, root.getChildren().get(0));
               sb.append(")");
               break;
            default:
               System.err.println("code generation error: should never get here: type " + ASTNode.typeValue(root));
               break;
         }
      }
   }
   
   /**
    * Register a new global string.
    * @param global String.
    * @return How it is called.
    */
   private String regName(String global) {
      String urand = new BigInteger(130, new Random()).toString(32);
      strs.put(urand, global);
      return urand;
   }

}
