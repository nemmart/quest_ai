import java.io.*;
import java.net.*;
import java.util.*;

import hw.*;
import os.*;
import debug.*;

public class StartStop {
   static final Set<String> SKIP_INSTRUCTIONS=new HashSet<String>(Arrays.asList(
             "WSEQ", "WSNE", "WSLT", "WSLE", "WSGT", "WSGE", "WUSGT", "WUSGE",
             "WSEQI", "WSNEI", "WSLEI", "WSGTI", "WUGTI", "WULEI", "NSANA",  "WSANA",
             "WSKBZ", "WSKBO", "FSEQ", "FSNE", "FSGT", "FSGE", "FSLE", "FSLT",
             "FSNO", "FSNM", "FSNU", "FSNUD", "FSNUO", "FSNOD", "FSNER",
             "ISZTS", "DSZTS", "XNISZ", "XNDSZ", "XWISZ", "XWDSZ", "LNISZ", "LNDSZ", "LWISZ", "LWDSZ"
   ));

   static int resolveXTarget(Memory memory, int addr, int opcode) {
    int     index=(opcode>>11) & 0x03;
    int     offset=memory.readWord(addr+1);
    boolean indirect=(offset & 0x8000)!=0;

    if(indirect) {
     System.err.println("CAUTION: INDIRECT INDEXING");
     return -1;
    }
    if(index==1) {
     int relative=(offset<<17)>>17;
     return (addr+1)+relative;
    }
    if(index==0) {
     return (addr & 0xFFFF0000) | (offset & 0x7FFF);
    }
    return -1;      // ac2/ac3 relative — can't resolve statically
   }

   static int resolveLTarget(Memory memory, int addr, int opcode) {
    int     index=(opcode>>11) & 0x03;
    int     offset=memory.readWide(addr+1);
    boolean indirect=(offset<0);

    if(indirect) {
     System.err.println("CAUTION: INDIRECT INDEXING");
     return -1;
    }
    if(index==1) {
     return (addr+1)+offset;
    }
    if(index==0) {
     return offset & 0x7FFFFFFF;
    }
    return -1;
   }

   static public TreeSet reachable(SymbolTable symbols, Memory memory, int start, int stop) {
    TreeSet<Integer> results=new TreeSet<Integer>();
    Set<Integer>     process=new HashSet<Integer>();
    Set<Integer>     additional=new HashSet<Integer>();
    int              opcode;
    Instruction      instruction;
    String           name, format;
    int              length, displacement, target;
    int              epilog=symbols.nameToAddress.get("I.EPILOG");

    for(int symbol : symbols.addressToName.keySet()) {
      if(symbol>=start && symbol<stop)
        process.add(symbol);
    }

    while(process.size()>0) {
      for(int pc : process) {
        opcode=memory.readWord(pc);
        if(opcode==0xC619 && memory.readWord(pc+1)==0x8006) {
          results.add(pc);
          results.add(pc+1);
          results.add(pc+2);
          if(memory.readWord(pc+2)!=0310) {  // 0310 terminates the process
            additional.add(pc+3);
            additional.add(pc+4);
          }
          continue;
        }
        instruction=Instruction.decode(false, opcode);
        if(instruction==null || instruction.name==null) {
         System.err.println("CAUTION: INSTRUCTION DECODE FAILED!");
         continue;
        }

        name=instruction.name;
        if(name.endsWith("*"))
         name=name.substring(0, name.length()-1);
        format=instruction.instructionFormat;
        length=(format!=null) ? Disassembler.wordLength(format) : 1;

        for(int i=0;i<length;i++)
          results.add(pc+i);

        if(name.equals("WRTN") || name.equals("DERR")) {
        }
        else if(name.equals("WBR")) {
         displacement=((opcode>>7) & 0xF0) + ((opcode>>6) & 0x0F);
         displacement=(displacement<<24)>>24;
         additional.add(pc+displacement);
        }
        else if(name.equals("XJMP")) {
         target=resolveXTarget(memory, pc, opcode);
         if(target>=0)
          additional.add(target);
        }
        else if(name.equals("LJMP")) {
         target=resolveLTarget(memory, pc, opcode);
         if(target>=0)
          additional.add(target);
        }
        else if(name.equals("XJSR")) {
         target=resolveXTarget(memory, pc, opcode);
         if(target!=epilog)
           additional.add(pc+2);
        }
        else if(name.equals("LJSR")) {
         target=resolveLTarget(memory, pc, opcode);
         if(target!=epilog)
           additional.add(pc+3);
        }
        else if(name.equals("XCALL")) {
         target=resolveXTarget(memory, pc, opcode);
         if(target>=0)
           additional.add(target);
         additional.add(pc+3);
        }
        else if(name.equals("LCALL"))
         additional.add(pc+4);
        else if(name.equals("XNDO") || name.equals("XWDO")) {
         displacement=memory.readWord(pc+2);
         additional.add(pc+3);
         additional.add(pc+1+displacement);
        }
        else if(name.equals("LNDO") || name.equals("LWDO")) {
         displacement=memory.readWord(pc+3);
         additional.add(pc+4);
         additional.add(pc+1+displacement);
        }
        else if(SKIP_INSTRUCTIONS.contains(name)) {
         additional.add(pc+length);
         additional.add(pc+length+1);
        }
        else if(format!=null && format.equals("novaCompute")) {
          if(name.equals("JMP") || name.equals("JSR"))
            System.err.printf("CAUTION: NOVA JUMP AT ADDRESS %x\n", pc);
          int skipCode=opcode & 0x07;
          if(skipCode==0)
           additional.add(pc+1);
          else if(skipCode==1)
           additional.add(pc+2);
          else {
           additional.add(pc+1);
           additional.add(pc+2);
          }
        }
        else
          additional.add(pc+length);
      }
      process.clear();
      for(Integer pc : additional) {
        if(!results.contains(pc))
          process.add(pc);
      }
      additional.clear();
     }
     return results;
   }

   static public void main(String[] args) {
    FSTerminal  terminal;
    String      file;
    FSChannel   channel;
    PageSet     filePages;
    OSProcess   process;
    SymbolTable symbols;
    Memory      fileMemory, processMemory;
    Integer     start, stop, memoryStop;

    if(args.length!=5) {
     System.err.println("Usage: java StartStop <dir> <PR file> <Start Symbol> <Stop Symbol> <Const Last Symbol>");
     System.exit(1);
    }

    FS.initializeWithPath(args[0]);

    file=args[1].toUpperCase();
    if(file.endsWith(".PR"))
     file=file.substring(0, file.length()-3);

    channel=FSChannel.openForPagedIO(":" + file + ".PR", true);
    filePages=channel.pageSet();

    process=new OSProcess(":", file);
    symbols=process.symbols;
    processMemory=process.memory;

    int sharedStartPage=filePages.readWord(0x10F);
    int sharedPageCount=filePages.readWord(0x113);
    int fileSharedOffset=filePages.readWord(0x11A);
    int SEGMENT_BASE=OSProcess.SEGMENT_BASE*1024;

    start=symbols.nameToAddress.get(args[2]);
    stop=symbols.nameToAddress.get(args[3]);
    if(start==null) throw new RuntimeException("Symbol '" + args[2] + "' not found");
    if(stop==null) throw new RuntimeException("Symbol '" + args[3] + "' not found");

    TreeSet<Integer> reachable=reachable(symbols, processMemory, start.intValue(), stop.intValue());
    List<Integer>    addresses=new ArrayList<Integer>();
    int              decompile=0;

    addresses.addAll(symbols.addressToName.keySet());

    if(args.length==5) {
      memoryStop=symbols.nameToAddress.get(args[4]);
      if(memoryStop==null) throw new RuntimeException("Symbol '" + args[4] + "' not found");
      System.out.printf("mem %08x %08x\n", SEGMENT_BASE, SEGMENT_BASE+(fileSharedOffset-8)*1024);
      System.out.printf("mem %08x %08x\n", SEGMENT_BASE+sharedStartPage*1024, memoryStop.intValue());
    }

    for(int i=0;i<addresses.size();i++) {
      int under=stop, first=addresses.get(i), last=first, found=0;

      if(first<start || first>=stop)
        continue;

      if(i<addresses.size()-1)
        under=addresses.get(i+1);
      for(int pc : reachable) {
        if(pc>=first && pc<under)
          found++;
        if(pc>last && pc<under)
          last=pc;
      }
      System.out.printf("code %08x %08x %s\n", first, last+1, symbols.addressToName.get(first));
      if(last+1!=under) {
        if(under==stop && args.length==5)
          System.out.printf("mem %08x %08x\n", stop, SEGMENT_BASE+(sharedStartPage+sharedPageCount)*1024);
        else
          System.out.printf("mem %08x %08x\n", last+1, under);
      }
      decompile+=last-first+1;
    }

    System.err.println();
    System.err.println("Summary:");
    System.err.println(reachable.size() + " reachable instruction words found!");
    System.err.println(decompile + " instruction words found!");
   }
}

