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
   
   private int where;
   private boolean extd;
   private ASTNode ast;
   private HashMap<String, String> ext, strs;
   private StringBuilder sb;
   private String[] args;
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
    * @param extd Whether to consider VSOPExtended.
    */
   public CGen(ASTNode root, HashMap<String, String> ext, boolean extd, String[] args, int where) {
      ast = root;
      this.ext = ext;
      this.extd = extd;
      this.args = args;
      this.where = where;
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
      classes.remove("string");
      classes.remove("float");
      classes.remove("int32");
      classes.remove("byte");
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
      sb.append("#pragma pack(4)\n#include \"gc.h\"\n#include <stdio.h>\n#include <stdlib.h>\n#include <string.h>\n");
      if(extd)
         sb.append("#include \"hashtable.c\"\n");
      for(int i = where; i < args.length; i++) {
         if(args[i].startsWith("-ci"))
            sb.append("#include " + args[i].replaceFirst("-ci", "") + "\n");
      }
      aft = sb.length();
      sb.append("int __pow(int a, int b); int __pow(int a, int b)"
               + "{if(b == 0) return 1; if(b == 1) return a; int rec = __pow(a, b/2); if(b % 2) return a * rec * rec; return rec * rec;} ");
      sb.append("float __powf(float a, int b); float __powf(float a, int b)"
               + "{if(b == 0) return 1.0f; if(b == 1) return a; float rec = __powf(a, b/2); if(b % 2) return a * rec * rec; return rec * rec;} ");
      //Define Object_struct. What did you expect?
      if(extd) {
         sb.append("typedef struct Object_vtable Object_vtable; typedef struct Object_struct {Object_vtable* _vtable;} Object_struct; char Object_equals(Object_struct*,"
                  + "Object_struct*); int Object_code(Object_struct*); struct Object_vtable {char (*equals)(Object_struct*, Object_struct*); int (*code)(Object_struct*);};"
                  + "Object_vtable Object_static_vtable = {Object_equals, Object_code}; Object_struct* Object_init__(Object_struct* self)"
                  + "{self->_vtable = &Object_static_vtable; return self;}"
                  + "char Object_equals(Object_struct* a, Object_struct *b) {return a == b;}"
                  + "int Object_code(Object_struct* self) {unsigned h = (unsigned)self; h ^= (h >> 20) ^ (h >> 12); return (int) (h ^ (h >> 7) ^ (h >> 4));}");
      } else {
         sb.append("typedef struct Object_vtable Object_vtable; typedef struct Object_struct {Object_vtable* _vtable;} Object_struct; struct Object_vtable {};"
                  + "Object_vtable Object_static_vtable = {}; Object_struct* Object_init__(Object_struct* self){self->_vtable = &Object_static_vtable; return self;}");
      }
      //And here comes logging for free
      sb.append("typedef struct IO_vtable IO_vtable; typedef struct IO_struct {IO_vtable* _vtable;} IO_struct; IO_struct* IO_print(IO_struct*, char*);"
               + "IO_struct* IO_printInt32(IO_struct*, int); IO_struct* IO_printFloat(IO_struct*, float); IO_struct* IO_printBool(IO_struct*, char);"
               + "char* IO_inputLine(IO_struct*); int IO_inputInt32(IO_struct*); float IO_inputFloat(IO_struct*); char IO_inputBool(IO_struct*);");
      if(extd) {
         sb.append("struct IO_vtable {char (*equals)(IO_struct*, IO_struct*); int (*code)(IO_struct*); IO_struct* (*printInt32)(IO_struct*, int);"
                  + "IO_struct* (*printFloat)(IO_struct*, float); IO_struct* (*printBool)(IO_struct*, char); int (*inputInt32)(IO_struct*);"
                  + "char* (*inputLine)(IO_struct*); char (*inputBool)(IO_struct*); IO_struct* (*print)(IO_struct*, char*); float (*inputFloat)(IO_struct*);};"
                  + "IO_vtable IO_static_vtable = {Object_equals, Object_code, IO_printInt32, IO_printFloat, IO_printBool, IO_inputInt32,"
                  + "IO_inputLine, IO_inputBool, IO_print, IO_inputFloat};");
      } else {
         sb.append("struct IO_vtable {IO_struct* (*printInt32)(IO_struct*, int); IO_struct* (*printFloat)(IO_struct*, float);"
                  + "IO_struct* (*printBool)(IO_struct*, char); int (*inputInt32)(IO_struct*); char* (*inputLine)(IO_struct*); char (*inputBool)(IO_struct*);"
                  + "IO_struct* (*print)(IO_struct*, char*); float (*inputFloat)(IO_struct*);};"
                  + "IO_vtable IO_static_vtable = {IO_printInt32, IO_printFloat, IO_printBool, IO_inputInt32, IO_inputLine, IO_inputBool, IO_print, IO_inputFloat};");
      }
      sb.append("IO_struct* IO_init__(IO_struct* self) {self->_vtable = &IO_static_vtable; return self;}"
               + "IO_struct* IO_print(IO_struct* self, char* str) {printf(\"%s\", str); return self;}"
               + "IO_struct* IO_printInt32(IO_struct* self, int num) {printf(\"%d\", num); return self;}"
               + "IO_struct* IO_printFloat(IO_struct* self, float num) {printf(\"%f\", num); return self;}"
               + "IO_struct* IO_printBool(IO_struct* self, char bool) {if(bool) printf(\"true\"); else printf(\"false\"); return self;}"
               + "char* IO_inputLine(IO_struct* self) {char* ret = GC_MALLOC(256); if(!fgets(ret, 256, stdin)) {ret = GC_MALLOC(1); ret[0] = 0;} return ret;}"
               + "int IO_inputInt32(IO_struct* self) {int ret; if(scanf(\"%d\", &ret) == EOF) {fputs(\"Error reading input.\", stderr); exit(1);} return ret;}"
               + "float IO_inputFloat(IO_struct* self) {float ret; if(scanf(\"%f\", &ret) == EOF) {fputs(\"Error reading input.\", stderr); exit(1);} return ret;}"
               + "char IO_inputBool(IO_struct* self) {char ret; if(scanf(\"%c\", &ret) == EOF) {fputs(\"Error reading input.\", stderr); exit(1);} return ret? 0x01 : 0x00;}");
      
      //From a first pass, generate the structures that classes rely are
      genTypedefs(ast, true);
      while(classes.size() > 0)
         genStructures(ast, true, false);
      //From a second pass, generate the list of all declared local variable for each
      //method, to build the "let"'s later on.
      registerLets(ast, null, null, 0);
      //From a third pass, generate the list of all intermediate variable for each
      //instruction, to build them later on.
      registerInsts(null, ast, null, null);
      //Add the special String_equals method that is defined by ourselves
      if(extd) {
         sb.append("char String_equals(String_struct* self, Object_struct* b) {return strcmp(self->value, ((String_struct*) b)->value) == 0;}");
         sb.append("int String_size(String_struct* self) {return strlen(self->value);}");
         sb.append("String_struct* String_a(String_struct* self, char* s) {int slen = strlen(s); if(self->rsize <= self->len + slen) { self->rsize = "
                  + "2*(self->len + slen) + 1; self->value = GC_REALLOC(self->value, self->rsize);} strcat(self->value, s); return self;}");
         sb.append("int String_asciiAt(String_struct* self, int i) {return (int)self->value[i];}");
         sb.append("int String_code(String_struct* self) {int hash = 0; char c, *s = self->value; for (; c = *s; ++s) hash = 31 * hash + c; return hash;}");
      }
      //From a fourth pass, generate the methods that are functions that have a pointer *self
      //Fourth pass is actually deeper in first pass
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
            pb = new ProcessBuilder("clang", "-x", "c", "-Oz", "-o", file, "-S", "-emit-llvm", "-I/usr/local/gc/include", "-");
         } else {
            ArrayList<String> params = new ArrayList<String>();
            params.add("clang");
            params.add("-x");
            params.add("c");
            params.add("-Ofast");
            params.add("-o");
            params.add(file);
            params.add("-I/usr/local/gc/include");
            params.add("-static");
            params.add("-");
            params.add("-L/usr/local/gc/lib");
            params.add("-lgc");
            for(int i = where; i < args.length; i++) {
               if(args[i].startsWith("-cl"))
                  params.add(args[i].replaceFirst("-cl", "-l"));
            }
            pb = new ProcessBuilder(params);
         }
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
    * Generate typedefs into builder.
    * @param root AST.
    * @param pass Reached classes.
    */
   private void genTypedefs(ASTNode root, boolean pass) {
      String type;
      
      if(root.ending) {
         switch(root.itype) {
            case SymbolValue.CLASS:
               type = root.getChildren().get(0).getValue().toString();
               sb.append("typedef struct " + type + "_struct " + type + "_struct; ");
               break;
            default:
               break;
         }
      }
      
      if(pass) {
         for(ASTNode r : root.getChildren()) {
            genTypedefs(r, false);
         }
      }
   }
   
   /**
    * Generate structures into builder.
    * @param root AST.
    * @param pass Reached classes.
    * @param second Second pass.
    */
   @SuppressWarnings("unchecked")
   private void genStructures(ASTNode root, boolean pass, boolean second) {
      int shift = 0, j;
      CClassRecord c;
      String type = "", upper = "";
      ASTNode a, vclass;
      ArrayList<String> fields, types;
      ArrayList<String> methods;
      ArrayList<CSignature> sigs;
      Stack<ASTNode> msee;
      Stack<String> rlabel;
      HashMap<String, HashMap<String, ScopeItem>> prim;
      
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
                  sb.append("struct " + type + "_struct {" + type + "_vtable* _vtable; ");
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
                  sb.append("}; ");
                  
                  //Now getting to the vtable definition, which may be inherited
                  //Prepare vtable definition
                  methods = new ArrayList<String>();
                  sigs = new ArrayList<CSignature>();
                  msee = new Stack<ASTNode>();
                  rlabel = new Stack<String>();
                  do {
                     vclass = ast.scope.get(ScopeItem.CLASS, type).userType;
                     if(vclass.getChildren().size() > 1) {
                        for(ASTNode at : vclass.getChildren()) {
                           msee.push(at);
                           rlabel.push(type);
                        }
                     } else {
                        //This is a self defined class, Object, IO, ...
                        prim = (HashMap<String, HashMap<String, ScopeItem>>) vclass.getProp("prim");
                        for(Map.Entry<String, ScopeItem> entry : prim.get(type).entrySet())  {
                           a = new ASTNode("method", null).addChild(new ASTNode(SymbolValue.OBJECT_IDENTIFIER, entry.getKey()))
                                    .addProp("type", entry.getValue().userType.getProp("type"));
                           if(entry.getValue().formals != null)
                              a.addChild(entry.getValue().formals);
                           else
                              a.addChild(new ASTNode("dummy", null));
                           msee.push(a);
                           rlabel.push(type);
                        }
                     }
                  } while(!(type = ext.get(type)).equals(Analyzer.EMPTY));
                  type = root.getChildren().get(0).getValue().toString();
                  while(!msee.isEmpty()) {
                     a = msee.pop();
                     String from = rlabel.pop();
                     if(type.equals(from)) {
                        tryAddSig(a, type, methods, sigs, true, false);
                     } else {
                        tryAddSig(a, type, methods, sigs, false, false);
                     }
                  }
                  //_vtable definition and one static instance
                  sb.append("struct " + type + "_vtable {");
                  //Write the fields into the C code
                  for(int i = 0; i < methods.size(); i++) {
                     sb.append(sigs.get(i).ret + " (*" + methods.get(i) + ")(");
                     for(j = 0; j < sigs.get(i).pnames.size(); j++) {
                        sb.append(sigs.get(i).ptypes.get(j) + ",");
                     }
                     if(sigs.get(i).pnames.size() > 0)
                        sb.deleteCharAt(sb.length() - 1);
                     sb.append("); ");
                  }
                  sb.append("}; ");
                  //Create the static instance
                  sb.append(type + "_vtable " + type + "_static_vtable = {");
                  //Write the fields into the C code
                  for(int i = 0; i < methods.size(); i++) {
                     do {
                        vclass = ast.scope.get(ScopeItem.CLASS, type).userType;
                        if(vclass.scope.methodSet().contains(methods.get(i))) {
                           upper = type;
                           break;
                        } else {
                           //This may be a self defined class, Object, IO, ...
                           prim = (HashMap<String, HashMap<String, ScopeItem>>) vclass.getProp("prim");
                           if(prim != null && prim.get(type).containsKey(methods.get(i))) {
                              upper = type;
                              break;
                           }
                        }
                     } while(!(type = ext.get(type)).equals(Analyzer.EMPTY));
                     type = root.getChildren().get(0).getValue().toString();
                     sb.append(upper + "_" + methods.get(i) + ",");
                  }
                  if(methods.size() > 0 || Analyzer.isSameOrChild(ext, type, "IO"))
                     sb.deleteCharAt(sb.length() - 1);
                  sb.append("}; ");
                  
                  //Now adding a new function for init
                  //Add init method for this class
                  sb.append(type + "_struct* " + type + "_init__(" + type + "_struct* self) {");
                  //Build "super();" call
                  if(!ext.get(type).equals("Object")) {
                     sb.append(ext.get(type) + "_init__(self); ");
                  }
                  sb.append("self->_vtable = &" + type + "_static_vtable; ");
                  //Register class then close so far the init, after having saved where to insert.
                  ast.scope.put(ScopeItem.CTYPE, type, new CClassRecord(fields, types, methods, sigs, sb.length(), sb.length()));
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
         String cname = root.getChildren().get(0).getValue().toString();
         c = (CClassRecord) ast.scope.getLLVM(ScopeItem.CTYPE, cname);
         //Build the whole of the class
         for(ASTNode r : root.getChildren()) {
            genFunctions(cname, r);
         }
         //We can now close the _init__ method.
         if(c.printed == false) {
            //Now add local defined by "let"'s variables.
            for(int i = 0; i < lets.get(cname + "_" + null).size(); i++) {
               sb.insert(c.inits, letstypes.get(cname + "_" + null).get(i) + " " + lets.get(cname + "_" + null).get(i) + "; ");
               shift += (letstypes.get(cname + "_" + null).get(i) + " " + lets.get(cname + "_" + null).get(i) + "; ").length();
            }
            //Now add local defined by instruction variables.
            for(Map.Entry<ASTNode, String> iname : imapping.get(cname + "_" + null).entrySet()) {
               sb.insert(c.inits, itypes.get(cname + "_" + null).get(iname.getKey()) + " " + iname.getValue() + "; ");
               shift += (itypes.get(cname + "_" + null).get(iname.getKey()) + " " + iname.getValue() + "; ").length();
            }
            //All classes defined after us must shift were to insert
            j = cdef.indexOf(cname);
            for(int i = j + 1; i < cdef.size(); i++) {
               ((CClassRecord) ast.scope.getLLVM(ScopeItem.CTYPE, cdef.get(i))).initb += shift;
               ((CClassRecord) ast.scope.getLLVM(ScopeItem.CTYPE, cdef.get(i))).inits += shift;
            }
            c.printed = true;
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
         lets.put(cname + "_" + null, new ArrayList<String>());
         letstypes.put(cname + "_" + null, new ArrayList<String>());
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
            case SymbolValue.SWITCH:
            case SymbolValue.EQUAL:
            case SymbolValue.AND:
            case SymbolValue.OR:
            case SymbolValue.LOWER_EQUAL:
            case SymbolValue.LOWER:
            case SymbolValue.GREATER_EQUAL:
            case SymbolValue.GREATER:
            case SymbolValue.SHL:
            case SymbolValue.SHR:
            case SymbolValue.PLUS:
            case SymbolValue.MINUS:
            case SymbolValue.TIMES:
            case SymbolValue.DIV:
            case SymbolValue.POW:
            case SymbolValue.ISNULL:
            case SymbolValue.NEW:
            case SymbolValue.ASSIGN:
            case SymbolValue.ERASE:
            case SymbolValue.INTEGER_LITERAL:
            case SymbolValue.FLOAT_LITERAL:
            case SymbolValue.STRING_LITERAL:
            case SymbolValue.FALSE:
            case SymbolValue.TRUE:
            case SymbolValue.UNIT_VALUE:
            case SymbolValue.NULL:
               imapping.get(cname + "_" + mname).put(root, "_inst__" + CGen.randomString());
               itypes.get(cname + "_" + mname).put(root, CGen.localType(root.getProp("type").toString()));
               break;
            case SymbolValue.OBJECT_IDENTIFIER:
               //Skip if we are the name of a method, or the name of the method in a call
               if(!parent.stype.equals("method") && !(parent.stype.equals("call") && parent.getChildren().get(1) == root)) {
                  si = root.scope.getBeforeClassLevel(ScopeItem.FIELD, root.getValue().toString());
                  if(si == null)
                     si = Analyzer.findFieldAbove(ext, root, (parent.stype.equals("fieldget") && parent.getChildren().get(1) == root)?
                              parent.getChildren().get(0).getProp("type").toString() : cname);
                  imapping.get(cname + "_" + mname).put(root, "_inst__" + CGen.randomString());
                  itypes.get(cname + "_" + mname).put(root, CGen.localType(si.userType.getProp("type").toString()));
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
               imapping.get(cname + "_" + mname).put(root, "_inst__" + CGen.randomString());
               itypes.get(cname + "_" + mname).put(root, CGen.localType(root.getProp("type").toString()));
               break;
            case "if":
               brlabels.put(root, new String[] {"_if__" + CGen.randomString(), "_else__" + CGen.randomString(), "_endif__" + CGen.randomString()});
            case "call": //Fallthrough
            case "assign":
            case "block":
            case "let":
            case "uminus":
            case "fieldget":
            case "cast":
            case "deref":
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
      String stars = "";
      String tps[] = type.split(":");
      for(int i = 1; i < tps.length; i++)
         stars += "*";

      switch(tps[tps.length - 1]) {
         case "int32":
            return "int" + stars;
         case "float":
            return "float" + stars;
         case "byte":
         case "bool":
         case "unit":
            return "char" + stars;
         case "string":
            return "char*" + stars;
         default:
            //Is a pointer
            return tps[tps.length - 1] + "_struct*" + stars;
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
      String stars = "";
      String tps[] = type.split(":");
      for(int i = 1; i < tps.length; i++)
         stars += "*";
      
      switch(tps[tps.length - 1]) {
         case "int32":
            sb.append("int" + stars + " " + fname + "; ");
            break;
         case "float":
            sb.append("float" + stars + " " + fname + "; ");
            break;
         case "byte":
         case "bool":
         case "unit":
            sb.append("char" + stars + " " + fname + "; ");
            break;
         case "string":
            sb.append("char*" + stars + " " + fname + "; ");
            break;
         default:
            //Is a pointer
            sb.append(tps[tps.length - 1] + "_struct*" + stars + " " + fname + "; ");
            break;
      }
   }

   /**
    * Generate functions into builder.
    * @param cname Class name for the following methods.
    * @param root Where we are.
    */
   private void genFunctions(String cname, ASTNode root) {
      boolean leave;
      int shift = 0, j;
      String mname;
      StringBuilder save, tmp;
      CClassRecord c;
      
      if(root.ending == false) {
         c = (CClassRecord) ast.scope.getLLVM(ScopeItem.CTYPE, cname);
         switch(root.stype) {
            case "method":
               //Do not build String_equals and other natives
               if(cname.equals("String")) {
                  switch(root.getChildren().get(0).getValue().toString()) {
                     case "equals":
                     case "size":
                     case "a":
                     case "asciiAt":
                     case "code":
                        leave = true;
                        break;
                     default:
                        leave = false;
                        break;
                  }
                  if(leave)
                     break;
               }
               
               //Now building normal and included methods
               //Start building method
               tryAddSig(root, cname, new ArrayList<String>(), new ArrayList<CSignature>(), true, true);
               sb.deleteCharAt(sb.length() - 1);
               sb.append(" {");
               mname = root.getChildren().get(0).getValue().toString();
               //Build External.*_.* from includes
               if(cname.startsWith("External")) {
                  sb.append("return " + mname + "(");
                  if(root.getChildren().size() > 3) {
                     for(int i = 0; i < root.getChildren().get(1).getChildren().size(); i++) {
                        sb.append(root.getChildren().get(1).getChildren().get(i).getChildren().get(0).getValue().toString() + ",");
                     }
                     sb.deleteCharAt(sb.length() - 1);
                  }
                  sb.append(");} ");
               } else {
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
               }
               break;
            case "field":
               if(root.getChildren().size() > 2) {
                  //Build init body
                  save = sb;
                  tmp = sb = new StringBuilder();
                  buildBody(root, cname, null, root.getChildren().get(2), 0);
                  sb = save;
                  sb.insert(c.initb, tmp.toString() + "; ");
                  shift = (tmp.toString() + "; ").length();
                  c.initb += shift;
                  //Initialized
                  sb.insert(c.initb, "self->" + root.getChildren().get(0).getValue().toString() + " = ");
                  shift += ("self->" + root.getChildren().get(0).getValue().toString() + " = ").length();
                  c.initb += ("self->" + root.getChildren().get(0).getValue().toString() + " = ").length();
                  sb.insert(c.initb, imapping.get(cname + "_" + null).get(root.getChildren().get(2)) + "; ");
                  shift += (imapping.get(cname + "_" + null).get(root.getChildren().get(2)) + "; ").length();
                  c.initb += (imapping.get(cname + "_" + null).get(root.getChildren().get(2)) + "; ").length();
               } else {
                  //Unitialized!
                  sb.insert(c.initb, "self->" + getDefaultForType(root.getChildren().get(0).getValue().toString(), root.getChildren().get(1).getProp("type").toString(), true));
                  shift = ("self->" + getDefaultForType(root.getChildren().get(0).getValue().toString(), root.getChildren().get(1).getProp("type").toString(), true)).length();
                  c.initb += shift;
               }
               //All classes defined after us must shift were to insert
               j = cdef.indexOf(cname);
               for(int i = j + 1; i < cdef.size(); i++) {
                  ((CClassRecord) ast.scope.getLLVM(ScopeItem.CTYPE, cdef.get(i))).initb += shift;
                  ((CClassRecord) ast.scope.getLLVM(ScopeItem.CTYPE, cdef.get(i))).inits += shift;
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
    * @param rself True if a self enclosed string.
    */
   private String getDefaultForType(String field, String type, boolean rself) {
      switch (type) {
         case "int32":
            return field + " = 0; ";
         case "float":
            return field + " = 0.0f; ";
         case "byte":
         case "bool":
         case "unit":
            return field + " = 0; ";
         case "string":
            return field + " = GC_MALLOC(sizeof(char)); " + (rself? "self->" : "") + field + "[0] = 0; ";
         default:
            //Is a pointer, or an array, eh, a pointer whatever
            return field + " = (void*)0; ";
      }
   }
   
   /**
    * Turns a deref subtree into a stack.
    * @param root First deref node.
    * @return Stack.
    */
   private static Stack<ASTNode> derefToStack(ASTNode root) {
      Stack<ASTNode> deref = new Stack<ASTNode>();
      for(; root.stype.equals("deref"); root = root.getChildren().get(1)) {
         deref.add(0, root.getChildren().get(0));
      }
      return deref;
   }

  /**
   * Add function body.
   * @param parent Parent node.
   * @param cname Class name.
   * @param mname Method name.
   * @param root Current node.
   * @param nlets Imrication level for "let"'s.
   * @return Created return variable name if any is created for above block.
   */
   private void buildBody(ASTNode parent, String cname, String mname, ASTNode root, int nlets) {
      boolean has;
      int top;
      String tmp = null, drf;
      Stack<ASTNode> deref;
      Stack<String> ainit;

      if(root.stype.equals("if")) {
         buildBody(root, cname, mname, root.getChildren().get(0), nlets);
         sb.append("if(" + imapping.get(cname + "_" + mname).get(root.getChildren().get(0)) + ")");
         sb.append("goto " + brlabels.get(root)[0] + "; else goto " + brlabels.get(root)[1] + ";");
         sb.append(brlabels.get(root)[0] + ":");
         buildBody(root, cname, mname, root.getChildren().get(1), nlets);
         if(root.getChildren().size() > 2)
            sb.append(imapping.get(cname + "_" + mname).get(root) + " = " + imapping.get(cname + "_" + mname).get(root.getChildren().get(1)) + ";");
         else
            sb.append(imapping.get(cname + "_" + mname).get(root) + " = 0;");
         sb.append("goto " + brlabels.get(root)[2] + ";" + brlabels.get(root)[1] + ":");
         if(root.getChildren().size() > 2) {
            buildBody(root, cname, mname, root.getChildren().get(2), nlets);
            sb.append(imapping.get(cname + "_" + mname).get(root) + " = " + imapping.get(cname + "_" + mname).get(root.getChildren().get(2)) + ";");
         } else {
            sb.append(imapping.get(cname + "_" + mname).get(root) + " = 0;");
         }
         sb.append("goto " + brlabels.get(root)[2] + ";");
         sb.append(brlabels.get(root)[2] + ":");
         return;
      } else if(root.stype.equals("while")) {
         sb.append(brlabels.get(root)[0] + ":");
         buildBody(root, cname, mname, root.getChildren().get(0), nlets);
         sb.append("if(" + imapping.get(cname + "_" + mname).get(root.getChildren().get(0)) + ") {");
         buildBody(root, cname, mname, root.getChildren().get(1), nlets);
         sb.append("goto " + brlabels.get(root)[0] + "; } else { ");
         sb.append(imapping.get(cname + "_" + mname).get(root) + " = 0;");
         sb.append("goto " + brlabels.get(root)[1] + ";}");
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
            sb.append(getDefaultForType(tmp, root.getChildren().get(1).getProp("type").toString(), false));
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

      if(root.ending) {
         switch(root.itype) {
            case SymbolValue.NOT:
               if(root.getProp("type").toString().equals("bool"))
                  sb.append(imapping.get(cname + "_" + mname).get(root) + " = !" + imapping.get(cname + "_" + mname).get(root.getChildren().get(0)) + ";");
               else
                  sb.append(imapping.get(cname + "_" + mname).get(root) + " = ~" + imapping.get(cname + "_" + mname).get(root.getChildren().get(0)) + ";");
               break;
            case SymbolValue.SWITCH:
               sb.append(imapping.get(cname + "_" + mname).get(root) + " = (" + (root.getProp("type").equals("int32")? "int" : "float")
                        + ")" + imapping.get(cname + "_" + mname).get(root.getChildren().get(0)) + ";");
               break;
            case SymbolValue.EQUAL:
               if(root.getChildren().get(0).getProp("type").equals("string") && root.getChildren().get(1).getProp("type").equals("string"))
                  sb.append(imapping.get(cname + "_" + mname).get(root) + " = strcmp(" + imapping.get(cname + "_" + mname).get(root.getChildren().get(0)) + ", " +
                           imapping.get(cname + "_" + mname).get(root.getChildren().get(1)) + ") == 0;");
               else
                  sb.append(imapping.get(cname + "_" + mname).get(root) + " = (" + imapping.get(cname + "_" + mname).get(root.getChildren().get(0)) + ") == (" +
                           imapping.get(cname + "_" + mname).get(root.getChildren().get(1)) + ");");
               break;
            case SymbolValue.AND:
               sb.append(imapping.get(cname + "_" + mname).get(root) + " = (" + imapping.get(cname + "_" + mname).get(root.getChildren().get(0)) + ") & (" +
                        imapping.get(cname + "_" + mname).get(root.getChildren().get(1)) + ");");
               break;
            case SymbolValue.OR:
               sb.append(imapping.get(cname + "_" + mname).get(root) + " = (" + imapping.get(cname + "_" + mname).get(root.getChildren().get(0)) + ") | (" +
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
            case SymbolValue.GREATER_EQUAL:
               sb.append(imapping.get(cname + "_" + mname).get(root) + " = (" + imapping.get(cname + "_" + mname).get(root.getChildren().get(0)) + ") >= (" +
                        imapping.get(cname + "_" + mname).get(root.getChildren().get(1)) + ");");
               break;
            case SymbolValue.GREATER:
               sb.append(imapping.get(cname + "_" + mname).get(root) + " = (" + imapping.get(cname + "_" + mname).get(root.getChildren().get(0)) + ") > (" +
                        imapping.get(cname + "_" + mname).get(root.getChildren().get(1)) + ");");
               break;
            case SymbolValue.SHL:
               sb.append(imapping.get(cname + "_" + mname).get(root) + " = (" + imapping.get(cname + "_" + mname).get(root.getChildren().get(0)) + ") << (" +
                        imapping.get(cname + "_" + mname).get(root.getChildren().get(1)) + ");");
               break;
            case SymbolValue.SHR:
               sb.append(imapping.get(cname + "_" + mname).get(root) + " = (" + imapping.get(cname + "_" + mname).get(root.getChildren().get(0)) + ") >> (" +
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
               if(root.getProp("type").equals("int32"))
                  sb.append(imapping.get(cname + "_" + mname).get(root) + " = __pow((" + imapping.get(cname + "_" + mname).get(root.getChildren().get(0)) + "), (" +
                           imapping.get(cname + "_" + mname).get(root.getChildren().get(1)) + "));");
               else
                  sb.append(imapping.get(cname + "_" + mname).get(root) + " = __powf((" + imapping.get(cname + "_" + mname).get(root.getChildren().get(0)) + "), (" +
                           imapping.get(cname + "_" + mname).get(root.getChildren().get(1)) + "));");
               break;
            case SymbolValue.ISNULL:
               sb.append(imapping.get(cname + "_" + mname).get(root) + " = (" + imapping.get(cname + "_" + mname).get(root.getChildren().get(0)) + ") == 0;");
               break;
            case SymbolValue.NEW:
               if(root.getChildren().size() < 2)
                  sb.append(imapping.get(cname + "_" + mname).get(root) + " = " + root.getProp("type").toString() + "_init__(GC_MALLOC(sizeof(" + root.getProp("type").toString() + "_struct)));");
               else {
                  ainit = new Stack<String>();
                  deref = CGen.derefToStack(root.getChildren().get(1));
                  tmp = root.getProp("type").toString().replaceFirst("\\[\\]:", "");
                  for(int i = 0; i < deref.size() - 1; i++)
                     ainit.push("_new__" + CGen.randomString());
                  sb.append(imapping.get(cname + "_" + mname).get(root) + " = GC_MALLOC(" + imapping.get(cname + "_" + mname).get(deref.peek()) + "*sizeof(" + CGen.localType(tmp) + "));");
                  for(int i = deref.size() - 2; i >= 0; i--) {
                     sb.append("for(int " + ainit.get(i) + " = 0; " + ainit.get(i) + " < " + imapping.get(cname + "_" + mname).get(deref.get(i + 1)) + "; " + ainit.get(i) + "++) {");
                     tmp = tmp.replaceFirst("\\[\\]:", "");
                     sb.append(imapping.get(cname + "_" + mname).get(root));
                     for(int j = i; j < deref.size() - 1; j++)
                        sb.append("[" + ainit.get(j) + "]");
                     sb.append(" = GC_MALLOC(" + imapping.get(cname + "_" + mname).get(deref.get(i)) + "*sizeof(" + CGen.localType(tmp) + "));");
                  }
                  for(int i = deref.size() - 2; i >= 0; i--) {
                     sb.append("}");
                  }
               }
               break;
            case SymbolValue.ASSIGN:
               sb.append(imapping.get(cname + "_" + mname).get(root) + " = " + imapping.get(cname + "_" + mname).get(root.getChildren().get(0)) + ";");
               break;
            case SymbolValue.ERASE:
               sb.append("GC_FREE(" + imapping.get(cname + "_" + mname).get(root.getChildren().get(0)) + ");");
               sb.append(imapping.get(cname + "_" + mname).get(root) + " = (void*)0;");
               break;
            case SymbolValue.INTEGER_LITERAL:
               sb.append(imapping.get(cname + "_" + mname).get(root) + " = " + (int)root.getValue() + ";");
               break;
            case SymbolValue.FLOAT_LITERAL:
               sb.append(imapping.get(cname + "_" + mname).get(root) + " = " + (float)root.getValue() + ";");
               break;
            case SymbolValue.STRING_LITERAL:
               sb.append(imapping.get(cname + "_" + mname).get(root) + " = " + regName(root.getValue().toString()) + ";");
               break;
            case SymbolValue.OBJECT_IDENTIFIER:
               //Get back position in array
               drf = "";
               if(root.getChildren().size() > 0) {
                  deref = CGen.derefToStack(root.getChildren().get(0));
                  for(int i = deref.size() - 1; i >= 0; i--)
                     drf += "[" + imapping.get(cname + "_" + mname).get(deref.get(i)) + "]";
               }
               //Skip if we are the name of a method, or the name of the method in a call, or the accessed member of fieldget
               if(parent.stype.equals("method") || (parent.stype.equals("call") && parent.getChildren().get(1) == root) ||
                        (parent.stype.equals("fieldget") && parent.getChildren().get(1) == root))
                  break;
               if(root.getValue().toString().equals("self"))
                  sb.append(imapping.get(cname + "_" + mname).get(root) + " = self;");
               else if(root.scope.getBeforeClassLevel(ScopeItem.FIELD, root.getValue().toString()) == null) {
                  sb.append(imapping.get(cname + "_" + mname).get(root) + " = self->" + root.getValue().toString() + drf + ";");
               } else {
                  top = nlets;
                  do {
                     if((tmp = lmapping.get(cname + mname + top + ">" + root.getValue().toString())) != null) {
                        sb.append(imapping.get(cname + "_" + mname).get(root) + " = " + lmapping.get(cname + mname + top + ">" + root.getValue().toString()) + drf + ";");
                        break;
                     }
                     top--;
                  } while(top > -1);
                  if(top == -1) {
                     sb.append(imapping.get(cname + "_" + mname).get(root) + " = " + root.getValue().toString() + drf + ";");
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
            case SymbolValue.NULL:
               sb.append(imapping.get(cname + "_" + mname).get(root) + " = (void*)0;");
               break;
            case SymbolValue.INT32:
            case SymbolValue.FLOAT:
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
            case "deref":
               sb.append(imapping.get(cname + "_" + mname).get(root) + " = " + imapping.get(cname + "_" + mname).get(root.getChildren().get(0)) + ";");
               break;
            case "fieldget":
               //Get back position in array
               drf = "";
               if(root.getChildren().size() > 2) {
                  deref = CGen.derefToStack(root.getChildren().get(2));
                  for(int i = deref.size() - 1; i >= 0; i--)
                     drf += "[" + imapping.get(cname + "_" + mname).get(deref.get(i)) + "]";
               }
               sb.append(imapping.get(cname + "_" + mname).get(root) + " = (" + imapping.get(cname + "_" + mname).get(root.getChildren().get(0)) + ")->"
                        + root.getChildren().get(1).getValue().toString() + drf + ";");
               break;
            case "cast":
               sb.append(imapping.get(cname + "_" + mname).get(root) + " = (" + root.getProp("type") + "_struct*"
                        + ")" + imapping.get(cname + "_" + mname).get(root.getChildren().get(0)) + ";");
               break;
            case "call":
               has = root.getChildren().size() > 2;
               //Do a dynamic dispatch if not required to use a static one
               if(extd == false || root.getProp("cast") == null)
                  sb.append(imapping.get(cname + "_" + mname).get(root) + " = " + imapping.get(cname + "_" + mname).get(root.getChildren().get(0)) + "->_vtable->" +
                           root.getChildren().get(1).getValue().toString() + "(" + imapping.get(cname + "_" + mname).get(root.getChildren().get(0)) + ",");
               else
                  sb.append(imapping.get(cname + "_" + mname).get(root) + " = " + root.getProp("cast").toString() + "_" +
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
               //Get back position in array
               drf = "";
               if(root.getChildren().size() > 2) {
                  deref = CGen.derefToStack(root.getChildren().get(2));
                  for(int i = deref.size() - 1; i >= 0; i--)
                     drf += "[" + imapping.get(cname + "_" + mname).get(deref.get(i)) + "]";
               }
               //Build assign
               if(root.getChildren().get(0).stype.equals("fieldget")) {
                  sb.append(imapping.get(cname + "_" + mname).get(root) + " = ((" + imapping.get(cname + "_" + mname).get(root.getChildren().get(0).getChildren().get(0))
                           + ")->" + root.getChildren().get(0).getChildren().get(1).getValue().toString() + drf + ") = ("
                           + imapping.get(cname + "_" + mname).get(root.getChildren().get(1)) + ");");
               } else {
                  has = (root.scope.getBeforeClassLevel(ScopeItem.FIELD, root.getChildren().get(0).getValue().toString()) == null);
                  if(has == false) {
                     top = nlets;
                     do {
                        if((tmp = lmapping.get(cname + mname + top + ">" + root.getChildren().get(0).getValue().toString())) != null)
                           break;
                        top--;
                     } while(top > -1);
                     if(top == -1) {
                        tmp = root.getChildren().get(0).getValue().toString();
                     }
                  }
                  sb.append(imapping.get(cname + "_" + mname).get(root) + " = (" + (has? "self->" +
                           root.getChildren().get(0).getValue().toString() : tmp) + drf + ") = (" +  imapping.get(cname + "_" + mname).get(root.getChildren().get(1)) + ");");
               }
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
            case "dummy":
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
