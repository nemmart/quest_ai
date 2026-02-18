package q;

import hw.*;

public class Item {
   static public PageSet   worldDataFile=DataFiles.worldDataFile();
   static public final int base=11504;

   public int x;
   public int y;
   public int tag;
   public int extra1;
   public int extra2;
   public int extra3;
   public int extra4;
   public int extra5;
   public int extra6;

   static public int count() {
    return worldDataFile.readWide(base-2);
   }

   static public int y(int location) {
    return worldDataFile.readWord(base+location*9);
   }

   static public int x(int location) {
    return worldDataFile.readWord(base+location*9+1);
   }

   static public int tag(int location) {
    return worldDataFile.readWord(base+location*9+2);
   }

   static public int extra1(int location) {
    return worldDataFile.readWord(base+location*9+3);
   }

   static public int extra2(int location) {
    return worldDataFile.readWord(base+location*9+4);
   }

   static public int extra3(int location) {
    return worldDataFile.readWord(base+location*9+5);
   }

   static public int extra4(int location) {
    return worldDataFile.readWord(base+location*9+6);
   }

   static public int extra5(int location) {
    return worldDataFile.readWord(base+location*9+7);
   }

   static public int extra6(int location) {
    return worldDataFile.readWord(base+location*9+8);
   }

   static public boolean valid(int location) {
    int x=x(location), y=y(location), tag=tag(location);

    return (x>0 && x<0xFFFF && y>0 && y<0xFFFF && tag>0 && tag<2000);
   }

   public Item() {
    x=0;
    y=0;
    tag=0;
    extra1=0;
    extra2=0;
    extra3=0;
    extra4=0;
    extra5=0;
    extra6=0;
   }

   public Item(int location) {
    x=x(location);
    y=y(location);
    tag=tag(location);
    extra1=extra1(location);
    extra2=extra2(location);
    extra3=extra3(location);
    extra4=extra4(location);
    extra5=extra5(location);
    extra6=extra6(location);
   }

   public boolean valid() {
    return (x>0 && x<0xFFFF && y>0 && y<0xFFFF && tag>0 & tag<2000);
   }

   public void load(int location) {
    x=x(location);
    y=y(location);
    tag=tag(location);
    extra1=extra1(location);
    extra2=extra2(location);
    extra3=extra3(location);
    extra4=extra4(location);
    extra5=extra5(location);
    extra6=extra6(location);
   }
}
