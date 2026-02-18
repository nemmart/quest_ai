package q;

import hw.*;
import os.*;

public class DataFiles {
   static public PageSet worldDataFile=null;
   static public PageSet sharedDataFile=null;
   static public PageSet castleDataFile=null;

   static public PageSet worldDataFile() {
    if(worldDataFile==null) {
     FSObject file=FS.retrieve(":WORLD_DATA_FILE");

     if(file==null || !(file instanceof FSFile))
      throw new RuntimeException("WORLD_DATA_FILE not found");
     worldDataFile=((FSFile)file).pageSet();
    }
    return worldDataFile;
   }

   static public PageSet sharedDataFile() {
    if(sharedDataFile==null) {
     FSObject file=FS.retrieve(":SHARED_DATA_FILE");

     if(file==null || !(file instanceof FSFile))
      throw new RuntimeException("SHARED_DATA_FILE not found");
     sharedDataFile=((FSFile)file).pageSet();
    }
    return sharedDataFile;
   }

   static public PageSet castleDataFile() {
    if(castleDataFile==null) {
     FSObject file=FS.retrieve(":CASTLE_DATA_FILE");

     if(file==null || !(file instanceof FSFile))
      throw new RuntimeException("CASTLE_DATA_FILE not found");
     castleDataFile=((FSFile)file).pageSet();
    }
    return castleDataFile;
   }
}

