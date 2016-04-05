package be.ac.ulg.vsop.codegen;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import org.bridj.Pointer;
import org.llvm.binding.LLVMLibrary;
import org.llvm.binding.LLVMLibrary.LLVMModuleRef;
import org.llvm.binding.LLVMLibrary.LLVMTypeRef;

import be.ac.ulg.vsop.analyzer.Analyzer;
import be.ac.ulg.vsop.analyzer.ScopeItem;
import be.ac.ulg.vsop.parser.ASTNode;
import be.ac.ulg.vsop.parser.SymbolValue;


public class LLVMGen {
   
   private ASTNode ast;
   private HashMap<String, ScopeItem> rscope;
   private HashMap<String, String> ext;
   private HashMap<String, LLVMTypeRef> cstrs;
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
      Pointer<Character> mname = Pointer.allocateArray(Character.class, 4);
      mname.setChars("vsop".toCharArray());
      m = LLVMLibrary.LLVMModuleCreateWithName(mname.as(Byte.class));
   }
   
   /**
    * Emit LLVM to a stream.
    * @param ps Stream where to emit.
    */
   public void emit(PrintStream ps) {
      PrintStream save = System.out;
      
      //From a first pass, generate the structures that class rely are
      genStructures(ast, true, false);
      //From a second pass, generate the methods that are functions that have a pointer *self
      //Second pass is actually deeper in first pass
      genStructures(ast, true, true);
      
      System.setOut(ps);
      LLVMLibrary.LLVMDumpModule(m);
      LLVMLibrary.LLVMDisposeModule(m);
      System.setOut(save);
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
      LLVMTypeRef t, st;
      ArrayList<LLVMTypeRef> tps;
      HashSet<String> fields;
      
      if(!second && root.ending) {
         switch(root.itype) {
            case SymbolValue.CLASS:
               //Create a type for this structure
               type = root.getValue().toString();
               tps = new ArrayList<LLVMTypeRef>();
               fields = new HashSet<String>();
               do {
                  vclass = rscope.get(ScopeItem.CLASS + type).userType;
                  for(String fname : vclass.scope.keySet()) {
                     if(fname.startsWith(ScopeItem.FIELD + "") && !fields.contains(fname)) {
                        t = LLVMGen.LLVMType(root.scope.get(fname).type);
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
               Pointer<Character> gname = Pointer.allocateArray(Character.class, root.getValue().toString().length());
               gname.setChars(root.getValue().toString().toCharArray());
               LLVMLibrary.LLVMAddGlobal(m, st, gname.as(Byte.class));
               cstrs.put(root.getValue().toString(), st);
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
            genFunctions(type, r);
         }
      }
   }
   
   /**
    * Returns LLVM type for our types.
    * @param type Our kind of type.
    * @return LLVMTypeRef equivalent.
    */
   private static LLVMTypeRef LLVMType(int type) {
      switch(type) {
         case SymbolValue.INT32:
            return LLVMLibrary.LLVMInt32Type();
         case SymbolValue.BOOL:
            return LLVMLibrary.LLVMInt8Type();
         case SymbolValue.OBJECT_IDENTIFIER:
            //Is a pointer, assume we are 32-bit arch
            return LLVMLibrary.LLVMPointerType(LLVMLibrary.LLVMInt32Type(), 1);
         case SymbolValue.STRING:
            //Pointer to well known String class instance
            return LLVMLibrary.LLVMPointerType(LLVMLibrary.LLVMInt32Type(), 1);
         case SymbolValue.UNIT:
            return LLVMLibrary.LLVMVoidType();
         default:
            //Is a pointer, assume we are 32-bit arch
            return LLVMLibrary.LLVMPointerType(LLVMLibrary.LLVMInt32Type(), 1);
      }
   }

   /**
    * Returns LLVM type for our types.
    * @param type Our kind of type.
    * @return LLVMTypeRef equivalent.
    */
   private static LLVMTypeRef LLVMType(String type) {
      switch(type) {
         case "int32":
            return LLVMLibrary.LLVMInt32Type();
         case "bool":
            return LLVMLibrary.LLVMInt8Type();
         case "string":
            //Pointer to well known String class instance
            return LLVMLibrary.LLVMPointerType(LLVMLibrary.LLVMInt32Type(), 1);
         case "unit":
            return LLVMLibrary.LLVMVoidType();
         default:
            //Is a pointer, assume we are 32-bit arch
            return LLVMLibrary.LLVMPointerType(LLVMLibrary.LLVMInt32Type(), 1);
      }
   }

   /**
    * Generate functions into builder.
    * @param cname Class name for the following methods.
    * @param root Where we are.
    */
   private void genFunctions(String cname, ASTNode root) {
      boolean has;
      Pointer<Character> gname;
      Pointer<LLVMTypeRef> types;
      LLVMLibrary.LLVMTypeRef fsig;
      LLVMLibrary.LLVMValueRef fbody;
      HashMap<String, Integer> argPos;

      if(root.ending == false) {
         switch(root.stype) {
            case "method":
               has = root.getChildren().get(1).stype.equals("formals");
               argPos = new HashMap<String, Integer>();
               if(has) {
                  ASTNode fms = root.getChildren().get(1);
                  types = Pointer.allocateArray(LLVMTypeRef.class, fms.getChildren().size() + 1);
                  for(int i = 1; i < fms.getChildren().size() + 1; i++) {
                     types.set(i, LLVMGen.LLVMType(fms.getChildren().get(i - 1).getProp("type").toString()));
                     argPos.put(fms.getChildren().get(i - 1).getValue().toString(), i);
                  }
               } else {
                  types = Pointer.allocateArray(LLVMTypeRef.class, 1);
               }
               //Add self as parameter to the function
               types.set(0, LLVMGen.LLVMType(cname));
               argPos.put("self", 0);
               //Create method declaration
               fsig = LLVMLibrary.LLVMFunctionType(LLVMGen.LLVMType(root.getProp("type").toString()), types,
                       has? 1 + root.getChildren().get(1).getChildren().size() : 1, 0);
               //Name the methos as global function and register it
               gname = Pointer.allocateArray(Character.class, cname.length() + root.getChildren().get(0).getValue().toString().length());
               gname.setChars(LLVMGen.catChars(cname.toCharArray(), root.getChildren().get(0).getValue().toString().toCharArray()));
               fbody = LLVMLibrary.LLVMAddFunction(m, gname.as(Byte.class), fsig);
               //Add method body
               LLVMLibrary.LLVMBasicBlockRef bb = LLVMLibrary.LLVMAppendBasicBlock(fbody, Pointer.allocateArray(Byte.class, 0));
               LLVMLibrary.LLVMBuilderRef builder = LLVMLibrary.LLVMCreateBuilder();
               LLVMLibrary.LLVMPositionBuilderAtEnd(builder, bb);
               LLVMLibrary.LLVMBuildRet(builder, buildBody(argPos, has? root.getChildren().get(2) : root.getChildren().get(1), builder));
               break;
            default:
               break;
         }
      }
   }

  /**
   * Add function body.
   * @param ap Position of arguments in list.
   * @param root Current node.
   * @param b Builder.
   */
   private LLVMLibrary.LLVMValueRef buildBody(HashMap<String, Integer> ap, ASTNode root, LLVMLibrary.LLVMBuilderRef b) {
      if(root.ending) {
         switch(root.itype) {
            case SymbolValue.NOT:
               return LLVMLibrary.LLVMBuildNot(b, buildBody(ap, root.getChildren().get(0), b), Pointer.allocateArray(Byte.class, 0));
            case SymbolValue.EQUAL:
               return LLVMLibrary.LLVMBuildICmp(b, LLVMLibrary.LLVMIntPredicate.LLVMIntEQ,
                       buildBody(ap, root.getChildren().get(0), b), buildBody(ap, root.getChildren().get(1), b), Pointer.allocateArray(Byte.class, 0));
            case SymbolValue.AND:
               return LLVMLibrary.LLVMBuildAnd(b, buildBody(ap, root.getChildren().get(0), b),
                       buildBody(ap, root.getChildren().get(1), b), Pointer.allocateArray(Byte.class, 0));
            case SymbolValue.LOWER_EQUAL:
               return LLVMLibrary.LLVMBuildICmp(b, LLVMLibrary.LLVMIntPredicate.LLVMIntSLE,
                       buildBody(ap, root.getChildren().get(0), b), buildBody(ap, root.getChildren().get(1), b), Pointer.allocateArray(Byte.class, 0));
            case SymbolValue.LOWER:
               return LLVMLibrary.LLVMBuildICmp(b, LLVMLibrary.LLVMIntPredicate.LLVMIntSLT,
                       buildBody(ap, root.getChildren().get(0), b), buildBody(ap, root.getChildren().get(1), b), Pointer.allocateArray(Byte.class, 0));
            case SymbolValue.PLUS:
               return LLVMLibrary.LLVMBuildAdd(b, buildBody(ap, root.getChildren().get(0), b),
                       buildBody(ap, root.getChildren().get(1), b), Pointer.allocateArray(Byte.class, 0));
            case SymbolValue.MINUS:
               return LLVMLibrary.LLVMBuildSub(b, buildBody(ap, root.getChildren().get(0), b),
                       buildBody(ap, root.getChildren().get(1), b), Pointer.allocateArray(Byte.class, 0));
            case SymbolValue.TIMES:
               return LLVMLibrary.LLVMBuildMul(b, buildBody(ap, root.getChildren().get(0), b),
                       buildBody(ap, root.getChildren().get(1), b), Pointer.allocateArray(Byte.class, 0));
            case SymbolValue.DIV:
               return LLVMLibrary.LLVMBuildSDiv(b, buildBody(ap, root.getChildren().get(0), b),
                       buildBody(ap, root.getChildren().get(1), b), Pointer.allocateArray(Byte.class, 0));
            case SymbolValue.POW:
               //TODO: Find POW instruction
               return LLVMLibrary.LLVMBuildMul(b, buildBody(ap, root.getChildren().get(0), b),
                       buildBody(ap, root.getChildren().get(1), b), Pointer.allocateArray(Byte.class, 0));
            case SymbolValue.ISNULL:
               return LLVMLibrary.LLVMBuildIsNull(b, buildBody(ap, root.getChildren().get(0), b), Pointer.allocateArray(Byte.class, 0));
            case SymbolValue.ASSIGN:
               //TODO: Find assign instruction
            case SymbolValue.NEW:
               return LLVMLibrary.LLVMBuildAlloca(b, cstrs.get(root.getProp("type")), Pointer.allocateArray(Byte.class, 0));
            default:
               System.err.println("code generation error: should never get here");
               return null;
         }
      } else {
         return null;
         /*
         switch(root.stype) {
            case "call":
               type = getNodeType(root, 0);
               do {
                  //Get scope of callee
                  s = scope.get(ScopeItem.CLASS + type);
                  //Check that object type has methods
                  if(s == null || s.type != ScopeItem.CLASS)
                     throw new Exception(root.getProp("line") + ":" + root.getProp("col") + ": semantics error cannot call a method on type " + getNodeType(root, 0));
                  //Check that this method is registered
                  if((t = s.userType.scope.get(ScopeItem.METHOD + root.getChildren().get(1).getValue().toString())) != null)
                     break;
               } while(!(type = ext.get(type)).equals(Analyzer.EMPTY));
               //Check that this method is registered
               if((t = s.userType.scope.get(ScopeItem.METHOD + root.getChildren().get(1).getValue().toString())) == null)
                  throw new Exception(root.getProp("line") + ":" + root.getProp("col") + ": semantics error cannot call method " +
                          root.getChildren().get(1).getValue().toString() + " on type " + getNodeType(root, 0));
               //Then our type is the one of the method
               root.addProp("type", getNodeType(t.userType, -1));
               //Check ok arguments
               if(((root.getChildren().size() == 2) != (t.formals == null)) || (root.getChildren().size() > 2 && t.formals != null && root.getChildren().get(2).getChildren().size() != t.formals.getChildren().size())) {
                  throw new Exception(root.getProp("line") + ":" + root.getProp("col") + ": semantics error wrong number of arguments to method " + root.getChildren().get(1).getValue());
               } else if(root.getChildren().size() > 2) {
                  for(int i = 0; i < root.getChildren().get(2).getChildren().size(); i++) {
                     String arg = getNodeType(root.getChildren().get(2), i);
                     String formal = getNodeType(t.formals, i);
                     if(!isSameOrChild(arg, formal))
                        throw new Exception(root.getChildren().get(2).getChildren().get(i).getProp("line") + ":" + root.getChildren().get(2).getChildren().get(i).getProp("col") +
                                ": semantics error expected type " + formal + " but got " + arg + " for argument " + (i+1) + " of method " + root.getChildren().get(1).getValue());
                  }
               }
               break;
            case "formal":
               root.addProp("type", getNodeType(root, 0));
               if(ext.get(getNodeType(root, 0)) == null)
                  throw new Exception(root.getProp("line") + ":" + root.getProp("col") + ": semantics error unknown type " + getNodeType(root, 0));
               break;
            case "field":
               //Here we can check no override of field
               type = root.scope.get(ScopeItem.FIELD + "self").userType.getProp("type").toString();
               while(!(type = ext.get(type)).equals(Analyzer.EMPTY)) {
                  //If such a field already exists above...
                  if(scope.get(ScopeItem.CLASS + type).userType.scope.get(ScopeItem.FIELD + root.getChildren().get(0).getValue().toString()) != null)
                     throw new Exception(root.getProp("line") + ":" + root.getProp("col") + ": semantics error cannot redefine symbol '" + root.getChildren().get(0).getValue().toString() + "'here");
               }
               root.addProp("type", getNodeType(root, 0));
               if(ext.get(getNodeType(root, 0)) == null)
                  throw new Exception(root.getProp("line") + ":" + root.getProp("col") + ": semantics error unknown type " + getNodeType(root, 0));
               if(root.getChildren().size() > 2 && !getNodeType(root, 0).equals(getNodeType(root, 2)))
                  throw new Exception(root.getProp("line") + ":" + root.getProp("col") + ": semantics error cannot assign " +
                          getNodeType(root, 2) + " to " + getNodeType(root, 0));
               break;
            case "assign":
               type = getNodeType(root, 1);
               root.addProp("type", type);
               //Check that no assign to self
               if(root.getChildren().get(0).getValue().toString().equals("self"))
                  throw new Exception(root.getProp("line") + ":" + root.getProp("col") + ": semantics error cannot assign anything to 'self'");
               do {
                  //Get scope of candidate
                  s = scope.get(ScopeItem.CLASS + type);
                  //Check that candidate and assigned are the same
                  if(getNodeType(s.userType, -1).equals(getNodeType(root, 0)))
                     break;
               } while(!(type = ext.get(type)).equals(Analyzer.EMPTY));
               if(type.equals(Analyzer.EMPTY))
                  throw new Exception(root.getProp("line") + ":" + root.getProp("col") + ": semantics error cannot assign " +
                          getNodeType(root, 1) + " to " + getNodeType(root, 0));
               break;
            case "method":
               if(ext.get(getNodeType(root, -1)) == null)
                  throw new Exception(root.getProp("line") + ":" + root.getProp("col") + ": semantics error unknown type " + getNodeType(root, -1));
               if(!getNodeType(root, -1).equals(getNodeType(root, root.getChildren().size() - 1)))
                  throw new Exception(root.getProp("line") + ":" + root.getProp("col") + ": semantics error method type is " +
                          getNodeType(root, -1) + " but got " + getNodeType(root, root.getChildren().size() - 1));
            case "block":
               root.addProp("type", getNodeType(root, root.getChildren().size() - 1));
               break;
            case "if":
               if(!getNodeType(root, 0).equals("bool"))
                  throw new Exception(root.getProp("line") + ":" + root.getProp("col") + ": semantics error if condition must be bool");
               root.addProp("type", getNodeType(root, 1));
               if(root.getChildren().size() > 2) {
                  if(getNodeType(root, 1).equals("unit") || getNodeType(root, 2).equals("unit"))
                     root.addProp("type", "unit");
                  else {
                     if(isSameOrChild(getNodeType(root, 1), getNodeType(root, 2)))
                        root.addProp("type", getNodeType(root, 2));
                     else if(isSameOrChild(getNodeType(root, 2), getNodeType(root, 1)))
                        root.addProp("type", getNodeType(root, 1));
                     else
                        throw new Exception(root.getProp("line") + ":" + root.getProp("col") + ": semantics error if branches are typed as " +
                                getNodeType(root, 1) + " and " + getNodeType(root, 2));
                  }
               }
               break;
            case "while":
               if(!getNodeType(root, 0).equals("bool"))
                  throw new Exception(root.getProp("line") + ":" + root.getProp("col") + ": semantics error while condition must be bool");
               break;
            case "let":
               type = getNodeType(root, 2);
               if(ext.get(getNodeType(root, 1)) == null)
                  throw new Exception(root.getProp("line") + ":" + root.getProp("col") + ": semantics error unknown type " + getNodeType(root, 1));
               do {
                  //Get scope of candidate
                  s = scope.get(ScopeItem.CLASS + type);
                  //Check that candidate and assigned are the same
                  if(getNodeType(s.userType, -1).equals(getNodeType(root, 1)))
                     break;
               } while(!(type = ext.get(type)).equals(Analyzer.EMPTY));
               if(root.getChildren().size() > 3 && type.equals(Analyzer.EMPTY))
                  throw new Exception(root.getProp("line") + ":" + root.getProp("col") + ": semantics error cannot assign " +
                          getNodeType(root, 2) + " to " + getNodeType(root, 1));
            case "uminus": //Fallthrough
               root.addProp("type", getNodeType(root, 0));
               break;
            default:
               break;
         }
         */
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
