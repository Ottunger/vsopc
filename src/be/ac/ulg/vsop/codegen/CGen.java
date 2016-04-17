package be.ac.ulg.vsop.codegen;

import java.io.File;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Stack;

import be.ac.ulg.vsop.analyzer.Analyzer;
import be.ac.ulg.vsop.analyzer.ScopeItem;
import be.ac.ulg.vsop.parser.ASTNode;
import be.ac.ulg.vsop.parser.SymbolValue;


public class CGen {
   
   public final static char LLVM = 'L';
   public final static char C = 'C';
   public final static char EXE = 'E';
   
   private Stack<Integer> bcins; //Before current instruction when required for insterting calls.
   private Stack<String> rlabel; //End of block value accumulator 
   private ASTNode ast;
   private HashMap<String, String> ext, strs;
   StringBuilder sb;
   private Set<String> classes;
   private HashMap<String, ArrayList<String>> lets, letstypes;
   private HashMap<String, String> lmapping;
   
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
    * @param file Where to save.
    * @param type Type of emission.
    */
   public void emit(OutputStream o, String file, char type) throws Exception {
      classes = new HashSet<String>(ext.keySet());
      classes.remove("Object");
      classes.remove("String");
      classes.remove("int32");
      classes.remove("bool");
      classes.remove("unit");
      classes.remove("IO");
      
      strs = new HashMap<String, String>();
      sb = new StringBuilder();
      bcins = new Stack<Integer>();
      rlabel = new Stack<String>();
      lets = new HashMap<String, ArrayList<String>>();
      letstypes = new HashMap<String, ArrayList<String>>();
      lmapping = new HashMap<String, String>();
      
      //Create pow function, for pow in VSOP
      sb.append("#include \"gc.h\"\n#include <stdio.h>\n#include <stdlib.h>\nint __pow(int a, int b); int __pow(int a, int b)"
               + "{if(b == 0) return 1; if(b == 1) return a; int rec = __pow(a, b/2); if(b % 2) return a * rec * rec; return rec * rec;} ");
      //And here comes logging for free
      sb.append("typedef struct IO_vtable IO_vtable; typedef struct IO_struct {IO_vtable* _vtable;} IO_struct; IO_struct* IO_print(IO_struct*, char*);"
               + "IO_struct* IO_printInt(IO_struct*, int); struct IO_vtable {IO_struct* (*print)(IO_struct*, char*); IO_struct* (*printInt)(IO_struct*, int);"
               + "}; IO_vtable IO_static_vtable = {IO_print, IO_printInt}; IO_struct* IO_init__(IO_struct* self) {self->_vtable = &IO_static_vtable; return self;"
               + "} IO_struct* IO_print(IO_struct* self, char* str) {puts(str); return self;} IO_struct* IO_printInt(IO_struct* self, int num) {"
               + "printf(\"%d\\n\", num); return self;}");
      
      //From a first pass, generate the structures that classes rely are
      while(classes.size() > 0)
         genStructures(ast, true, false);
      //From a second pass, generate the list of all declared local variable for each
      //method, to build the "let"'s later on.
      registerLets(ast, null, null, 0);
      //From a third pass, generate the methods that are functions that have a pointer *self
      //Third pass is actually deeper in first pass
      genStructures(ast, true, true);
      
      //Create global strings constants.
      for(Map.Entry<String, String> ent : strs.entrySet()) {
         sb.insert(0, ent.getKey() + " = \"" + ent.getValue() + "\"; ");
      }
      
      //Build entry point, new Main().main().
      sb.append("int main() { GC_INIT(); return Main_main(Main_init__(((Main_struct*)GC_MALLOC(sizeof(Main_struct))))); }");
      
      if(type == CGen.C) {
         o.write(sb.toString().getBytes());
      } else {
         Path temp = null;
         ProcessBuilder pb;
         Process p;
         if(type == CGen.LLVM) {
            file = CGen.randomString();
            temp = new File(file).toPath();
            pb = new ProcessBuilder("clang", "-x", "c", "-o", file, "-S", "-emit-llvm", "-I/usr/local/gc/include", "-");
         } else
            pb = new ProcessBuilder("clang", "-x", "c", "-O3", "-o", file, "-I/usr/local/gc/include", "-static", "-", "-L/usr/local/gc/lib", "-lgc");
         pb.redirectError(new File("/dev/null"));
         pb.redirectOutput(new File("/dev/null"));
         p = pb.start();
         p.getOutputStream().write(sb.toString().getBytes());
         p.getOutputStream().flush();
         p.getOutputStream().close();
         p.getInputStream().close();
         p.waitFor();
         if(p.exitValue() == 1)
            throw new Exception("Could not safely emit code as received 1, error");
         if(type == CGen.LLVM) {
            o.write(Files.readAllBytes(temp));
            Files.delete(temp);
         }
      }
   }
   
   /**
    * Generate structures into builder.
    * @param root AST.
    * @param pass Reached classes.
    * @param second Second pass.
    */
   private void genStructures(ASTNode root, boolean pass, boolean second) {
      String type = "", upper = "";
      ASTNode vclass;
      ArrayList<String> fields, types;
      ArrayList<String> methods;
      ArrayList<CSignature> sigs;
      
      if(!second && root.ending) {
         switch(root.itype) {
            case SymbolValue.CLASS:
               //Create a structure for this type if all parent types already defined
               type = root.getChildren().get(0).getValue().toString();
               if(!classes.contains(ext.get(type))) {
                  classes.remove(type);
                  
                  //Now getting over to the structure type
                  //Place the full structure
                  type = root.getChildren().get(0).getValue().toString();
                  sb.append("typedef struct " + type + "_vtable " + type + "_vtable; ");
                  sb.append("typedef struct " + type + "_struct {" + type + "_vtable* _vtable; ");
                  fields = new ArrayList<String>();
                  types = new ArrayList<String>();
                  do {
                     vclass = ast.scope.get(ScopeItem.CLASS, type).userType;
                     for(String fname : vclass.scope.fieldSet()) {
                        if(!fname.equals("self") && fields.contains(fname) == false) {
                           types.add(0, vclass.scope.get(ScopeItem.FIELD, fname).userType.getProp("type").toString());
                           fields.add(0, fname);
                        }
                     }
                     types.add(CClassRecord.DELIMITER);
                     fields.add(CClassRecord.DELIMITER);
                  } while(!(type = ext.get(type)).equals(Analyzer.EMPTY));
                  //Write the fields into the C code
                  for(int i = 0; i < fields.size(); i++) {
                     if(fields.get(i).equals(CClassRecord.DELIMITER) == false)
                        cType(fields.get(i), types.get(i));
                  }
                  type = root.getChildren().get(0).getValue().toString();
                  sb.append("} " + type + "_struct; ");
                  
                  //Now getting to the vtable definition, which may be inherited
                  //Prepare vtable definition
                  methods = new ArrayList<String>();
                  sigs = new ArrayList<CSignature>();
                  do {
                     vclass = ast.scope.get(ScopeItem.CLASS, type).userType;
                     if(vclass.getChildren().size() > 2) {
                        for(ASTNode a : vclass.getChildren()) {
                           if(type.equals(root.getChildren().get(0).getValue().toString())) {
                              tryAddSig(a, root.getChildren().get(0).getValue().toString(), methods, sigs, true, false);
                           } else {
                              tryAddSig(a, root.getChildren().get(0).getValue().toString(), methods, sigs, false, false);
                           }
                        }
                     }
                  } while(!(type = ext.get(type)).equals(Analyzer.EMPTY));
                  type = root.getChildren().get(0).getValue().toString();
                  //vtable definition and one static instance
                  sb.append("struct " + type + "_vtable {");
                  //Write the fields into the C code
                  for(int i = 0; i < methods.size(); i++) {
                     sb.append(sigs.get(i).ret + " (*" + methods.get(i) + ")(");
                     for(int j = 0; j < sigs.get(i).pnames.size(); j++) {
                        sb.append(sigs.get(i).ptypes.get(j) + ",");
                     }
                     if(sigs.get(i).pnames.size() > 0)
                        sb.deleteCharAt(sb.length() - 1);
                     sb.append("); ");
                  }
                  //Add IO methods if derived from it
                  if(Analyzer.isSameOrChild(ext, type, "IO"))
                     sb.append("IO_struct* (*print)(IO_struct*, char*); IO_struct* (*printInt)(IO_struct*, int);");
                  sb.append("}; ");
                  //Create the static instance
                  sb.append(type + "_vtable " + type + "_static_vtable = {");
                  //Write the fields into the C code
                  for(int i = 0; i < methods.size(); i++) {
                     do {
                        vclass = ast.scope.get(ScopeItem.CLASS, type).userType;
                        if(vclass.scope.methodSet().contains(methods.get(i))) {
                           upper = vclass.getChildren().get(0).getValue().toString();
                           break;
                        }
                     } while(!(type = ext.get(type)).equals(Analyzer.EMPTY));
                     type = root.getChildren().get(0).getValue().toString();
                     sb.append(upper + "_" + methods.get(i) + ",");
                  }
                  //Add IO methods if derived from it
                  if(Analyzer.isSameOrChild(ext, type, "IO"))
                     sb.append("IO_print, IO_printInt,");
                  if(methods.size() > 0 || Analyzer.isSameOrChild(ext, type, "IO"))
                     sb.deleteCharAt(sb.length() - 1);
                  sb.append("}; ");
                  
                  //Now ading a new function for init
                  //Add init method for this class
                  sb.append(type + "_struct* " + type + "_init__(" + type + "_struct* self) {");
                  //Build "super();" call
                  if(!ext.get(type).equals("Object")) {
                     sb.append(ext.get(type) + "_init__(self); ");
                  }
                  sb.append("self->_vtable = &" + type + "_static_vtable; ");
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
            genFunctions(root.getChildren().get(0).getValue().toString(), r);
         }
      }
   }
   
   /**
    * Register the "let"'s for all the methods, so that we can prepare them.
    * @param root AST root.
    * @param cname Class name.
    * @param mname Method name.
    * @param nlets Imbrication level of "let"'s.
    */
   private void registerLets(ASTNode root, String cname, String mname, int nlets) {
      String gen;
      
      if(root.itype == SymbolValue.CLASS) {
         cname = root.getChildren().get(0).getValue().toString();
      } else if(root.stype.equals("method")) {
         mname = root.getChildren().get(0).getValue().toString();
         lets.put(cname + "_" + mname, new ArrayList<String>());
         letstypes.put(cname + "_" + mname, new ArrayList<String>());
      } else if(root.stype.equals("let")) {
         gen = "__" + CGen.randomString();
         lmapping.put(cname + mname + nlets + ">" + root.getChildren().get(0).getValue().toString(), gen);
         lets.get(cname + "_" + mname).add(0, gen);
         letstypes.get(cname + "_" + mname).add(0, CGen.localType(root.getChildren().get(1).getProp("type").toString()));
         nlets++;
      }
      
      for(ASTNode a : root.getChildren())
         registerLets(a, cname, mname, nlets);
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
            return type + "_struct*";
      }
   }
   
   /**
    * Add a signature if non existent.
    * @param a The node of the method.
    * @param methods Methods.
    * @param sigs Signatures.
    * @param write Write the signature.
    * @param names Print the names of the formals/arguments.
    */
   private void tryAddSig(ASTNode a, String cname, ArrayList<String> methods, ArrayList<CSignature> sigs, boolean write, boolean names) {
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
            sig.newParam("self", CGen.localType(cname));
            if(has) {
               fms = a.getChildren().get(1);
               for(int j = 0; j < fms.getChildren().size(); j++) {
                  sig.newParam(fms.getChildren().get(j).getChildren().get(0).getValue().toString(),
                           CGen.localType(fms.getChildren().get(j).getProp("type").toString()));
               }
            }
            sigs.add(sig);
            
            //Print out the signature if needed
            if(write) {
               i = sigs.size() - 1;
               sb.append(sigs.get(i).ret + " " + cname + "_" + methods.get(i) + "(");
               for(int j = 0; j < sigs.get(i).pnames.size(); j++) {
                  sb.append(sigs.get(i).ptypes.get(j));
                  if(names)
                     sb.append(" " + sigs.get(i).pnames.get(j));
                  sb.append(",");
               }
               if(sigs.get(i).pnames.size() > 0)
                  sb.deleteCharAt(sb.length() - 1);
               sb.append(");");
            }
         }
      }
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
            break;
         case "bool":
            sb.append("char " + fname + "; ");
            break;
         case "String":
            sb.append("char* " + fname + "; ");
            break;
         case "unit":
            sb.append("char " + fname + "; ");
            break;
         default:
            //Is a pointer
            sb.append(type + "_struct* " + fname + "; ");
            break;
      }
   }

   /**
    * Generate functions into builder.
    * @param cname Class name for the following methods.
    * @param root Where we are.
    */
   private void genFunctions(String cname, ASTNode root) {
      StringBuilder save, tmp;
      CClassRecord c;
      
      if(root.ending == false) {
         switch(root.stype) {
            case "method":
               //Start building method
               tryAddSig(root, cname, new ArrayList<String>(), new ArrayList<CSignature>(), true, true);
               sb.deleteCharAt(sb.length() - 1);
               sb.append(" {" + CGen.localType(root.getProp("type").toString()) + " _ret__; ");
               //Now add local defined by "let"'s variables.
               for(int i = 0; i < lets.get(cname + "_" + root.getChildren().get(0).getValue().toString()).size(); i++) {
                  sb.append(letstypes.get(cname + "_" + root.getChildren().get(0).getValue().toString()).get(i) + " " +
                           lets.get(cname + "_" + root.getChildren().get(0).getValue().toString()).get(i) + "; ");
               }
               //Now build body
               rlabel.push("_ret__");
               buildBody(cname, root.getChildren().get(0).getValue().toString(),
                        root.getChildren().size() > 3? root.getChildren().get(3) : root.getChildren().get(2), 0);
               rlabel.pop();
               sb.append("return _ret__;} ");
               break;
            case "field":
               c = (CClassRecord) ast.scope.getLLVM(ScopeItem.CTYPE, cname);
               if(root.getChildren().size() > 2) {
                  //Initialized
                  sb.insert(c.initb, "self->" + root.getChildren().get(0).getValue().toString() + " = ");
                  c.initb += ("self->" + root.getChildren().get(0).getValue().toString() + " = ").length();
                  //Save and run
                  save = sb;
                  tmp = sb = new StringBuilder();
                  buildBody(cname, null, root.getChildren().get(2), 0);
                  sb = save;
                  sb.insert(c.initb, tmp.toString() + "; ");
                  c.initb += (tmp.toString() + "; ").length();
               } else {
                  //Unitialized!
                  sb.insert(c.initb, "self->" + getDefaultForType(root.getChildren().get(0).getValue().toString(), root.getChildren().get(1).getProp("type").toString()));
                  c.initb += ("self->" + getDefaultForType(root.getChildren().get(0).getValue().toString(), root.getChildren().get(1).getProp("type").toString())).length();
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
            return field + " = GC_MALLOC(sizeof(char)); " + field + "[0] = '\0'; "; 
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
   * @param Method name.
   * @param root Current node.
   * @param nlets Imrication level for "let"'s.
   * @return Created return variable name if any is created for above block.
   */
   private void buildBody(String cname, String mname, ASTNode root, int nlets) {
      boolean has;
      int top;
      String tmp;
      StringBuilder save, t;
      ASTNode insert;
      
      if(root.ending) {
         switch(root.itype) {
            case SymbolValue.NOT:
               sb.append("(!");
               buildBody(cname, mname, root.getChildren().get(0), nlets);
               sb.append(")");
               break;
            case SymbolValue.EQUAL:
               sb.append("(");
               buildBody(cname, mname, root.getChildren().get(0), nlets);
               sb.append(" == ");
               buildBody(cname, mname, root.getChildren().get(1), nlets);
               sb.append(")");
               break;
            case SymbolValue.AND:
               sb.append("(");
               buildBody(cname, mname, root.getChildren().get(0), nlets);
               sb.append(" & ");
               buildBody(cname, mname, root.getChildren().get(1), nlets);
               sb.append(")");
               break;
            case SymbolValue.LOWER_EQUAL:
               sb.append("(");
               buildBody(cname, mname, root.getChildren().get(0), nlets);
               sb.append(" <= ");
               buildBody(cname, mname, root.getChildren().get(1), nlets);
               sb.append(")");
               break;
            case SymbolValue.LOWER:
               sb.append("(");
               buildBody(cname, mname, root.getChildren().get(0), nlets);
               sb.append(" < ");
               buildBody(cname, mname, root.getChildren().get(1), nlets);
               sb.append(")");
               break;
            case SymbolValue.PLUS:
               sb.append("(");
               buildBody(cname, mname, root.getChildren().get(0), nlets);
               sb.append(" + ");
               buildBody(cname, mname, root.getChildren().get(1), nlets);
               sb.append(")");
               break;
            case SymbolValue.MINUS:
               sb.append("(");
               buildBody(cname, mname, root.getChildren().get(0), nlets);
               sb.append(" - ");
               buildBody(cname, mname, root.getChildren().get(1), nlets);
               sb.append(")");
               break;
            case SymbolValue.TIMES:
               sb.append("(");
               buildBody(cname, mname, root.getChildren().get(0), nlets);
               sb.append(" * ");
               buildBody(cname, mname, root.getChildren().get(1), nlets);
               sb.append(")");
               break;
            case SymbolValue.DIV:
               sb.append("(");
               buildBody(cname, mname, root.getChildren().get(0), nlets);
               sb.append(" / ");
               buildBody(cname, mname, root.getChildren().get(1), nlets);
               sb.append(")");
               break;
            case SymbolValue.POW:
               sb.append("__pow(");
               buildBody(cname, mname, root.getChildren().get(0), nlets);
               sb.append(", ");
               buildBody(cname, mname, root.getChildren().get(1), nlets);
               sb.append(")");
               break;
            case SymbolValue.ISNULL:
               sb.append("(");
               buildBody(cname, mname, root.getChildren().get(0), nlets);
               sb.append(" == 0)");
               break;
            case SymbolValue.ASSIGN:
               buildBody(cname, mname, root.getChildren().get(0), nlets);
               break;
            case SymbolValue.NEW:
               sb.append("(" + root.getProp("type").toString() + "_init__(GC_MALLOC(sizeof(" + root.getProp("type").toString() + "_struct))))");
               break;
            case SymbolValue.INTEGER_LITERAL:
               sb.append("(" + (int)root.getValue() + ")");
               break;
            case SymbolValue.STRING_LITERAL:
               sb.append("(" + regName(root.getValue().toString()) + ")");
               break;
            case SymbolValue.OBJECT_IDENTIFIER:
               if(root.getValue().toString().equals("self"))
                  sb.append("(self)");
               else if(root.scope.getBeforeClassLevel(ScopeItem.FIELD, root.getValue().toString()) == null)
                  sb.append("(self->" + root.getValue().toString() + ")");
               else {
                  top = nlets;
                  do {
                     if((tmp = lmapping.get(cname + mname + top + ">" + root.getValue().toString())) != null) {
                        sb.append("(" + lmapping.get(cname + mname + top + ">" + root.getValue().toString()) + ")");
                        break;
                     }
                     top--;
                  } while(top > -1);
                  if(top == -1) {
                     sb.append("(" + root.getValue().toString() + ")");
                  }
               }
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
               top = bcins.pop();
               tmp = "__" + CGen.randomString();
               sb.insert(top, CGen.localType(root.getChildren().get(0).getProp("type").toString()) + " " + tmp + " = ");
               top += (CGen.localType(root.getChildren().get(0).getProp("type").toString()) + " " + tmp + " = ").length();
               //Save and run
               save = sb;
               t = sb = new StringBuilder();
               bcins.push(0);
               buildBody(cname, mname, root.getChildren().get(0), nlets);
               bcins.pop();
               sb = save;
               sb.insert(top, t.toString() + "; ");
               top += (t.toString() + "; ").length();
               bcins.push(top);
               //Now that preparation of callee is done, build the call
               sb.append("(" + tmp + "->_vtable->" + root.getChildren().get(1).getValue().toString() + "(" + tmp + ",");
               if(has) {
                  for(int i = 0; i < root.getChildren().get(2).getChildren().size(); i++) {
                     buildBody(cname, mname, root.getChildren().get(2).getChildren().get(i), nlets);
                     sb.append(",");
                  }
               }
               sb.deleteCharAt(sb.length() - 1);
               sb.append("))");
               break;
            case "assign":
               sb.append("(");
               buildBody(cname, mname, root.getChildren().get(0), nlets);
               sb.append(" = ");
               buildBody(cname, mname, root.getChildren().get(1), nlets);
               sb.append(")");
               break;
            case "block":
               //When we have a block, we create an accumulator for it. 
               rlabel.push("_ret__" + CGen.randomString());
               sb.append(CGen.localType(root.getProp("type").toString()) + " " + rlabel.peek() + "; {");
               bcins.push(sb.length());
               for(int i = 0; i < root.getChildren().size() - 1; i++) {
                  bcins.pop();
                  bcins.push(sb.length());
                  buildBody(cname, mname, root.getChildren().get(i), nlets);
                  sb.append("; ");
               }
               bcins.pop();
               bcins.push(sb.length());
               //If we reached bottom of stack, is the enclosing block of a method, and save in the global return
               tmp = rlabel.pop();
               sb.append(rlabel.peek() + " = ");
               buildBody(cname, mname, root.getChildren().get(root.getChildren().size() - 1), nlets);
               bcins.pop();
               sb.append(";} ");
               break; 
            case "if":
               sb.append("((");
               buildBody(cname, mname, root.getChildren().get(0), nlets);
               sb.append(")? (");
               buildBody(cname, mname, root.getChildren().get(1), nlets);
               sb.append(") : (");
               if(root.getChildren().size() > 2) {
                  buildBody(cname, mname, root.getChildren().get(2), nlets);
               } else {
                  sb.append("0");
               }
               sb.append("))");
               break;
            case "while":
               sb.append("while(");
               buildBody(cname, mname, root.getChildren().get(0), nlets);
               sb.append(") {");
               buildBody(cname, mname, root.getChildren().get(1), nlets);
               sb.append("} ");
               break;
            case "let":
               tmp = lmapping.get(cname + mname + nlets + ">" + root.getChildren().get(0).getValue().toString());
               if(root.getChildren().size() > 3) {
                  //Initialized
                  sb.append(tmp + " = ");
                  buildBody(cname, mname, root.getChildren().get(2), nlets);
                  sb.append("; ");
               } else {
                  //Unitialized!
                  sb.append(getDefaultForType(tmp, root.getChildren().get(1).getProp("type").toString()));
               }
               //Add a level of indirection to create a block down below! ;)
               top = root.getChildren().size() > 3? 3 : 2;
               insert = new ASTNode("block", "__dummy__");
               insert.getChildren().add(root.getChildren().get(top));
               insert.addProp("type", root.getChildren().get(top).getProp("type"));
               root.getChildren().add(top, insert);
               //Build the return
               nlets++;
               buildBody(cname, mname, root.getChildren().get(top), nlets);
               break;
            case "uminus":
               sb.append("(-");
               buildBody(cname, mname, root.getChildren().get(0), nlets);
               sb.append(")");
               break;
            default:
               System.err.println("code generation error: should never get here: type " + ASTNode.typeValue(root));
               break;
         }
      }
   }
   
   /**
    * Returns a random string.
    * @return Random string.
    */
   private static String randomString() {
      return new BigInteger(130, new Random()).toString(32);
   }
   
   /**
    * Register a new global string.
    * @param global String.
    * @return How it is called.
    */
   private String regName(String global) {
      String urand = CGen.randomString();
      strs.put(urand, global);
      return urand;
   }

}
