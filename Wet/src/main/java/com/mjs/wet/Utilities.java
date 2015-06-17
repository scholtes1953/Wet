package com.mjs.wet;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;
import java.util.Map.Entry;
import java.util.StringJoiner;

public class Utilities {

	/**
	 * Export a Map of String to Double to a csv file
	 * @param map Map to export
	 * @param fileName file to export to
	 * @param header header row of exported file
	 */
	public static void exportMap(Map<String, Double> map, String fileName, String header) {
		BufferedWriter writer = null;
		try {
			writer = new BufferedWriter(new FileWriter(fileName));
			writer.write(header);
			writer.newLine();
			for(Entry<String,Double> entry: map.entrySet()) {
				StringBuilder sj = new StringBuilder();
				sj.append("\"").append(entry.getKey()).append("\"").append(",");
				sj.append(String.format("%5.2E", entry.getValue()));
				writer.write(sj.toString());
				writer.newLine();
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}finally {
			if (writer != null) {
				try {
					writer.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}
	
	/**
	 * Create a String key from county name and state: Travis + TX becomes Travis|TX
	 * @param county County name
	 * @param state State name
	 * @return single String
	 */
	static public String createCountyStateKey(String county, String state) {
		return county + "|" + state;
	}

	/**
	 * Finds commas, but ignores commas enclosed in a quoted string
	 * @return regex for use in String.split()
	 */
	static public String embeddedCommaRegex() {
        String otherThanQuote = " [^\"] ";
        String quotedString = String.format(" \" %s* \" ", otherThanQuote);
        String regex = String.format("(?x) "+ // enable comments, ignore white spaces
                ",                         "+ // match a comma
                "(?=                       "+ // start positive look ahead
                "  (                       "+ //   start group 1
                "    %s*                   "+ //     match 'otherThanQuote' zero or more times
                "    %s                    "+ //     match 'quotedString'
                "  )*                      "+ //   end group 1 and repeat it zero or more times
                "  %s*                     "+ //   match 'otherThanQuote'
                "  $                       "+ // match the end of the string
                ")                         ", // stop positive look ahead
                otherThanQuote, quotedString, otherThanQuote);
        return regex;
	}
}
