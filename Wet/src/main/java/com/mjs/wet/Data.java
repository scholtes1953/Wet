package com.mjs.wet;

import java.io.BufferedReader;
import java.io.IOException;
import java.text.NumberFormat;
import java.text.ParseException;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mjs.wet.Wet.Pair;

/**
 * Loads and manipulates specific data file types and formats
 * 
 * @author mscholtes
 *
 */
public class Data {
	static Map<String, String> states = new HashMap<String,String>();
	
	/**
	 * 
	 * @param counties HashMap of MSA to List of counties
	 * @param precipReader BufferedReader for WBAN precip. data
	 * @param wbanCounties Map of WBAN to county
	 * @return Map of MSA name to inches of rain
	 */
	static public Map<String, Double> mergeRainWithCounties(
			Map<String, String> counties, 
			BufferedReader precipReader,
			Map<String, Pair<String,String>> wbanCounties)
	{
		// the precip list is huge, so instead of loading all into memory,
		// stream it. It is sorted by WBAN
		//
		// 	until reach end of precip 
		//  	get and sum precip for 7am - midnight until wban changes
		// 		look up the county and state for this wban
		//  	get MSA for county,state
		//  	save rain for msa
		Map<String, Double> msaRain = new HashMap<String, Double>();
		
		boolean reachedEOF = false;
		/*
		 * Precip file header and sample line:
		 * Wban,YearMonthDay,Hour,Precipitation,PrecipitationFlag
		 * 00103,20150523,03,0.01,
		 */
		try {
			List<String> fieldList = getHeaderFields(precipReader, ",");
			int WBAN_INDEX = fieldList.indexOf("Wban");
			int HOUR_INDEX = fieldList.indexOf("Hour");
			int PRECIP_INDEX = fieldList.indexOf("Precipitation");
			
			Pair<String, Double> wbanPrecip = getDaytimePrecip(precipReader, WBAN_INDEX, HOUR_INDEX, PRECIP_INDEX); //"00102", 0.01
			double totalRain = 0.0d;
			String oldWban;
			while(!reachedEOF && wbanPrecip != null) {
				oldWban = wbanPrecip.getElement0();
				totalRain = wbanPrecip.getElement1().doubleValue();
				do{
					wbanPrecip = getDaytimePrecip(precipReader, WBAN_INDEX, HOUR_INDEX, PRECIP_INDEX);
					if (wbanPrecip == null) 
						reachedEOF = true;
					else {
						totalRain += wbanPrecip.getElement1().doubleValue();
					}
				}while(wbanPrecip != null && wbanPrecip.getElement0().equals(oldWban));
				// at this point, totalRain is the sum for oldWban
				Pair<String, String> countyState = getNormalizedCountyState(wbanCounties, oldWban);
				if (countyState != null) {
					String MSA = getMSAForCounty(countyState, counties);
					if (MSA != null) {
						msaRain.put(MSA,  totalRain);
					}
				}
			}
		}catch(IOException e) {
			System.out.println(e.getMessage());
		}
		return msaRain;
	}

	/**
	 * Create a map of County|State to MSA.
	 * <p>
	 * For efficiency the key is a concatenation  of county and state.
	 * @param reader BufferedReader for table of MSAs and their counties
	 * @return Map of CountyState to MSA
	 */
	static public Map<String, String> loadMsaCountyMap(BufferedReader reader){
		// those fields we wish to extract
		final String MSA_STRING = "Metropolitan Statistical Area";
		final int MSA_PROPERTY = 0;
		final int METRO_MICRO_PROPERTY = 1;
		final int COUNTY_PROPERTY = 2;
		final int STATE_PROPERTY = 3;
		
		String[] properties = new String[4];
		properties[MSA_PROPERTY] = "CBSA Title";
		properties[METRO_MICRO_PROPERTY] = "Metropolitan/Micropolitan Statistical Area";
		properties[COUNTY_PROPERTY] = "County/County Equivalent";
		properties[STATE_PROPERTY] = "State Name";

		// field index of given property in the data lines
		int[] indexes = new int[properties.length];
		
		HashMap<String, String> msaMap = new HashMap<String, String>();
		
		int lineCount;
		String line;

		try {
			String[] fields;
			for (lineCount = 0; (line = reader.readLine()) != null; ++lineCount) {
				fields = line.split(Utilities.embeddedCommaRegex(), -1);
				// we assume header row is first line, and all subsequent rows are data
				if (lineCount ==0) {
					// parse header line for field names
					ArrayList<String> fieldList = new ArrayList<String>(Arrays.asList(fields));

					for(int pi = 0; pi < properties.length; pi++) {
						indexes[pi] = fieldList.indexOf(properties[pi]);							
					}
				}else {
					// get MSA, county, state
					String MSA = normalizeField(fields[indexes[MSA_PROPERTY]]);
					boolean isMetro = (fields[indexes[METRO_MICRO_PROPERTY]].equals(MSA_STRING));
					String county = normalizeCounty(fields[indexes[COUNTY_PROPERTY]]);
					String state = normalizeState(fields[indexes[STATE_PROPERTY]]);
					String countyStateKey = Utilities.createCountyStateKey(county, state);
		
					if (isMetro) {
						msaMap.put(countyStateKey, MSA);
					}
				}
			}
		} catch (IOException io) {
			System.err.println("IOException: " + io.getMessage());
		}finally {
			if (reader != null)
				try {
					reader.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
		}
		return msaMap;
	}
	
	/**
	 * Create a map of WBANs to Counties 
	 * @param wbanReader BufferedReader to master WBAN list
	 * @return Map of WBAN to a County, State Pair
	 */
	static public Map<String, Pair<String, String>> loadWBANCountyMap(
			BufferedReader wbanReader) 
	{
		
		/*
		 * Wban file header and smaple lines:
		 * "REGION"|"WBAN_ID"|"STATION_NAME"|"STATE_PROVINCE"|"COUNTY"|...
		 * "240/940"|"00000"|"WOLF POINT"|"MT"||
		 * "003"|"00300"|"STANLEY MUNICIPAL AIRPORT"|"ND"|"MOUNTRAIL"|....
		 * 
		 */
		Map<String, Pair<String, String>> wbanCounties = new HashMap<String, Pair<String,String>>();
		List<String> fieldList;
		try {
			fieldList = getHeaderFields(wbanReader, "\\|");
			int WBAN_ID_INDEX = fieldList.indexOf("\"WBAN_ID\"");
			int COUNTY_INDEX = fieldList.indexOf("\"COUNTY\"");
			int STATE_INDEX = fieldList.indexOf("\"STATE_PROVINCE\"");
			String wban="", county="", state="";
			int maxIndex = Integer.max(WBAN_ID_INDEX, COUNTY_INDEX);
			maxIndex = Integer.max(maxIndex, STATE_INDEX);

			String line;
			while((line = wbanReader.readLine()) != null) {
				String[] fields = line.split("\\|");
				if (fields.length < maxIndex+1) continue;
				wban = normalizeField(fields[WBAN_ID_INDEX]);
				county = normalizeCounty(fields[COUNTY_INDEX]);
				if (!county.isEmpty()) {
					state = normalizeState(fields[STATE_INDEX]);
					if (!state.isEmpty())
						wbanCounties.put(wban, Pair.createPair(county, state));						
				}
				
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		return wbanCounties;
	}
	
	/**
	 * extrapolate populations for MSAs to a given date.
	 * We have hard-coded dependency on the specific data file. 
	 * @param popReader BufferedReader to population data
	 * @param extrapolationDate LocalDate for population estimate
	 * @return Map of MSA to population
	 */
	static public Map<String, Integer> getExtrapolatedPopulations(
			BufferedReader popReader, LocalDate extrapolationDate) 
	{

		/*
		 * Population file header and sample data: (numbers as of July 1 of given year)
		 * CBSA Code,Metro Division Code,Metropolitan statistical areas,Census,Estimates base,2010,2011,2012
		 * 10180,,"Abilene, TX","165,252","165,252","165,578","166,481","166,963"
		 */
		Map<String, Integer> msaPop = new HashMap<String, Integer>();
		List<String> fieldList;
		try {
			String regex = Utilities.embeddedCommaRegex();
			fieldList = getHeaderFields(popReader, ",");
			int MSA_INDEX = fieldList.indexOf("Metropolitan statistical areas");
			int INDEX_2010 = fieldList.indexOf("2010");
			int INDEX_2011 = fieldList.indexOf("2011");
			int INDEX_2012 = fieldList.indexOf("2012");
			String line;
			while ((line = popReader.readLine()) != null) {
				String[] fields = line.split(regex, -1);
				String msa = normalizeField(fields[MSA_INDEX]);
				Number pop2010 = NumberFormat.getNumberInstance(java.util.Locale.US).parse(normalizeField(fields[INDEX_2010]));
				Number pop2011 = NumberFormat.getNumberInstance(java.util.Locale.US).parse(normalizeField(fields[INDEX_2011]));
				Number pop2012 = NumberFormat.getNumberInstance(java.util.Locale.US).parse(normalizeField(fields[INDEX_2012]));
				Pair<Number, LocalDate> val2010 = Pair.createPair(pop2010, LocalDate.of(2010, Month.JULY, 1));
				Pair<Number, LocalDate> val2011 = Pair.createPair(pop2011, LocalDate.of(2011, Month.JULY, 1));
				Pair<Number, LocalDate> val2012 = Pair.createPair(pop2012, LocalDate.of(2012, Month.JULY, 1));
				int extPop = extrapolate(val2010, val2011, val2012, extrapolationDate);
				msaPop.put(msa, extPop);
			}
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ParseException e) {
			e.printStackTrace();
		}

		return msaPop;
	}

	/*
	 * Extrapolate population from three different times to a given future time
	 */
	static int extrapolate(Pair<Number, LocalDate> val2010,
			Pair<Number, LocalDate> val2011, Pair<Number, LocalDate> val2012,
			LocalDate extrapolationDate) {
		// TODO: experiment with cubic fit (parabola fit to 3 points),
		// least-square fit, or spline.
		// In the interest of time, here we simply use the latest value
		return val2012.getElement0().intValue();
	}

	
 
	/**
	 * Get the name of the MSA that a county belongs to, e.g.:
	 * Travis|TX is in the  "Austin-Round Rock, TX" MSA
	 * @param countyStatePair a county, state Pair
	 * @param counties a Map of County|State to MSA
	 * @return the MSA to which the county belongs
	 */
	static public String getMSAForCounty(Pair<String, String> countyStatePair,
			Map<String, String> counties) {
		String countyState = Utilities.createCountyStateKey(countyStatePair.getElement0(), countyStatePair.getElement1());
		return counties.get(countyState);
	}


	/**
	 * Look up the county and state in the wban master index
	 * <p>
	 * County is normalized to the format "BROWARD", 
	 * and State is normalized to the format "MT"
	 * @param wbanCounties map of WBAN to County, State
	 * @param wban WBAN id
	 * @return a normalized County, State Pair
	 */
	static public Pair<String, String> getNormalizedCountyState(
			Map<String,Pair<String,String>> wbanCounties, String wban)
	{
		return wbanCounties.get(wban);
	}


	/**
	 * Read a data line from the wban precipitation file
	 * @param reader BufferedReader to wban precipitation file
	 * @param wbanIndex Index of the WBAN_ID in the line
	 * @param hourIndex Index of the hour in the line
	 * @param precipIndex Index of the precipitation amount in the line
	 * @return a wban, precipitation Pair, where the rainfall amount is only nonzero for the hours of 7AM to midnight, 
	 * or null if reached EOF
	 * @throws IOException if the reader encounters a problem other than EOF
	 */
	static public Pair<String, Double> getDaytimePrecip(BufferedReader reader, int wbanIndex, int hourIndex, int precipIndex) throws IOException {
		String line = reader.readLine();
		if (line == null)
			return null;
		
		String[] fields = line.split(",");
		String wban = normalizeField(fields[wbanIndex]);
		Double rain = new Double(0.0);
		try {
			int hour = Integer.parseUnsignedInt(fields[hourIndex]); // 1...24 are the hours ending at 1AM...midnight
			String sRain = fields[precipIndex];	
			if (hour > 7) {
				rain = Double.parseDouble(sRain);	// on "" or "T" throws 			
			}		
		}catch(NumberFormatException ex) {
			// do nothing
		}
		return Pair.createPair(wban, rain);
	}
	

	
	/**
	 * Get the names of the data fields in a delimited file from the header.  This routine is only
	 * expected to be called for the first read of a BufferedReader.
	 * @param reader BufferedReader 
	 * @param regex for this particular file, e.g. "," for simple csv, or "\\"" for simple psv
	 * @return List of field names
	 * @throws IOException if the reader encounters a problem other than EOF
	 */
	static public List<String> getHeaderFields(BufferedReader reader, String regex) throws IOException {
		String header = reader.readLine();
		if (header == null) {
			throw new IOException("unable to read header from file");		
		}else {
			String fields[] = header.split(regex);
			return new ArrayList<String>(Arrays.asList(fields));
		}
	}

	/**
	 * Generic cleanup of a field, e..g "Foo" to Foo
	 * @param field a field from a line of a delimited file 
	 * @return modified field
	 */
	static public String normalizeField(String field) {
		// remove surrounding double quotes
		return field.replaceAll("^\"|\"$", "");
	}

	/**
	 * Normalizes a state name to a standard form: Montana to MT, "MT" to MT
	 * @param state a state name
	 * @return normalized form of state name
	 */
	static public String normalizeState(String state) {		
		String trimmed = normalizeField(state).trim();
		if (trimmed.length() > 2)
			trimmed = states.get(trimmed);
		return trimmed;
	}

	/**
	 * Normalize a county name to a standard form: Travis County to TRAVIS, "TRAVIS" to TRAVIS
	 * @param county a County name
	 * @return normalized county name
	 */
	static public String normalizeCounty(String county) {
		String trimmed = normalizeField(county).trim().toUpperCase();
		int countyIndex = trimmed.indexOf("COUNTY");
		if (countyIndex >= 0) {
			trimmed = trimmed.substring(0, countyIndex).trim();
		}
		return trimmed;
	}
	
	static {
		states.put("Alabama","AL");
		states.put("Alaska","AK");
		states.put("Alberta","AB");
		states.put("American Samoa","AS");
		states.put("Arizona","AZ");
		states.put("Arkansas","AR");
		states.put("Armed Forces (AE)","AE");
		states.put("Armed Forces Americas","AA");
		states.put("Armed Forces Pacific","AP");
		states.put("British Columbia","BC");
		states.put("California","CA");
		states.put("Colorado","CO");
		states.put("Connecticut","CT");
		states.put("Delaware","DE");
		states.put("District Of Columbia","DC");
		states.put("Florida","FL");
		states.put("Georgia","GA");
		states.put("Guam","GU");
		states.put("Hawaii","HI");
		states.put("Idaho","ID");
		states.put("Illinois","IL");
		states.put("Indiana","IN");
		states.put("Iowa","IA");
		states.put("Kansas","KS");
		states.put("Kentucky","KY");
		states.put("Louisiana","LA");
		states.put("Maine","ME");
		states.put("Manitoba","MB");
		states.put("Maryland","MD");
		states.put("Massachusetts","MA");
		states.put("Michigan","MI");
		states.put("Minnesota","MN");
		states.put("Mississippi","MS");
		states.put("Missouri","MO");
		states.put("Montana","MT");
		states.put("Nebraska","NE");
		states.put("Nevada","NV");
		states.put("New Brunswick","NB");
		states.put("New Hampshire","NH");
		states.put("New Jersey","NJ");
		states.put("New Mexico","NM");
		states.put("New York","NY");
		states.put("Newfoundland","NF");
		states.put("North Carolina","NC");
		states.put("North Dakota","ND");
		states.put("Northwest Territories","NT");
		states.put("Nova Scotia","NS");
		states.put("Nunavut","NU");
		states.put("Ohio","OH");
		states.put("Oklahoma","OK");
		states.put("Ontario","ON");
		states.put("Oregon","OR");
		states.put("Pennsylvania","PA");
		states.put("Prince Edward Island","PE");
		states.put("Puerto Rico","PR");
		states.put("Quebec","PQ");
		states.put("Rhode Island","RI");
		states.put("Saskatchewan","SK");
		states.put("South Carolina","SC");
		states.put("South Dakota","SD");
		states.put("Tennessee","TN");
		states.put("Texas","TX");
		states.put("Utah","UT");
		states.put("Vermont","VT");
		states.put("Virgin Islands","VI");
		states.put("Virginia","VA");
		states.put("Washington","WA");
		states.put("West Virginia","WV");
		states.put("Wisconsin","WI");
		states.put("Wyoming","WY");
		states.put("Yukon Territory","YT");
	}
}
