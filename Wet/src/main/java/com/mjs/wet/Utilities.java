package com.mjs.wet;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;
import java.util.Map.Entry;
import java.util.StringJoiner;

public class Utilities {

	/**
	 * Exports a Map<String,Double> to a csv file
	 * @param msaPeopleInches
	 */
	public static void exportMap(Map<String, Double> msaPeopleInches) {
		BufferedWriter writer = null;
		try {
			writer = new BufferedWriter(new FileWriter("peopleinches.csv"));
			writer.write("MSA,PeopleInches");
			writer.newLine();
			for(Entry<String,Double> entry: msaPeopleInches.entrySet()) {
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
	
	// Travis + TX -> Travis|TX
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
