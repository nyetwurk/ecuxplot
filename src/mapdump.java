import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Date;

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
		System.err.print(Options.Usage());
		throw new Exception("invalid args");
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
		} else if(args[i].equals("-d")) {
		    this.format = Map.FORMAT_DUMP;
		} else if(args[i].equals("-o")) {
		    this.format = Map.FORMAT_OLD_XDF;
		} else if(args[i].equals("-x")) {
		    this.format = Map.FORMAT_XDF;
		} else {
		    this.filename=args[i];
		}
	    }
	    if (this.filename == null) {
		System.err.print(Options.Usage());
		throw new Exception("You must specify an input filename");
	    }
	    if (this.format == Map.FORMAT_OLD_XDF && image == null) {
		System.err.print(Options.Usage());
		throw new Exception("-o requires -i <image.bin> to detect image size");
	    }
	    if (this.format == Map.FORMAT_XDF && image == null) {
		System.err.print(Options.Usage());
		throw new Exception("-x requires -i <image.bin> to detect image size");
	    }
	}
	static String Usage() {
	    return
		  "Usage: mapdump [options] mappack.kp\n"
		+ "Options:\n"
		+ " -r <mappack.kp> [-r ...]     annotate with descriptions from matching maps also in these mappacks (ignored if -x is used)\n"
		+ " -i <image.bin>               generate min/max columns based on this image\n"
		+ " -d                           raw dump\n"
		+ " -o                           old xdf dump (requires -i <image.bin>)\n"
		+ " -x                           xdf dump (requires -i <image.bin>)\n";
	}
    }

    public static void main(String[] args) throws Exception
    {
	Options opts=null;
	try {
	    opts = new Options(args);
	} catch (Exception e) { return; }
	Parser mp = new Parser(opts.filename);
	ArrayList<Parser> refs = new ArrayList<Parser>();
	ByteBuffer imagebuf=null;
	String refsHeader="";
	for(String s: opts.reference) {
	    refs.add(new Parser(s));
	    refsHeader+=",\"" + s + "\"";
	}
	if(opts.image!=null) {
	    MMapFile mmap = new MMapFile(opts.image, ByteOrder.LITTLE_ENDIAN);
	    imagebuf = mmap.getByteBuffer();
	}
	switch(opts.format) {
	    case Map.FORMAT_CSV:
		System.out.print(Map.CSVHeader()+refsHeader);
		System.out.println();
		break;
	    case Map.FORMAT_OLD_XDF:
		System.out.print("XDF\n1.110000\n\n");
		break;
	    case Map.FORMAT_XDF:
		Date date = new Date();
		System.out.print("<!-- Written " + date.toString() + " -->\n");
		System.out.print("<XDFFORMAT version=\"1.50\">\n");
		break;
	    default: break;
	}
	for(Project p: mp.projects) {
	    System.out.print(p.toString(opts.format, imagebuf));
	    /*
	    for(Folder f: p.folders) {
		System.out.print(f.toString(opts.format));
		System.out.println();
	    }
	    */
	    if (p.maps==null) continue;

	    for(Map m: p.maps) {
		System.out.print(m.toString(opts.format, imagebuf));
		if(opts.format == Map.FORMAT_CSV) {
		    for(Parser pa: refs) {
			ArrayList<Map> matches = pa.find(m);
			if(matches.size()>0) {
			    Map r = matches.get(0);
			    System.out.print(",\"" + r.name + "\"");
			} else {
			    System.out.print(",\"\"");
			}
		    }
		    System.out.println();
		}
	    }
	}
	if (opts.format==Map.FORMAT_XDF)
	    System.out.print("</XDFFORMAT>\n");
    }
}
