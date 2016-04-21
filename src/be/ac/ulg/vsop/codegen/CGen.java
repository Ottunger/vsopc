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
   
   private ASTNode ast;
   private HashMap<String, String> ext, strs;
   StringBuilder sb;
   private Set<String> classes;
   private HashMap<String, ArrayList<String>> lets, letstypes;
   private HashMap<String, String> lmapping; //Used for "let"'s definitions of temp variables
   private HashMap<String, HashMap<ASTNode, String>> imapping, itypes; //Used for all instructions returns
   private HashMap<ASTNode, String[]> brlabels; //Labels used for branchings
   private ArrayList<String> cdef; //All defined classes, in their order of definition
   
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
      int aft;
      
      classes = new HashSet<String>(ext.keySet());
      classes.remove("Object");
      classes.remove("String");
      classes.remove("int32");
      classes.remove("bool");
      classes.remove("unit");
      classes.remove("IO");
      
      strs = new HashMap<String, String>();
      sb = new StringBuilder();
      lets = new HashMap<String, ArrayList<String>>();
      letstypes = new HashMap<String, ArrayList<String>>();
      lmapping = new HashMap<String, String>();
      imapping = new HashMap<String, HashMap<ASTNode, String>>();
      itypes = new HashMap<String, HashMap<ASTNode, String>>();
      brlabels = new HashMap<ASTNode, String[]>();
      cdef = new ArrayList<String>();
      
      //Create pow function, for pow in VSOP
      sb.append("#pragma pack(4)\n#include \"gc.h\"\n#include <stdio.h>\n#include <stdlib.h>\n");
      aft = sb.length();
      sb.append("int __pow(int a, int b); int __pow(int a, int b)"
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
      //From a third pass, generate the list of all intermediate variable for each
      //instruction, to build them later on.
      registerInsts(null, ast, null, null);
      //From a fourth pass, generate the methods that are functions that have a pointer *self
      //Third pass is actually deeper in first pass
      genStructures(ast, true, true);
      
      //Create global strings constants.
      for(Map.Entry<String, String> ent : strs.entrySet()) {
         sb.insert(aft, "char " + ent.getKey() + "[] = " + ent.getValue() + "; ");
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
      Stack<ASTNode> msee;
      Stack<String> rlabel;
      
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
                  msee = new Stack<ASTNode>();
                  rlabel = new Stack<String>();
                  do {
                     vclass = ast.scope.get(ScopeItem.CLASS, type).userType;
                     if(vclass.getChildren().size() > 2) {
                        for(ASTNode a : vclass.getChildren()) {
                           msee.push(a);
                           rlabel.push(type);
                        }
                     }
                  } while(!(type = ext.get(type)).equals(Analyzer.EMPTY));
                  type = root.getChildren().get(0).getValue().toString();
                  while(!msee.isEmpty()) {
                     ASTNode a = msee.pop();
                     String from = rlabel.pop();
                     if(type.equals(from)) {
                        tryAddSig(a, type, methods, sigs, true, false);
                     } else {
                        tryAddSig(a, type, methods, sigs, false, false);
                     }
                  }
                  //vtable definition and one static instance
                  sb.append("struct " + type + "_vtable {");
                  //Add IO methods if derived from it
                  if(Analyzer.isSameOrChild(ext, type, "IO"))
                     sb.append("IO_struct* (*print)(" + type + "_struct*, char*); IO_struct* (*printInt)(" + type + "_struct*, int);");
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
                  sb.append("}; ");
                  //Create the static instance
                  sb.append(type + "_vtable " + type + "_static_vtable = {");
                  //Add IO methods if derived from it
                  if(Analyzer.isSameOrChild(ext, type, "IO"))
                     sb.append("IO_print, IO_printInt,");
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
                  
                  //And we have defined ourself :)
                  cdef.add(type);
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
      int top;
      String gen;
      ASTNode insert;
      
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
    * Register the return values of instructions.
    * @param parent Parent of current node.
    * @param root AST root.
    * @param cname Class name.
    * @param mname Method name.
    */
   private void registerInsts(ASTNode parent, ASTNode root, String cname, String mname) {
      ScopeItem si;
      
      if(root.ending) {
         switch(root.itype) {
            case SymbolValue.CLASS:
               cname = root.getChildren().get(0).getValue().toString();
               imapping.put(cname + "_" + null, new HashMap<ASTNode, String>());
               itypes.put(cname + "_" + null, new HashMap<ASTNode, String>());
               break;
            case SymbolValue.NOT:
            case SymbolValue.EQUAL:
            case SymbolValue.AND:
            case SymbolValue.LOWER_EQUAL:
            case SymbolValue.LOWER:
            case SymbolValue.PLUS:
            case SymbolValue.MINUS:
            case SymbolValue.TIMES:
            case SymbolValue.DIV:
            case SymbolValue.POW:
            case SymbolValue.ISNULL:
            case SymbolValue.NEW:
            case SymbolValue.ASSIGN:
            case SymbolValue.INTEGER_LITERAL:
            case SymbolValue.STRING_LITERAL:
            case SymbolValue.FALSE:
            case SymbolValue.TRUE:
            case SymbolValue.UNIT_VALUE:
               imapping.get(cname + "_" + mname).put(root, "_inst__" + CGen.randomString());
               itypes.get(cname + "_" + mname).put(root, CGen.localType(root.getProp("type").toString()));
               break;
            case SymbolValue.OBJECT_IDENTIFIER:
               //Skip if we are the name of a method, or the name of the method in a call
               if(!parent.stype.equals("method") && !(parent.stype.equals("call") && parent.getChildren().get(1) == root)) {
                  si = root.scope.getBeforeClassLevel(ScopeItem.FIELD, root.getValue().toString());
                  if(si == null) {
                     si = Analyzer.findFieldAbove(ext, root, cname);
                     imapping.get(cname + "_" + mname).put(root, "_inst__" + CGen.randomString());
                     itypes.get(cname + "_" + mname).put(root, CGen.localType(si.userType.getProp("type").toString(), true));
                  } else {
                     imapping.get(cname + "_" + mname).put(root, "_inst__" + CGen.randomString());
                     itypes.get(cname + "_" + mname).put(root, CGen.localType(si.userType.getProp("type").toString()));
                  }
               }
               break;
            default:
               break;
         }
      } else {
         switch(root.stype) {
            case "method":
               mname = root.getChildren().get(0).getValue().toString();
               imapping.put(cname + "_" + mname, new HashMap<ASTNode, String>());
               itypes.put(cname + "_" + mname, new HashMap<ASTNode, String>());
               break;
            case "while":
               brlabels.put(root, new String[] {"_check__" + CGen.randomString(), "_endwhile__" + CGen.randomString()});
               break;
            case "if":
               brlabels.put(root, new String[] {"_if__" + CGen.randomString(), "_else__" + CGen.randomString(), "_endif__" + CGen.randomString()});
            case "call": //Fallthrough
            case "assign":
            case "block":
            case "let":
            case "uminus":
               imapping.get(cname + "_" + mname).put(root, "_inst__" + CGen.randomString());
               itypes.get(cname + "_" + mname).put(root, CGen.localType(root.getProp("type").toString()));
               break;
            default:
               break;
         }
      }
      
      for(ASTNode a : root.getChildren())
         registerInsts(root, a, cname, mname);
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
    * Type in C.
    * @param type Type.
    * @param pointerize Whether to pointerize a plain type.
    * @return C type.
    */
   private static String localType(String type, boolean pointerize) {
      switch(type) {
         case "int32":
            return "int*";
         case "bool":
            return "char*";
         case "String":
            return "char*";
         case "unit":
            return "char*";
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
         }
            
         //Print out the signature if needed
         if(write) {
            i = methods.indexOf(mname);
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
      int shift, j;
      String mname;
      StringBuilder save, tmp;
      CClassRecord c;
      
      if(root.ending == false) {
         switch(root.stype) {
            case "method":
               //Start building method
               tryAddSig(root, cname, new ArrayList<String>(), new ArrayList<CSignature>(), true, true);
               sb.deleteCharAt(sb.length() - 1);
               sb.append(" {");
               mname = root.getChildren().get(0).getValue().toString();
               //Now add local defined by "let"'s variables.
               for(int i = 0; i < lets.get(cname + "_" + mname).size(); i++) {
                  sb.append(letstypes.get(cname + "_" + mname).get(i) + " " + lets.get(cname + "_" + mname).get(i) + "; ");
               }
               //Now add local defined by instruction variables.
               for(Map.Entry<ASTNode, String> iname : imapping.get(cname + "_" + mname).entrySet()) {
                  sb.append(itypes.get(cname + "_" + mname).get(iname.getKey()) + " " + iname.getValue() + "; ");
               }
               //Now build body
               buildBody(root, cname, mname, root.getChildren().get(root.getChildren().size() > 3? 3 : 2), 0);
               sb.append("return " + imapping.get(cname + "_" + mname).get(root.getChildren().get(root.getChildren().size() > 3? 3 : 2)) + ";} ");
               break;
            case "field":
               c = (CClassRecord) ast.scope.getLLVM(ScopeItem.CTYPE, cname);
               if(root.getChildren().size() > 2) {
                  //Initialized
                  sb.insert(c.initb, "self->" + root.getChildren().get(0).getValue().toString() + " = ");
                  shift = ("self->" + root.getChildren().get(0).getValue().toString() + " = ").length();
                  c.initb += shift;
                  //Save and run
                  save = sb;
                  tmp = sb = new StringBuilder();
                  buildBody(root, cname, null, root.getChildren().get(2), 0);
                  sb = save;
                  sb.insert(c.initb, tmp.toString() + "; ");
                  shift += (tmp.toString() + "; ").length();
                  c.initb += (tmp.toString() + "; ").length();
               } else {
                  //Unitialized!
                  sb.insert(c.initb, "self->" + getDefaultForType(root.getChildren().get(0).getValue().toString(), root.getChildren().get(1).getProp("type").toString()));
                  shift = ("self->" + getDefaultForType(root.getChildren().get(0).getValue().toString(), root.getChildren().get(1).getProp("type").toString())).length();
                  c.initb += shift;
               }
               //All classes defined after us must shift were to insert
               j = cdef.indexOf(cname);
               for(int i = j + 1; i < cdef.size(); i++)
                  ((CClassRecord) ast.scope.getLLVM(ScopeItem.CTYPE, cdef.get(i))).initb += shift;
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
   * @param parent Parent node.
   * @param cname Class name.
   * @param mname name.
   * @param root Current node.
   * @param nlets Imrication level for "let"'s.
   * @return Created return variable name if any is created for above block.
   */
   private void buildBody(ASTNode parent, String cname, String mname, ASTNode root, int nlets) {
      boolean has;
      int top;
      String tmp, tmptype;

      if(root.stype.equals("if")) {
         buildBody(root, cname, mname, root.getChildren().get(0), nlets);
         sb.append("if(" + imapping.get(cname + "_" + mname).get(root.getChildren().get(0)) + ")");
         sb.append("goto " + brlabels.get(root)[0] + ";");
         if(root.getChildren().size() > 2) {
            sb.append("else goto " + brlabels.get(root)[1] + ";");
         }
         sb.append(brlabels.get(root)[0] + ":");
         buildBody(root, cname, mname, root.getChildren().get(1), nlets);
         sb.append(imapping.get(cname + "_" + mname).get(root) + " = " + imapping.get(cname + "_" + mname)
                 .get(root.getChildren().get(1).getChildren().get(root.getChildren().get(1).getChildren().size() - 1)) + ";");
         sb.append("goto " + brlabels.get(root)[2] + ";");
         if(root.getChildren().size() > 2) {
            sb.append(brlabels.get(root)[1] + ":");
            buildBody(root, cname, mname, root.getChildren().get(2), nlets);
            sb.append(imapping.get(cname + "_" + mname).get(root) + " = " + imapping.get(cname + "_" + mname)
                    .get(root.getChildren().get(2).getChildren().get(root.getChildren().get(2).getChildren().size() - 1)) + ";");
            sb.append("goto " + brlabels.get(root)[2] + ";");
         }
         sb.append(brlabels.get(root)[2] + ":");
         return;
      } else if(root.stype.equals("while")) {
         sb.append(brlabels.get(root)[0] + ":");
         sb.append("if(" + imapping.get(cname + "_" + mname).get(root.getChildren().get(0)) + ") {");
         buildBody(root, cname, mname, root.getChildren().get(1), nlets);
         if(root.getChildren().size() > 2) {
            sb.append("} else goto " + brlabels.get(root)[1] + ";");
         }
         sb.append(brlabels.get(root)[1] + ":");
         return;
      } else if(root.stype.equals("let")) {
         has = root.getChildren().size() > 3;
         //Build the children only up until init
         for(int i = 0; i < (has? 3 : 2); i++) {
            buildBody(root, cname, mname, root.getChildren().get(i), nlets + 1);
         }
         //Build our values
         tmp = lmapping.get(cname + mname + nlets + ">" + root.getChildren().get(0).getValue().toString());
         if(root.getChildren().size() > 3) {
            //Initialized
            sb.append(tmp + " = " + imapping.get(cname + "_" + mname).get(root.getChildren().get(2)) + ";");
         } else {
            //Unitialized!
            sb.append(getDefaultForType(tmp, root.getChildren().get(1).getProp("type").toString()));
         }
         //Build the body
         buildBody(root, cname, mname, root.getChildren().get(has? 3 : 2), nlets + 1);
         //Build the return
         sb.append(imapping.get(cname + "_" + mname).get(root) + " = " +
                 imapping.get(cname + "_" + mname).get(root.getChildren().get(has? 3 : 2)) + ";");
         return;
      }

      //Build the instructions that make ourself up
      for(ASTNode a : root.getChildren()) {
         buildBody(root, cname, mname, a, nlets);
      }
      //TODO: When type of local instr is no pointer, should pointerize it (for returns, at least, when else?)
      //TODO: Call dereference fail? Check on provided VSOP file
      if(root.ending) {
         switch(root.itype) {
            case SymbolValue.NOT:
               sb.append(imapping.get(cname + "_" + mname).get(root) + " = !" + imapping.get(cname + "_" + mname).get(root.getChildren().get(0)) + ";");
               break;
            case SymbolValue.EQUAL:
               sb.append(imapping.get(cname + "_" + mname).get(root) + " = (" + imapping.get(cname + "_" + mname).get(root.getChildren().get(0)) + ") == (" +
                        imapping.get(cname + "_" + mname).get(root.getChildren().get(1)) + ");");
               break;
            case SymbolValue.AND:
               sb.append(imapping.get(cname + "_" + mname).get(root) + " = (" + imapping.get(cname + "_" + mname).get(root.getChildren().get(0)) + ") & (" +
                        imapping.get(cname + "_" + mname).get(root.getChildren().get(1)) + ");");
               break;
            case SymbolValue.LOWER_EQUAL:
               sb.append(imapping.get(cname + "_" + mname).get(root) + " = (" + imapping.get(cname + "_" + mname).get(root.getChildren().get(0)) + ") <= (" +
                        imapping.get(cname + "_" + mname).get(root.getChildren().get(1)) + ");");
               break;
            case SymbolValue.LOWER:
               sb.append(imapping.get(cname + "_" + mname).get(root) + " = (" + imapping.get(cname + "_" + mname).get(root.getChildren().get(0)) + ") < (" +
                        imapping.get(cname + "_" + mname).get(root.getChildren().get(1)) + ");");
               break;
            case SymbolValue.PLUS:
               sb.append(imapping.get(cname + "_" + mname).get(root) + " = (" + imapping.get(cname + "_" + mname).get(root.getChildren().get(0)) + ") + (" +
                        imapping.get(cname + "_" + mname).get(root.getChildren().get(1)) + ");");
               break;
            case SymbolValue.MINUS:
               sb.append(imapping.get(cname + "_" + mname).get(root) + " = (" + imapping.get(cname + "_" + mname).get(root.getChildren().get(0)) + ") - (" +
                        imapping.get(cname + "_" + mname).get(root.getChildren().get(1)) + ");");
               break;
            case SymbolValue.TIMES:
               sb.append(imapping.get(cname + "_" + mname).get(root) + " = (" + imapping.get(cname + "_" + mname).get(root.getChildren().get(0)) + ") * (" +
                        imapping.get(cname + "_" + mname).get(root.getChildren().get(1)) + ");");
               break;
            case SymbolValue.DIV:
               sb.append(imapping.get(cname + "_" + mname).get(root) + " = (" + imapping.get(cname + "_" + mname).get(root.getChildren().get(0)) + ") / (" +
                        imapping.get(cname + "_" + mname).get(root.getChildren().get(1)) + ");");
               break;
            case SymbolValue.POW:
               sb.append(imapping.get(cname + "_" + mname).get(root) + " = __pow((" + imapping.get(cname + "_" + mname).get(root.getChildren().get(0)) + "), (" +
                        imapping.get(cname + "_" + mname).get(root.getChildren().get(1)) + "));");
            case SymbolValue.ISNULL:
               sb.append(imapping.get(cname + "_" + mname).get(root) + " = (" + imapping.get(cname + "_" + mname).get(root.getChildren().get(0)) + ") == 0;");
               break;
            case SymbolValue.NEW:
               sb.append(imapping.get(cname + "_" + mname).get(root) + " = " + root.getProp("type").toString() + "_init__(GC_MALLOC(sizeof(" + root.getProp("type").toString() + "_struct)));");
               break;
            case SymbolValue.ASSIGN:
               sb.append(imapping.get(cname + "_" + mname).get(root) + " = " + imapping.get(cname + "_" + mname).get(root.getChildren().get(0)) + ";");
               break;
            case SymbolValue.INTEGER_LITERAL:
               sb.append(imapping.get(cname + "_" + mname).get(root) + " = " + (int)root.getValue() + ";");
               break;
            case SymbolValue.STRING_LITERAL:
               sb.append(imapping.get(cname + "_" + mname).get(root) + " = " + regName(root.getValue().toString()) + ";");
               break;
            case SymbolValue.OBJECT_IDENTIFIER:
               //Skip if we are the name of a method, or the name of the method in a call
               if(parent.stype.equals("method") || (parent.stype.equals("call") && parent.getChildren().get(1) == root))
                  break;
               if(root.getValue().toString().equals("self"))
                  sb.append(imapping.get(cname + "_" + mname).get(root) + " = self;");
               else if(root.scope.getBeforeClassLevel(ScopeItem.FIELD, root.getValue().toString()) == null) {
                  tmptype = Analyzer.findFieldAbove(ext, root, cname).userType.getProp("type").toString();
                  has = (tmptype.equals("int32") || tmptype.equals("bool") || tmptype.equals("unit"));
                  sb.append(imapping.get(cname + "_" + mname).get(root) + " = " + (has? "&" : "") + "self->" + root.getValue().toString() + ";");
               } else {
                  top = nlets;
                  do {
                     if((tmp = lmapping.get(cname + mname + top + ">" + root.getValue().toString())) != null) {
                        sb.append(imapping.get(cname + "_" + mname).get(root) + " = " + lmapping.get(cname + mname + top + ">" + root.getValue().toString()) + ";");
                        break;
                     }
                     top--;
                  } while(top > -1);
                  if(top == -1) {
                     sb.append(imapping.get(cname + "_" + mname).get(root) + " = " + root.getValue().toString() + ";");
                  }
               }
               break;
            case SymbolValue.FALSE:
               sb.append(imapping.get(cname + "_" + mname).get(root) + " = 0;");
               break;
            case SymbolValue.TRUE:
               sb.append(imapping.get(cname + "_" + mname).get(root) + " = 1;");
               break;
            case SymbolValue.UNIT_VALUE:
               sb.append(imapping.get(cname + "_" + mname).get(root) + " = 0;");
               break;
            case SymbolValue.INT32:
            case SymbolValue.STRING:
            case SymbolValue.BOOL:
            case SymbolValue.UNIT:
            case SymbolValue.TYPE_IDENTIFIER:
               //Just to not get an error
               break;
            default:
               System.err.println("code generation error: should never get here: type " + ASTNode.typeValue(root));
               break;
         }
      } else {
         switch(root.stype) {
            case "call":
               has = root.getChildren().size() > 2;
               sb.append(imapping.get(cname + "_" + mname).get(root) + " = " + imapping.get(cname + "_" + mname).get(root.getChildren().get(0)) + "->_vtable->" +
                        root.getChildren().get(1).getValue().toString() + "(" + imapping.get(cname + "_" + mname).get(root.getChildren().get(0)) + ",");
               if(has) {
                  for(int i = 0; i < root.getChildren().get(2).getChildren().size(); i++) {
                     sb.append(imapping.get(cname + "_" + mname).get(root.getChildren().get(2).getChildren().get(i)) + ",");
                  }
               }
               sb.deleteCharAt(sb.length() - 1);
               sb.append(");");
               break;
            case "assign":
               tmptype = Analyzer.findFieldAbove(ext, root.getChildren().get(0), cname).userType.getProp("type").toString();
               has = (root.scope.getBeforeClassLevel(ScopeItem.FIELD, root.getChildren().get(0).getValue().toString()) == null &&
                       tmptype.equals("int32") || tmptype.equals("bool") || tmptype.equals("unit"));
               sb.append(imapping.get(cname + "_" + mname).get(root) + " = (" + (has? "*" : "") + imapping.get(cname + "_" + mname).get(root.getChildren().get(0)) + ") = (" +
                        imapping.get(cname + "_" + mname).get(root.getChildren().get(1)) + ");");
               break;
            case "block":
               sb.append(imapping.get(cname + "_" + mname).get(root) + " = " +
                        imapping.get(cname + "_" + mname).get(root.getChildren().get(root.getChildren().size() - 1)) + ";");
               break;
            case "uminus":
               sb.append(imapping.get(cname + "_" + mname).get(root) + " = -(" + imapping.get(cname + "_" + mname).get(root.getChildren().get(0)) + ");");
               break;
            case "formal":
            case "args":
               //Just to not gen an error
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
      String urand = "_char__" + CGen.randomString();
      strs.put(urand, global);
      return urand;
   }

}
