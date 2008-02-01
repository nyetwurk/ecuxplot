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
		    reference.add(args[i]);
		} else if(args[i].equals("-i")) {
		    i++;
		    if(i>=args.length)
			throw new Exception("-i requires argument");
		    image=args[i];
		} else {
		    filename=args[i];
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
	    refsHeader+=",\"" + s + "\"";
	}
	if(opts.image!=null) {
	    MMapFile mmap = new MMapFile(opts.image, ByteOrder.LITTLE_ENDIAN);
	    imagebuf = mmap.getByteBuffer();
	}
	// System.out.print(mp);
	Iterator itp = mp.projects.iterator();
	System.out.print(Map.CSVHeader()+refsHeader);
	System.out.println();
	while(itp.hasNext()) {
	    Project p = (Project) itp.next();
	    Iterator itm = p.maps.iterator();
	    while(itm.hasNext()) {
		Map m = (Map) itm.next();
		System.out.print(m.toCSV(imagebuf));
		Iterator itr = refs.iterator();
		while(itr.hasNext()) {
		    Parser pa = (Parser) itr.next();
		    ArrayList<Map> matches = pa.find(m);
		    if(matches.size()>0) {
			Map r = matches.get(0);
			System.out.print(",\"" + r.name + "\"");
		    }
		}
		System.out.println();
	    }
	}
    }
}
