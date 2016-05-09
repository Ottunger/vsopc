package be.ac.ulg.vsop.codegen;

import java.util.ArrayList;


public class CClassRecord {
   
   public static final String DELIMITER = "__DELIMITER__";
   
   public ArrayList<String> fields, types;
   public ArrayList<String> meths;
   public ArrayList<CSignature> sigs;
   public int inits, initb;
   public boolean printed;

   /**
    * Records info about a class at top level.
    * @param fields List of field names.
    * @param types List of fields types.
    * @param meths List of method names.
    * @param sigs List of method signatures.
    * @param inits Init position.
    * @param initb Init position.
    */
   public CClassRecord(ArrayList<String> fields, ArrayList<String> types, ArrayList<String> meths, ArrayList<CSignature> sigs, int inits, int initb) {
      this.fields = fields;
      this.types = types;
      this.meths = meths;
      this.sigs = sigs;
      this.inits = inits;
      this.initb = initb;
      printed = false;
   }

}
