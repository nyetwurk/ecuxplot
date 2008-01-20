import java.io.*;
import java.util.*;
import au.com.bytecode.opencsv.*;

public class csvdump {
    public static void main(String[] args) throws Exception
    {
	CSVReader reader = new CSVReader(new FileReader(args[0]));
	String [] nextLine;
	while((nextLine = reader.readNext()) != null) {
	    System.out.println(Arrays.toString(nextLine));
	}
    }
}
