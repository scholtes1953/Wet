package com.mjs.wet;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;

/**
 * @author mscholtes
 *
 */
public class Wet {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		String wbanFile = "wbanmasterlist.psv";
		String CSAFile = "2013Feb_CSAList1.csv";
		String populationFile = "CBSA-EST2012-01.csv";
		String precipFile = "201505precip.txt";
		Wet wet = new Wet(wbanFile, CSAFile, populationFile, precipFile);
	}

	public Wet(String wbanFile, String CSAFile, String popFile, String precipFile) {
		BufferedReader wbanReader = getReader(wbanFile); // wban -> county relationship
		BufferedReader csaReader = getReader(CSAFile); // MSA -> county relationships
		BufferedReader popReader = getReader(popFile); // MSA -> population relationship
		BufferedReader precipReader = getReader(precipFile); // WBAN -> precipitation data
		
		HashMap<String,String> msaCountyMap = new HashMap<String,String>();
		
		int lineCount;
		String line;

		try {
			String[] fields;
			for (lineCount = 0; (line = csaReader.readLine()) != null; ++lineCount) {
				if (lineCount ==0) {
					// parse header line for field names
					fields = line.split(",");
					System.out.println(line);
					System.out.println(fields);
				}
			}
		} catch (IOException io) {
			System.err.println("IOException: " + io.getMessage());
		}finally {
			if (wbanReader != null)
				try {
					wbanReader.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			if (csaReader != null) {
				try {
					csaReader.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}
	
	private BufferedReader getReader(String fileName) {
		InputStream theStream = this.getClass().getResourceAsStream("/"+fileName);
		BufferedReader br = null;
		if (theStream != null) {
			br = new BufferedReader(new InputStreamReader(theStream));
		}
		return br;
	}
	
	static class Pair<K, V>{
		private final K element0;
		private final V element1;
		
		public static <K, V> Pair<K, V> createPair(K element0, V element1){
			return new Pair<K, V>(element0, element1);
		}
		public Pair(K element0, V element1) {
			this.element0 = element0;
			this.element1 = element1;
		}
		public K getElement0() { return element0; }
		public V getElement1() { return element1; }
		
	}
}
