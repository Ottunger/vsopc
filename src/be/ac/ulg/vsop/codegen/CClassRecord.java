package be.ac.ulg.vsop.codegen;

import java.util.ArrayList;


public class CClassRecord {
   
   public ArrayList<ArrayList<String>> fields, types;
   public ArrayList<String> meths;
   public ArrayList<CSignature> sigs;
   public int initb;

   /**
    * Records info about a class at top level.
    * @param fields List of field names.
    * @param types List of fields types.
    * @param meths List of method names.
    * @param sigs List of method signatures.
    * @param initb Init position.
    */
   public CClassRecord(ArrayList<ArrayList<String>> fields, ArrayList<ArrayList<String>> types, ArrayList<String> meths, ArrayList<CSignature> sigs, int initb) {
      this.fields = fields;
      this.types = types;
      this.meths = meths;
      this.sigs = sigs;
      this.initb = initb;
   }

}
