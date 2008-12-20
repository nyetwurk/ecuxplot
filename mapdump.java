import java.nio.channels.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Iterator;

import org.nyet.mappack.*;
import org.nyet.util.*;

public class mapdump {
    private static class Options {
	String filename = null;
	ArrayList<String> reference = new ArrayList<String>();
	String image = null;
	int format = Map.FORMAT_CSV;
	public Options(String[] args) throws Exception {
	    if(args.length<1) {
		throw new Exception("bad syntax");
	    }
	    int i;
	    for(i=0;i<args.length;i++) {
		if(args[i].equals("-r")) {
		    i++;
		    if(i>=args.length)
			throw new Exception("-r requires argument");
		    this.reference.add(args[i]);
		} else if(args[i].equals("-i")) {
		    i++;
		    if(i>=args.length)
			throw new Exception("-i requires argument");
		    this.image=args[i];
		} else if(args[i].equals("-x")) {
		    this.format = Map.FORMAT_XDF;
		} else {
		    this.filename=args[i];
		}
	    }
	}
    }

    public static void main(String[] args) throws Exception
    {
	Options opts = new Options(args);
	Parser mp = new Parser(opts.filename);
	ArrayList<Parser> refs = new ArrayList<Parser>();
	ByteBuffer imagebuf=null;
	Iterator i = opts.reference.iterator();
	String refsHeader="";
	while(i.hasNext()) {
	    String s = (String)i.next();
	    refs.add(new Parser(s));
	    refsHeader+="\"" + s + "\",";
	}
	if(opts.image!=null) {
	    MMapFile mmap = new MMapFile(opts.image, ByteOrder.LITTLE_ENDIAN);
	    imagebuf = mmap.getByteBuffer();
	}
	// System.out.print(mp);
	if(opts.format == Map.FORMAT_CSV) {
	    System.out.print(Map.CSVHeader()+refsHeader);
	    System.out.println();
	} else {
	    System.out.print("XDF\n1.110000\n\n");
	}
	for(Project p: mp.projects) {
	    System.out.print(p.toString(opts.format, imagebuf));
	    for(Map m: p.maps) {
		System.out.print(m.toString(opts.format, imagebuf));
		for(Parser pa: refs) {
		    ArrayList<Map> matches = pa.find(m);
		    if(matches.size()>0) {
			Map r = matches.get(0);
			System.out.print("\"" + r.name + "\",");
		    } else {
			System.out.print("\"\",");
		    }
		}
		System.out.println();
	    }
	}
    }
}
