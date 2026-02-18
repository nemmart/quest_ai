import java.io.*;
import java.net.*;

import hw.*;
import os.*;
import debug.*;

public class Disassemble {

   static public int checkForLabels(SymbolTable symbols, int start, int stop) {
    int index;

    for(index=start;index<stop;index++)
     if(symbols.nameForAddress(index)!=null)
      break;
    return index-start;
   }

   static public void disassemble(SymbolTable symbols, Memory memory, int start, int stop) {
    int         index, count, dumpStart, dumpStop, current, nextDecode, opcode;
    String      symbol;
    Instruction instruction;
    String      name;

    nextDecode=start;
    index=start;
    while(index<stop) {
     dumpStart=index;
     dumpStop=index+24;
     if(index>start+8)
      dumpStart=index-8;

     count=MemoryDumper.count(memory, dumpStart, stop);
     if(count>=48)
      count=checkForLabels(symbols, dumpStart, dumpStart+count);
     count=count-count%8;

     if(count>=48) {
      dumpStop+=count;
      index=dumpStart+count;
     }
     if(dumpStop>stop)
      dumpStop=stop;
     System.out.println();
     MemoryDumper.dump(memory, dumpStart, dumpStop);
     System.out.println();

     for(current=index;current<index+16 && current<stop;current++) {
      symbol=symbols.nameForAddress(current);
      if(symbol!=null) {
       System.out.println("----------------------------------------------------------------");
       System.out.println(symbol);
       System.out.println("----------------------------------------------------------------");
      }
      System.out.printf("%06x %04x ", current, memory.readWord(current));
      if(current>=nextDecode) {
       opcode=memory.readWord(current);
       if(opcode==0xc619 && memory.readWord(current+1)==0x8006) {
        System.out.printf("SYSCALL 0%o", memory.readWord(current+2));
        nextDecode=current+3;
       }
       else {
        instruction=Instruction.decode(false, opcode);
        if(instruction==null) {
         System.out.print("undef");
         nextDecode=current+1;
        }
        else {
         name=instruction.name;
         if(name==null)
          throw new RuntimeException("Unnamed instruction for opcode " + String.format("%04X", opcode));
         if(name!=null && name.endsWith("*"))
          name=name.substring(0, name.length()-1);
         if(instruction.instructionFormat==null) {
          System.out.print(name);
          nextDecode=current+1;
         }
         else {
          System.out.print(Disassembler.disassemble(memory, current, name, opcode, instruction.instructionFormat));
          nextDecode=current+Disassembler.wordLength(instruction.instructionFormat);
         }
        }
       }
      }
      System.out.println();
     }
     index=index+16;
    }
   }

   static public void main(String[] args) {
    FSTerminal  terminal;
    String      file;
    FSChannel   channel;
    PageSet     filePages;
    OSProcess   process;
    SymbolTable symbols;
    Memory      fileMemory, processMemory;

    if(args.length!=2) {
     System.err.println("Usage: java Disassemble <dir> <PR file>");
     System.exit(1);
    }

    FS.initializeWithPath(args[0]);

    file=args[1].toUpperCase();
    if(file.endsWith(".PR"))
     file=file.substring(0, file.length()-3);

    channel=FSChannel.openForPagedIO(":" + file + ".PR", true);
    filePages=channel.pageSet();

    System.out.println("------------------------------------- HEADER ------------------------------------");
    System.out.println();
    MemoryDumper.dump(filePages, 0, 0x2000);
    System.out.println();
    System.out.println();
    System.out.println();
    System.out.println();

    process=new OSProcess(":", file);
    symbols=process.symbols;
    processMemory=process.memory;

    int sharedStartPage=filePages.readWord(0x10F);
    int sharedPageCount=filePages.readWord(0x113);
    int fileSharedOffset=filePages.readWord(0x11A);
    int SEGMENT_BASE=OSProcess.SEGMENT_BASE*1024;

    System.out.println("----------------------------------- LOW MEMORY ----------------------------------");
    disassemble(symbols, processMemory, SEGMENT_BASE, SEGMENT_BASE+(fileSharedOffset-8)*1024);

//    mapFile(channel, SEGMENT_BASE, fileSharedOffset-8, 8, false, true, true);
//    mapFile(channel, sharedStartPage+SEGMENT_BASE, sharedPageCount, fileSharedOffset, true, false, false);

    System.out.println();
    System.out.println();
    System.out.println();
    System.out.println();
    System.out.println("---------------------------------- HIGH  MEMORY ---------------------------------");
    disassemble(symbols, processMemory, SEGMENT_BASE+sharedStartPage*1024, SEGMENT_BASE+(sharedStartPage+sharedPageCount)*1024);
   }
}

