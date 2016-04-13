package be.ac.ulg.vsop.codegen;

import java.util.ArrayList;


public class CSignature {
   
   public String ret;
   public ArrayList<String> pnames, ptypes;
   
   public CSignature(String ret) {
      this.ret = ret;
      pnames = new ArrayList<String>();
      ptypes = new ArrayList<String>();
   }
   
   public void newParam(String p, String t) {
      pnames.add(p);
      ptypes.add(t);
   }

}
