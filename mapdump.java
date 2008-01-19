import java.util.*;

public class mapdump {
    public static void main(String[] args) throws Exception
    {
	if(args.length!=1) {
	    System.out.println("need argument");
	    return;
	}
	MapPackParser mp = new MapPackParser(args[0]);
	System.out.print(mp);
	Iterator itp = mp.projects.iterator();
	while(itp.hasNext()) {
	    Project p = (Project) itp.next();
	    Iterator itm = p.maps.iterator();
	    while(itm.hasNext()) {
		Map m = (Map) itm.next();
		System.out.print(m);
	    }
	}
    }
}
