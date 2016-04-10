package be.ac.ulg.vsop.codegen;

import java.util.ArrayList;
import java.util.HashMap;

import org.llvm.binding.LLVMLibrary;
import org.llvm.binding.LLVMLibrary.LLVMTypeRef;
import org.llvm.binding.LLVMLibrary.LLVMValueRef;


public class ClassRecord {
   
   public LLVMTypeRef st;
   public HashMap<String, Integer> shift;
   public HashMap<String, LLVMTypeRef> types;
   public LLVMValueRef fbody;
   public LLVMLibrary.LLVMBuilderRef initb;

   /**
    * Records info about a class at top level.
    * @param st Build struct for this class.
    * @param fields List of field names.
    * @param tps List of field types.
    * @param fbody Constructor body.
    * @param initb Constructor builder.
    */
   public ClassRecord(LLVMTypeRef st, ArrayList<String> fields, ArrayList<LLVMTypeRef> tps, LLVMValueRef fbody, LLVMLibrary.LLVMBuilderRef initb) {
      int sh = 0;
      this.st = st;
      shift = new HashMap<String, Integer>();
      types = new HashMap<String, LLVMTypeRef>();
      shift.put("self", 0);
      types.put("self", st);
      
      this.fbody = fbody;
      this.initb = initb;
      
      for(int i = fields.size() - 1; i >= 0; i--) {
         shift.put(fields.get(i), sh + ClassRecord.shift(tps.get(i)));
         sh += ClassRecord.shift(tps.get(i));
         types.put(fields.get(i), tps.get(i));
      }
   }

   /**
    * Computes the new shift induced by a field.
    * @param t Type.
    * @return New to add shift.
    */
   private static int shift(LLVMTypeRef t) {
      if(t == LLVMLibrary.LLVMInt8Type())
         return 1;
      if(t == LLVMLibrary.LLVMInt32Type())
         return 4;
      else
         //Pointers are 4 in size;
         return 4;
   }

}
