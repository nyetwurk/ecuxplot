public class mapdump {
    public static void main(String[] args) throws Exception
    {
	if(args.length!=1) {
	    System.out.println("need argument");
	    return;
	}
	MapPackParser mp = new MapPackParser(args[0]);
	System.out.print(mp);
    }
}
