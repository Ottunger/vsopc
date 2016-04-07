package be.ac.ulg.vsop.codegen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import org.bridj.Pointer;
import org.llvm.binding.LLVMLibrary;
import org.llvm.binding.LLVMLibrary.LLVMModuleRef;
import org.llvm.binding.LLVMLibrary.LLVMTypeRef;
import org.llvm.binding.LLVMLibrary.LLVMValueRef;

import be.ac.ulg.vsop.analyzer.Analyzer;
import be.ac.ulg.vsop.analyzer.ScopeItem;
import be.ac.ulg.vsop.parser.ASTNode;
import be.ac.ulg.vsop.parser.SymbolValue;


public class LLVMGen {
   
   private ASTNode ast;
   private HashMap<String, ScopeItem> rscope;
   private HashMap<String, String> ext;
   private HashMap<String, LLVMTypeRef> cstrs;
   private HashMap<String, LLVMValueRef> cscope;
   private LLVMModuleRef m;
   
   /**
    * Build a LLVM IR generator.
    * @param root AST.
    * @param ext Inheritances.
    */
   public LLVMGen(ASTNode root, HashMap<String, String> ext) {
      ast = root;
      this.ext = ext;
      rscope = root.scope;
      cstrs = new HashMap<String, LLVMTypeRef>();
      cscope = new HashMap<String, LLVMValueRef>();
      
      Pointer<Character> mname = Pointer.allocateArray(Character.class, 4);
      mname.setChars("vsop".toCharArray());
      m = LLVMLibrary.LLVMModuleCreateWithName(mname.as(Byte.class));
   }
   
   /**
    * Emit LLVM to a stream.
    * @param fd File descriptor of out. Set negative for stdout.
    */
   public void emit(int fd) {
      //Use stdout
      if (fd < 0)
         fd = 1;
      
      //From a first pass, generate the structures that class rely are
      genStructures(ast, true, false);
      //From a second pass, generate the methods that are functions that have a pointer *self
      //Second pass is actually deeper in first pass
      genStructures(ast, true, true);
      
      LLVMLibrary.LLVMWriteBitcodeToFD(m, fd, 1, 0);
      LLVMLibrary.LLVMDisposeModule(m);
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
      LLVMTypeRef t, st, fsig;
      LLVMValueRef fbody;
      LLVMLibrary.LLVMBuilderRef builder = null;
      ArrayList<LLVMTypeRef> tps;
      Pointer<LLVMTypeRef> ptr;
      HashSet<String> fields;
      
      if(!second && root.ending) {
         switch(root.itype) {
            case SymbolValue.CLASS:
               //Create a type for this structure
               type = root.getChildren().get(0).getValue().toString();
               tps = new ArrayList<LLVMTypeRef>();
               fields = new HashSet<String>();
               do {
                  vclass = rscope.get(ScopeItem.CLASS + type).userType;
                  for(String fname : vclass.scope.keySet()) {
                     if(fname.startsWith(ScopeItem.FIELD + "") && !fields.contains(fname)) {
                        t = llvmType(root.scope.get(fname).type);
                        if(t != null) {
                           fields.add(fname);
                           tps.add(t);
                        }
                     }
                  }
               } while(!(type = ext.get(type)).equals(Analyzer.EMPTY));
               Pointer<LLVMTypeRef> types = Pointer.allocateArray(LLVMTypeRef.class, tps.size());
               for(int i = 0; i < tps.size(); i++)
                  types.set(i, tps.get(i));
               st = LLVMLibrary.LLVMStructType(types, tps.size(), 1);
               //Add in LLVM IR code this structure type as a global
               type = root.getChildren().get(0).getValue().toString();
               Pointer<Character> gname = Pointer.allocateArray(Character.class, type.length());
               gname.setChars(type.toCharArray());
               LLVMLibrary.LLVMAddGlobal(m, st, gname.as(Byte.class));
               cstrs.put(type, st);
               //Add init method for this class
               ptr = Pointer.allocateArray(LLVMTypeRef.class, 1);
               ptr.set(0, llvmType(root.getChildren().get(0).getValue().toString()));
               fsig = LLVMLibrary.LLVMFunctionType(LLVMLibrary.LLVMVoidType(), ptr, 1, 0);
               fbody = LLVMLibrary.LLVMAddFunction(m, Pointer.allocateArray(Byte.class, 6), fsig);
               cscope.put(ScopeItem.METHOD + type + "<init>", fbody);
               //Prepare builder so that to send it to pass
               LLVMLibrary.LLVMBasicBlockRef bb = LLVMLibrary.LLVMAppendBasicBlock(fbody, Pointer.allocateArray(Byte.class, 6));
               builder = LLVMLibrary.LLVMCreateBuilder();
               LLVMLibrary.LLVMPositionBuilderAtEnd(builder, bb);
               break;
            default:
               break;
         }
      }
      
      if(pass) {
         for(ASTNode r : root.getChildren()) {
            genStructures(r, false, second);
         }
      } else if(second){
         for(ASTNode r : root.getChildren()) {
            genFunctions(type, r, builder);
         }
      }
   }
   
   /**
    * Returns LLVM type for our types.
    * @param type Our kind of type.
    * @return LLVMTypeRef equivalent.
    */
   private LLVMTypeRef llvmType(int type) {
      switch(type) {
         case SymbolValue.INT32:
            return LLVMLibrary.LLVMInt32Type();
         case SymbolValue.BOOL:
            return LLVMLibrary.LLVMInt8Type();
         case SymbolValue.OBJECT_IDENTIFIER:
            //Is a pointer, assume we are 32-bit arch
            return LLVMLibrary.LLVMPointerType(cstrs.get(type), 1);
         case SymbolValue.STRING:
          //Pointer to well known String class instance
            return LLVMLibrary.LLVMPointerType(LLVMLibrary.LLVMInt8Type(), 1);
         case SymbolValue.UNIT:
            return LLVMLibrary.LLVMInt8Type();
         default:
          //Is a pointer, assume we are 32-bit arch
            return LLVMLibrary.LLVMPointerType(cstrs.get(type), 1);
      }
   }

   /**
    * Returns LLVM type for our types.
    * @param type Our kind of type.
    * @return LLVMTypeRef equivalent.
    */
   private LLVMTypeRef llvmType(String type) {
      switch(type) {
         case "int32":
            return LLVMLibrary.LLVMInt32Type();
         case "bool":
            return LLVMLibrary.LLVMInt8Type();
         case "string":
            //Pointer to well known String class instance
            return LLVMLibrary.LLVMPointerType(LLVMLibrary.LLVMInt8Type(), 1);
         case "unit":
            return LLVMLibrary.LLVMInt8Type();
         default:
            //Is a pointer, assume we are 32-bit arch
            return LLVMLibrary.LLVMPointerType(cstrs.get(type), 1);
      }
   }

   /**
    * Generate functions into builder.
    * @param cname Class name for the following methods.
    * @param root Where we are.
    * @param initb Builder for init function.
    */
   private void genFunctions(String cname, ASTNode root, LLVMLibrary.LLVMBuilderRef initb) {
      boolean has;
      ASTNode fn;
      Pointer<Character> gname;
      Pointer<LLVMTypeRef> types;
      LLVMTypeRef fsig;
      LLVMValueRef fbody, asgn;

      if(root.ending == false) {
         switch(root.stype) {
            case "method":
               //As method are ALWAYS after fields, buidl the end of <init> at first method
               if(initb != null) {
                  LLVMLibrary.LLVMBuildRet(initb, cscope.get(ScopeItem.FIELD + "self"));
                  //This creates several returns at end of function, but, who cares?
               }

               //Start building method
               has = root.getChildren().get(1).stype.equals("formals");
               if(has) {
                  ASTNode fms = root.getChildren().get(1);
                  types = Pointer.allocateArray(LLVMTypeRef.class, fms.getChildren().size() + 1);
                  for(int i = 1; i < fms.getChildren().size() + 1; i++) {
                     fn = fms.getChildren().get(i - 1).getChildren().get(0);
                     types.set(i, llvmType(fn.getProp("type").toString()));
                  }
               } else {
                  types = Pointer.allocateArray(LLVMTypeRef.class, 1);
               }
               //Add self as parameter to the function
               types.set(0, llvmType(cname));
               //Create method declaration
               fsig = LLVMLibrary.LLVMFunctionType(llvmType(root.getProp("type").toString()), types,
                       has? 1 + root.getChildren().get(1).getChildren().size() : 1, 0);
               //Name the method as global function and register it
               gname = Pointer.allocateArray(Character.class, cname.length() + root.getChildren().get(0).getValue().toString().length());
               gname.setChars(LLVMGen.catChars(cname.toCharArray(), root.getChildren().get(0).getValue().toString().toCharArray()));
               fbody = LLVMLibrary.LLVMAddFunction(m, gname.as(Byte.class), fsig);
               cscope.put(ScopeItem.METHOD + root.getChildren().get(0).getValue().toString(), fbody);
               //Register params for the build of body
               cscope.put(ScopeItem.FIELD + "self", LLVMLibrary.LLVMGetParam(fbody, 0));
               if(has) {
                  ASTNode fms = root.getChildren().get(1);
                  for(int i = 0; i < fms.getChildren().size(); i++)
                     cscope.put(ScopeItem.FIELD + fms.getChildren().get(i).getChildren().get(0).getValue().toString(), LLVMLibrary.LLVMGetParam(fbody, i + 1));
               }
               //Add method body
               LLVMLibrary.LLVMBasicBlockRef bb = LLVMLibrary.LLVMAppendBasicBlock(fbody, Pointer.allocateArray(Byte.class, 6));
               LLVMLibrary.LLVMBuilderRef builder = LLVMLibrary.LLVMCreateBuilder();
               LLVMLibrary.LLVMPositionBuilderAtEnd(builder, bb);
               LLVMLibrary.LLVMBuildRet(builder, buildBody(cname, has? root.getChildren().get(2) : root.getChildren().get(1), builder));
               break;
            case "field":
               if(root.getChildren().size() > 3) {
                  //Initialized
                  asgn = buildBody(cname, root.getChildren().get(2), initb);
               } else {
                  //Default initialized
                  asgn = getDefaultForType(root.getChildren().get(1).getProp("type").toString(), initb);
               }
               LLVMLibrary.LLVMBuildStore(initb, buildBody(cname, root.getChildren().get(0), initb), asgn);
               cscope.put(ScopeItem.FIELD + root.getChildren().get(0).getValue().toString(), asgn);
               break;
            default:
               break;
         }
      }
   }

   /**
    * Returns the default value for a type.
    * @param type The type.
    */
   private LLVMValueRef getDefaultForType(String type, LLVMLibrary.LLVMBuilderRef b) {
      LLVMValueRef tmp;
      Pointer<Byte> ptr;
      
      switch (type) {
         case "int32":
            return LLVMLibrary.LLVMConstInt(LLVMLibrary.LLVMInt32Type(), 0, 1);
         case "bool":
            return LLVMLibrary.LLVMConstInt(LLVMLibrary.LLVMInt8Type(), 0, 1);
         case "string":
            //Pointer to well known String class instance
            tmp = LLVMLibrary.LLVMBuildMalloc(b, llvmType("string"), Pointer.allocateArray(Byte.class, 6));
            ptr = Pointer.allocateArray(Byte.class, 1);
            ptr.set(0, (byte) 0);
            return tmp;
         case "unit":
            return LLVMLibrary.LLVMConstInt(LLVMLibrary.LLVMInt8Type(), 0, 1);
         default:
            //Is a pointer, assume we are 32-bit arch
            return LLVMLibrary.LLVMConstPointerNull(cstrs.get(type));
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
      LLVMValueRef tmp;
      LLVMLibrary.LLVMBasicBlockRef b1, b2;
      LLVMLibrary.LLVMBuilderRef bd1, bd2;
      Pointer<Character> str;
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
               tmp = LLVMLibrary.LLVMBuildMalloc(b, cstrs.get(root.getProp("type")), Pointer.allocateArray(Byte.class, 6));
               ptr = Pointer.allocate(LLVMValueRef.class);
               ptr.set(0, tmp);
               return LLVMLibrary.LLVMBuildCall(b, cscope.get(ScopeItem.METHOD + cname + "<init>"), ptr, 1, Pointer.allocateArray(Byte.class, 6));
            case SymbolValue.INTEGER_LITERAL:
               return LLVMLibrary.LLVMConstInt(LLVMLibrary.LLVMInt32Type(), (int)root.getValue(), 1);
            case SymbolValue.STRING_LITERAL:
               str = Pointer.allocateArray(Character.class, root.getValue().toString().length() + 1); //Make room for empty char \0
               str.setChars(root.getValue().toString().toCharArray());
               return LLVMLibrary.LLVMConstString(str.as(Byte.class), root.getValue().toString().length(), 1);
            case SymbolValue.OBJECT_IDENTIFIER:
               //Find in cscope the return. We know the scope is OK, right?
               return cscope.get(ScopeItem.FIELD + root.getValue().toString());
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
               return LLVMLibrary.LLVMBuildCall(b, cscope.get(ScopeItem.METHOD + root.getChildren().get(1).getValue().toString()),
                       ptr, has? 1 + root.getChildren().size() : 1, Pointer.allocateArray(Byte.class, 6));
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
                  cscope.put(ScopeItem.FIELD + root.getChildren().get(0).getValue().toString(), buildBody(cname, root.getChildren().get(2), b));
                  return buildBody(cname, root.getChildren().get(3), b);
               } else {
                  //Unitialized!
                  cscope.put(ScopeItem.FIELD + root.getChildren().get(0).getValue().toString(), getDefaultForType(root.getChildren().get(1).getProp("type").toString(), b));
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

   /**
    * Merges two char arrays.
    * @param a First.
    * @param b Second.
    * @return Merge.
     */
   private static char[] catChars(char[] a, char[] b) {
      char[] ret = new char[a.length + b.length];
      for(int i = 0; i < a.length; i++)
         ret[i] = a[i];
      for(int i = a.length; i < a.length + b.length; i++)
         ret[i] = b[i - a.length];
      return ret;
   }

}
