package be.ac.ulg.vsop.codegen;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

import org.bridj.Pointer;
import org.llvm.binding.LLVMLibrary;
import org.llvm.binding.LLVMLibrary.LLVMModuleRef;
import org.llvm.binding.LLVMLibrary.LLVMTypeRef;
import org.llvm.binding.LLVMLibrary.LLVMValueRef;

import be.ac.ulg.vsop.analyzer.Analyzer;
import be.ac.ulg.vsop.analyzer.ScopeItem;
import be.ac.ulg.vsop.parser.ASTNode;
import be.ac.ulg.vsop.parser.SymbolValue;


public class CGen {
   
   private ASTNode ast;
   private HashMap<String, String> ext;
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
      
      sb = new StringBuilder();
      
      //From a first pass, generate the structures that class rely are
      while(classes.size() > 0)
         genStructures(ast, true, false);
      //From a second pass, generate the methods that are functions that have a pointer *self
      //Second pass is actually deeper in first pass
      genStructures(ast, true, true);
      
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
               }
               
               //Now ading a new function for init
               //Add init method for this class
               /*
               type = root.getChildren().get(0).getValue().toString();
               ptr = Pointer.allocateArray(LLVMTypeRef.class, 1);
               ptr.set(0, LLVMLibrary.LLVMPointerType(st, 1));
               fsig = LLVMLibrary.LLVMFunctionType(LLVMLibrary.LLVMVoidType(), ptr, 1, 0); //Method sig, ie: void Class<init>(Class *this);
               fbody = LLVMLibrary.LLVMAddFunction(m, Pointer.allocateArray(Byte.class, 6), fsig); //Method body, will allocate all fields.
               ast.scope.put(ScopeItem.LLVMVALUE, ScopeItem.METHOD + type + "<init>", fbody);
               //Prepare builder so that to send it to pass
               LLVMLibrary.LLVMBasicBlockRef bb = LLVMLibrary.LLVMAppendBasicBlock(fbody, Pointer.allocateArray(Byte.class, 6));
               builder = LLVMLibrary.LLVMCreateBuilder();
               LLVMLibrary.LLVMPositionBuilderAtEnd(builder, bb);
               //Save this class for this build
               ast.scope.put(ScopeItem.LLVMTYPE, type, new ClassRecord(st, fields, tps, fbody, builder));
               //Build "super();" call
               if(!ext.get(type).equals("Object")) {
                  args = Pointer.allocate(LLVMValueRef.class);
                  //TODO: Find a dynamic cast that works!
                  args.set(LLVMLibrary.LLVMBuildCast(builder, LLVMLibrary.LLVMOpcode.LLVMAnd, LLVMLibrary.LLVMGetParam(fbody, 0),
                           ((ClassRecord) ast.scope.getLLVM(ScopeItem.LLVMTYPE, ext.get(type))).st, Pointer.allocateArray(Byte.class, 6)));
                  LLVMLibrary.LLVMBuildCall(builder, (LLVMValueRef) ast.scope.getLLVM(ScopeItem.LLVMVALUE, ScopeItem.METHOD + ext.get(type) + "<init>"), args, 1,
                           Pointer.allocateArray(Byte.class, 6));
               }
               */
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
               sb.append("); ");
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
      boolean has;
      ASTNode fn;
      Pointer<LLVMTypeRef> types;
      LLVMTypeRef fsig;
      LLVMValueRef fbody, asgn;
      LLVMLibrary.LLVMBuilderRef initb = ((ClassRecord) root.scope.getLLVM(ScopeItem.LLVMTYPE, cname)).initb;

      if(root.ending == false) {
         switch(root.stype) {
            case "method":
               //As methods are ALWAYS after fields, buidl the end of <init> at first method
               if(initb != null) {
                  LLVMLibrary.LLVMBuildRet(initb, ((ClassRecord) root.scope.getLLVM(ScopeItem.LLVMTYPE, cname)).fbody);
                  //This creates several returns at end of function, but, who cares?
               }

               //Start building method
               /*
               has = root.getChildren().get(1).stype.equals("formals");
               if(has) {
                  ASTNode fms = root.getChildren().get(1);
                  types = Pointer.allocateArray(LLVMTypeRef.class, fms.getChildren().size() + 1);
                  for(int i = 1; i < fms.getChildren().size() + 1; i++) {
                     fn = fms.getChildren().get(i - 1).getChildren().get(0);
                     types.set(i, llvmType(fn, fn.getProp("type").toString()));
                  }
               } else {
                  types = Pointer.allocateArray(LLVMTypeRef.class, 1);
               }
               //Add self as parameter to the function
               types.set(0, llvmType(root, cname));
               //Create method declaration
               fsig = LLVMLibrary.LLVMFunctionType(llvmType(root, root.getProp("type").toString()), types,
                       has? 1 + root.getChildren().get(1).getChildren().size() : 1, 0);
               //Name the method as global function and register it
               fbody = LLVMLibrary.LLVMAddFunction(m, Pointer.allocateArray(Byte.class, 6), fsig);
               ast.scope.put(ScopeItem.LLVMVALUE, ScopeItem.METHOD + cname + root.getChildren().get(0).getValue().toString(), fbody);
               //Register params for the build of body
               root.scope.put(ScopeItem.LLVMVALUE, ScopeItem.FIELD + "self", LLVMLibrary.LLVMGetParam(fbody, 0));
               if(has) {
                  ASTNode fms = root.getChildren().get(1);
                  for(int i = 0; i < fms.getChildren().size(); i++)
                     root.scope.put(ScopeItem.LLVMVALUE, ScopeItem.FIELD + fms.getChildren().get(i).getChildren().get(0).getValue().toString(),
                              LLVMLibrary.LLVMGetParam(fbody, i + 1));
               }
               //Add method body
               LLVMLibrary.LLVMBasicBlockRef bb = LLVMLibrary.LLVMAppendBasicBlock(fbody, Pointer.allocateArray(Byte.class, 6));
               LLVMLibrary.LLVMBuilderRef builder = LLVMLibrary.LLVMCreateBuilder();
               LLVMLibrary.LLVMPositionBuilderAtEnd(builder, bb);
               LLVMLibrary.LLVMBuildRet(builder, buildBody(cname, has? root.getChildren().get(2) : root.getChildren().get(1), builder));
               */
               break;
            case "field":
               if(root.getChildren().size() > 3) {
                  //Initialized
                  asgn = buildBody(cname, root.getChildren().get(2), initb);
               } else {
                  //Default initialized
                  asgn = getDefaultForType(root, root.getChildren().get(1).getProp("type").toString(), initb);
               }
               fbody = LLVMLibrary.LLVMGetParam(((ClassRecord) root.scope.getLLVM(ScopeItem.LLVMTYPE, cname)).fbody, 0);
               LLVMLibrary.LLVMBuildStore(initb, asgn, LLVMLibrary.LLVMBuildAdd(initb, fbody, LLVMLibrary.LLVMConstInt(LLVMLibrary.LLVMInt32Type(),
                        ((ClassRecord) root.scope.getLLVM(ScopeItem.LLVMTYPE, cname)).shift.get(root.getChildren().get(0).getValue().toString()), 0),
                        Pointer.allocateArray(Byte.class, 6)));
               root.scope.putAbove(ScopeItem.LLVMVALUE, ScopeItem.FIELD + root.getChildren().get(0).getValue().toString(), asgn, 1); //Register for the class
               break;
            default:
               break;
         }
      }
   }

   /**
    * Returns the default value for a type.
    * @param root Root.
    * @param type The type.
    * @param b Builder to build in.
    */
   private LLVMValueRef getDefaultForType(ASTNode root, String type, LLVMLibrary.LLVMBuilderRef b) {
      LLVMTypeRef t;
      LLVMValueRef tmp;
      
      switch (type) {
         case "int32":
            return LLVMLibrary.LLVMConstInt(LLVMLibrary.LLVMInt32Type(), 0, 1);
         case "bool":
            return LLVMLibrary.LLVMConstInt(LLVMLibrary.LLVMInt8Type(), 0, 1);
         case "String":
            //Pointer to well known String class instance, array of 1 zeroed-byte.
            t = LLVMLibrary.LLVMArrayType(LLVMLibrary.LLVMInt8Type(), 1);
            tmp = LLVMLibrary.LLVMBuildMalloc(b, t, Pointer.allocateArray(Byte.class, 6));
            LLVMLibrary.LLVMBuildStore(b, LLVMLibrary.LLVMConstInt(LLVMLibrary.LLVMInt8Type(), 0, 1), tmp);
            return tmp;
         case "unit":
            return LLVMLibrary.LLVMConstInt(LLVMLibrary.LLVMInt8Type(), 0, 1);
         default:
            //Is a pointer, assume we are 32-bit arch
            return LLVMLibrary.LLVMConstPointerNull(((ClassRecord) root.scope.getLLVM(ScopeItem.LLVMTYPE, type)).st);
      }
   }

  /**
   * Add function body.
   * @param cname Class name.
   * @param root Current node.
   * @param b Builder.
   */
   private LLVMLibrary.LLVMValueRef buildBody(String cname, ASTNode root, LLVMLibrary.LLVMBuilderRef b) {
      boolean has;
      String loop;
      LLVMTypeRef t;
      LLVMValueRef tmp;
      LLVMLibrary.LLVMBasicBlockRef b1, b2;
      LLVMLibrary.LLVMBuilderRef bd1, bd2;
      Pointer<LLVMValueRef> ptr;
      
      if(root.ending) {
         switch(root.itype) {
            case SymbolValue.NOT:
               return LLVMLibrary.LLVMBuildNot(b, buildBody(cname, root.getChildren().get(0), b), Pointer.allocateArray(Byte.class, 6));
            case SymbolValue.EQUAL:
               return LLVMLibrary.LLVMBuildICmp(b, LLVMLibrary.LLVMIntPredicate.LLVMIntEQ,
                       buildBody(cname, root.getChildren().get(0), b), buildBody(cname, root.getChildren().get(1), b), Pointer.allocateArray(Byte.class, 6));
            case SymbolValue.AND:
               return LLVMLibrary.LLVMBuildAnd(b, buildBody(cname, root.getChildren().get(0), b),
                       buildBody(cname, root.getChildren().get(1), b), Pointer.allocateArray(Byte.class, 6));
            case SymbolValue.LOWER_EQUAL:
               return LLVMLibrary.LLVMBuildICmp(b, LLVMLibrary.LLVMIntPredicate.LLVMIntSLE,
                       buildBody(cname, root.getChildren().get(0), b), buildBody(cname, root.getChildren().get(1), b), Pointer.allocateArray(Byte.class, 6));
            case SymbolValue.LOWER:
               return LLVMLibrary.LLVMBuildICmp(b, LLVMLibrary.LLVMIntPredicate.LLVMIntSLT,
                       buildBody(cname, root.getChildren().get(0), b), buildBody(cname, root.getChildren().get(1), b), Pointer.allocateArray(Byte.class, 6));
            case SymbolValue.PLUS:
               return LLVMLibrary.LLVMBuildAdd(b, buildBody(cname, root.getChildren().get(0), b),
                       buildBody(cname, root.getChildren().get(1), b), Pointer.allocateArray(Byte.class, 6));
            case SymbolValue.MINUS:
               return LLVMLibrary.LLVMBuildSub(b, buildBody(cname, root.getChildren().get(0), b),
                       buildBody(cname, root.getChildren().get(1), b), Pointer.allocateArray(Byte.class, 6));
            case SymbolValue.TIMES:
               return LLVMLibrary.LLVMBuildMul(b, buildBody(cname, root.getChildren().get(0), b),
                       buildBody(cname, root.getChildren().get(1), b), Pointer.allocateArray(Byte.class, 6));
            case SymbolValue.DIV:
               return LLVMLibrary.LLVMBuildSDiv(b, buildBody(cname, root.getChildren().get(0), b),
                       buildBody(cname, root.getChildren().get(1), b), Pointer.allocateArray(Byte.class, 6));
            case SymbolValue.POW:
               //TODO: Find POW instruction
               return LLVMLibrary.LLVMBuildMul(b, buildBody(cname, root.getChildren().get(0), b),
                       buildBody(cname, root.getChildren().get(1), b), Pointer.allocateArray(Byte.class, 6));
            case SymbolValue.ISNULL:
               return LLVMLibrary.LLVMBuildIsNull(b, buildBody(cname, root.getChildren().get(0), b), Pointer.allocateArray(Byte.class, 6));
            case SymbolValue.ASSIGN:
               return buildBody(cname, root.getChildren().get(0), b);
            case SymbolValue.NEW:
               tmp = LLVMLibrary.LLVMBuildMalloc(b, ((ClassRecord) root.scope.getLLVM(ScopeItem.LLVMTYPE, root.getProp("type").toString())).st,
                        Pointer.allocateArray(Byte.class, 6));
               ptr = Pointer.allocate(LLVMValueRef.class);
               ptr.set(0, tmp);
               return LLVMLibrary.LLVMBuildCall(b, (LLVMValueRef) root.scope.getLLVM(ScopeItem.LLVMVALUE, ScopeItem.METHOD + root.getProp("type").toString() + "<init>"),
                        ptr, 1, Pointer.allocateArray(Byte.class, 6));
            case SymbolValue.INTEGER_LITERAL:
               return LLVMLibrary.LLVMConstInt(LLVMLibrary.LLVMInt32Type(), (int)root.getValue(), 1);
            case SymbolValue.STRING_LITERAL:
               //Build the string on the heap, one char at a time sorry.
               t = LLVMLibrary.LLVMArrayType(LLVMLibrary.LLVMInt8Type(), root.getValue().toString().length() + 1);
               tmp = LLVMLibrary.LLVMBuildMalloc(b, t, Pointer.allocateArray(Byte.class, 6));
               for(int i = 0; i < root.getValue().toString().length(); i++)
                  LLVMLibrary.LLVMBuildStore(b, LLVMLibrary.LLVMConstInt(LLVMLibrary.LLVMInt8Type(), root.getValue().toString().charAt(i), 0),
                           LLVMLibrary.LLVMBuildAdd(b, tmp, LLVMLibrary.LLVMConstInt(LLVMLibrary.LLVMInt32Type(), i, 0), Pointer.allocateArray(Byte.class, 6)));
               LLVMLibrary.LLVMBuildStore(b, LLVMLibrary.LLVMConstInt(LLVMLibrary.LLVMInt8Type(), 0, 1),
                        LLVMLibrary.LLVMBuildAdd(b, tmp, LLVMLibrary.LLVMConstInt(LLVMLibrary.LLVMInt32Type(), root.getValue().toString().length(), 0),
                        Pointer.allocateArray(Byte.class, 6)));
               return tmp;
            case SymbolValue.OBJECT_IDENTIFIER:
               //Find in cscope the return. We know the scope is OK, right?
               return (LLVMValueRef) root.scope.getLLVM(ScopeItem.LLVMVALUE, ScopeItem.FIELD + root.getValue().toString());
            case SymbolValue.FALSE:
               return LLVMLibrary.LLVMConstInt(LLVMLibrary.LLVMInt8Type(), 0, 1);
            case SymbolValue.TRUE:
               return LLVMLibrary.LLVMConstInt(LLVMLibrary.LLVMInt8Type(), 1, 1);
            case SymbolValue.UNIT_VALUE:
               return LLVMLibrary.LLVMConstInt(LLVMLibrary.LLVMInt8Type(), 0, 1);
            case SymbolValue.INT32:
               return LLVMLibrary.LLVMBuildAlloca(b, LLVMLibrary.LLVMInt32Type(), Pointer.allocateArray(Byte.class, 6));
            case SymbolValue.BOOL:
               return LLVMLibrary.LLVMBuildAlloca(b, LLVMLibrary.LLVMInt8Type(), Pointer.allocateArray(Byte.class, 6));
            case SymbolValue.UNIT:
               return LLVMLibrary.LLVMBuildAlloca(b, LLVMLibrary.LLVMInt8Type(), Pointer.allocateArray(Byte.class, 6));
            case SymbolValue.STRING:
               return LLVMLibrary.LLVMBuildAlloca(b, LLVMLibrary.LLVMPointerType(LLVMLibrary.LLVMInt8Type(), 1), Pointer.allocateArray(Byte.class, 6));
            default:
               System.err.println("code generation error: should never get here: type " + ASTNode.typeValue(root));
               return null;
         }
      } else {
         switch(root.stype) {
            case "call":
               has = root.getChildren().size() > 2;
               ptr = Pointer.allocateArray(LLVMValueRef.class, has? 1 + root.getChildren().size() : 1);
               ptr.set(0, buildBody(cname, root.getChildren().get(0), b));
               if(has) {
                  for(int i = 0; i < root.getChildren().get(2).getChildren().size(); i++)
                     ptr.set(i + 1, buildBody(cname, root.getChildren().get(2).getChildren().get(i), b));
               }
               tmp = null;
               loop = cname;
               do {
                  tmp = (LLVMValueRef) root.scope.getLLVM(ScopeItem.LLVMVALUE, ScopeItem.METHOD + loop + root.getChildren().get(1).getValue().toString());
                  loop = ext.get(loop);
               } while(tmp == null);
               return LLVMLibrary.LLVMBuildCall(b, tmp, ptr, has? 1 + root.getChildren().get(2).getChildren().size() : 1, Pointer.allocateArray(Byte.class, 6));
            case "formal":
               return buildBody(cname, root.getChildren().get(0), b);
            case "assign":
               return LLVMLibrary.LLVMBuildStore(b, buildBody(cname, root.getChildren().get(1), b), buildBody(cname, root.getChildren().get(0), b));
            case "block":
               for(int i = 0; i < root.getChildren().size() - 1; i++)
                  buildBody(cname, root.getChildren().get(i), b);
               return buildBody(cname, root.getChildren().get(root.getChildren().size() - 1), b);
            case "if":
               b1 = LLVMLibrary.LLVMInsertBasicBlock(LLVMLibrary.LLVMGetInsertBlock(b), Pointer.allocateArray(Byte.class, 6));
               b2 = LLVMLibrary.LLVMInsertBasicBlock(LLVMLibrary.LLVMGetInsertBlock(b), Pointer.allocateArray(Byte.class, 6));
               bd1 = LLVMLibrary.LLVMCreateBuilder();
               bd2 = LLVMLibrary.LLVMCreateBuilder();
               LLVMLibrary.LLVMPositionBuilderAtEnd(bd1, b1);
               LLVMLibrary.LLVMPositionBuilderAtEnd(bd2, b2);
               LLVMLibrary.LLVMBuildRet(bd1, buildBody(cname, root.getChildren().get(1), bd1));
               if(root.getChildren().size() > 2)
                  LLVMLibrary.LLVMBuildRet(bd2, buildBody(cname, root.getChildren().get(2), bd2));
               else
                  LLVMLibrary.LLVMBuildRet(bd2, LLVMLibrary.LLVMBuildUnreachable(bd2));
               return LLVMLibrary.LLVMBuildCondBr(b, buildBody(cname, root.getChildren().get(0), b), b1, b2);
            case "while":
               b1 = LLVMLibrary.LLVMInsertBasicBlock(LLVMLibrary.LLVMGetInsertBlock(b), Pointer.allocateArray(Byte.class, 6));
               bd1 = LLVMLibrary.LLVMCreateBuilder();
               LLVMLibrary.LLVMPositionBuilderAtEnd(bd1, b1);
               buildBody(cname, root.getChildren().get(1), bd1);
               LLVMLibrary.LLVMBuildBr(bd1, b1);
               return LLVMLibrary.LLVMBuildCondBr(b, buildBody(cname, root.getChildren().get(0), b), b1, LLVMLibrary.LLVMInsertBasicBlock(LLVMLibrary.LLVMGetInsertBlock(b), Pointer.allocateArray(Byte.class, 6)));
            case "let":
               if(root.getChildren().size() > 3) {
                  //Initialized
                  root.scope.put(ScopeItem.LLVMVALUE, ScopeItem.FIELD + root.getChildren().get(0).getValue().toString(), buildBody(cname, root.getChildren().get(2), b));
                  return buildBody(cname, root.getChildren().get(3), b);
               } else {
                  //Unitialized!
                  root.scope.put(ScopeItem.LLVMVALUE, ScopeItem.FIELD + root.getChildren().get(0).getValue().toString(),
                           getDefaultForType(root, root.getChildren().get(1).getProp("type").toString(), b));
                  return buildBody(cname, root.getChildren().get(2), b);
               }
            case "uminus":
               return LLVMLibrary.LLVMBuildNeg(b, buildBody(cname, root.getChildren().get(0), b), Pointer.allocateArray(Byte.class, 6));
            default:
               System.err.println("code generation error: should never get here: type " + ASTNode.typeValue(root));
               return null;
         }
      }
   }

}
