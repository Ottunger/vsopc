package be.ac.ulg.vsop.analyzer;

import java.util.HashMap;
import java.util.Set;

import org.llvm.binding.LLVMLibrary;
import org.llvm.binding.LLVMLibrary.LLVMTypeRef;

import be.ac.ulg.vsop.codegen.ClassRecord;


public class Scope {
   
   private HashMap<String, ScopeItem> sc, sm, sf;
   private HashMap<String, LLVMLibrary.LLVMTypeRef> cs;
   private HashMap<String, ClassRecord> cv;
   private Scope p;
   
   /**
    * Creates an empty scope.
    */
   public Scope() {
      sc = new HashMap<String, ScopeItem>();
      sm = new HashMap<String, ScopeItem>();
      sf = new HashMap<String, ScopeItem>();
      cs = new HashMap<String, LLVMLibrary.LLVMTypeRef>();
      cv = new HashMap<String, ClassRecord>();
      p = null;
   }
   
   /**
    * Creates an empty scope.
    * @param p Parent.
    */
   public Scope(Scope p) {
      sc = new HashMap<String, ScopeItem>();
      sm = new HashMap<String, ScopeItem>();
      sf = new HashMap<String, ScopeItem>();
      cs = new HashMap<String, LLVMLibrary.LLVMTypeRef>();
      cv = new HashMap<String, ClassRecord>();
      this.p = p;
   }
   
   /**
    * Gets an item.
    * @param type Type.
    * @param name Name.
    * @return Item.
    */
   public ScopeItem get(int type, String name) {
      ScopeItem sci = null;
      
      switch(type) {
         case ScopeItem.CLASS:
            sci = sc.get(name);
            break;
         case ScopeItem.FIELD:
            sci = sf.get(name);
            break;
         case ScopeItem.METHOD:
            sci = sm.get(name);
            break;
         default:
            break;
      }
      
      if(sci == null && p != null)
         return p.get(type, name);
      return sci;
   }
   
   /**
    * Gets an item.
    * @param type Type.
    * @param name Name.
    * @return Item.
    */
   public Object getLLVM(int type, String name) {
      Object sci = null;
      
      switch(type) {
         case ScopeItem.LLVMTYPE:
            sci = cs.get(name);
            break;
         case ScopeItem.LLVMVALUE:
            sci = cv.get(name);
            break;
         default:
            break;
      }
      
      if(sci == null && p != null)
         return p.get(type, name);
      return sci;
   }
   
   /**
    * All field names.
    * @return Set of fields.
    */
   public Set<String> fieldSet() {
      Set<String> st = sf.keySet();
      if(p != null)
         st.addAll(p.fieldSet());
      return st;
   }
   
   /**
    * Registers an item.
    * @param type Type.
    * @param name Name.
    * @param sci Item.
    */
   public void put(int type, String name, Object sci) {
      switch(type) {
         case ScopeItem.CLASS:
            sc.put(name, (ScopeItem) sci);
            break;
         case ScopeItem.FIELD:
            sf.put(name, (ScopeItem) sci);
            break;
         case ScopeItem.METHOD:
            sm.put(name, (ScopeItem) sci);
            break;
         case ScopeItem.LLVMTYPE:
            cs.put(name, (LLVMTypeRef) sci);
            break;
         case ScopeItem.LLVMVALUE:
            cv.put(name, (ClassRecord) sci);
            break;
         default:
            break;
      }
   }
   
   /**
    * Registers an item above oneself.
    * @param type Type.
    * @param name Name.
    * @param sci Item.
    * @param level Level, 0 is immediate.
    */
   public void putAbove(int type, String name, Object sci, int level) {
      if(level < 1 || p == null) {
         put(type, name, sci);
         return;
      }
      p.putAbove(type, name, sci, level - 1);
   }
   
   /**
    * Sets parent.
    * @param p Parent.
    */
   public void setParent(Scope p) {
      this.p = p;
   }
   
   /**
    * Gets parent.
    * @return Parent.
    */
   public Scope getParent() {
      return p;
   }

}
