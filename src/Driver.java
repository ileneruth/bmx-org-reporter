import java.io.Console;
import java.util.HashMap;
import java.util.Map;

public class Driver {


	private static final String EU = "eu-gb";
	private static final String NG = "ng";
	private static final String AUS = "au-syd";	
	
	public static void main(String[] args) {
		
		HashMap<Integer, String> int2Region = new HashMap<Integer, String>();
		int2Region.put(1, EU);
		int2Region.put(2, NG);
		int2Region.put(3, AUS);
		
		Console console = System.console();
		if (console == null) {
			System.out.println("No console found");
			System.exit(1);
		}
		int region = 0;

		while ((region < 1) || (region > 3)) {				
			console.printf("1 - EU-GB\n");
			console.printf("2 - NG\n");
			console.printf("3 - AU-SYD\n");
			console.printf("Q - Quit\n\n");
			String entry = console.readLine("Enter the number for the region you need information about: ");
			
			if (testExit(entry)) {
				console.printf("Bye!!\n\n");
				System.exit(1);
			}
			
			try {
				region = (Integer.parseInt(entry));
			} catch (NumberFormatException nfe) {
				console.printf("Invalid argument.  Try again.\n\n");
			}			
		}
		

		AccessListLookup.getOrganizationUserRoles(int2Region.get(region));
	}
	
	private static boolean testExit(String entry) {
		if (entry.toUpperCase().equals("Q")) {
			return true;
		}
		return false;
	}
}
