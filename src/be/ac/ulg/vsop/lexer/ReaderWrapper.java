package be.ac.ulg.vsop.lexer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;


public class ReaderWrapper extends FileReader {
   
   private boolean atEOF;

   public ReaderWrapper(File f) throws FileNotFoundException {
      super(f);
      atEOF = false;
   }
   
   @Override
   public int read(char[] buf, int off, int num) throws IOException {
      int read = super.read(buf, off, num);
      if(read == num || atEOF) {
         return read;
      } else if(read == -1) {
         atEOF = true;
         buf[off] = ' ';
         return 1;
      } else {
         atEOF = true;
         buf[off + read] = ' ';
         return read + 1;
      }
   }

}
