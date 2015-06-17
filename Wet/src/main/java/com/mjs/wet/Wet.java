package com.mjs.wet;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.NumberFormat;
import java.text.ParseException;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * @author mscholtes
 *
 */
public class Wet {
	static Map<String, String> states = new HashMap<String,String>();
	
	private String wbanFile;
	private String CSAFile;
	private String popFile;
	private String precipFile;
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		String wbanFile = "wbanmasterlist.psv";
		String CSAFile = "2013Feb_CSAList1.csv";
		String populationFile = "CBSA-EST2012-01.csv";
		String precipFile = "201505precip.txt";
		Wet wet = new Wet(wbanFile, CSAFile, populationFile, precipFile);
		
		Map<String, Double> msaPeopleInches = wet.getPersonWetness();
		//TODO: graph it; Swing, JavaFX, JFreeChart, D3.js
		BufferedWriter writer = null;
		try {
			writer = new BufferedWriter(new FileWriter("peopleinches.csv"));
			writer.write("MSA,PeopleInches");
			for(Entry<String,Double> entry: msaPeopleInches.entrySet()) {
				writer.write(entry.getKey()+","+entry.getValue()+"\n");
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
	 * Creates a histogram of the wettest Metropolitan Statistical Areas, defined as
	 * (rainfall 7AM to midnight) x (population), e.g. something like "people-inches".
	 * 
	 * The data to create this histogram is distributed in four distinct tables:
	 * <ul>
	 * <li> a WBAN file that relates WBAN weather stations to counties</li>
	 * <li> a CSA file that relates MSAs to counties </li>
	 * <li> a population file that gives MSA populations</li>
	 * <li> a precipitation file that gives hourly precip. by WBAN</li>
	 * </ul>
	 * The entity relationships are:
	 * <ul>
	 * <li> a WBAN (Weather Bureau Army Navy) station resides in 1 county<li>
	 * <li> an MSA (Metropolitan Statistical Area) is comprised of 1 or more counties</li>
	 * </ul>
	 * 
	 * 
	 * Complications to address include: (1) population must be extrapolated to the epoch of 
	 * the rain data; we have population data for 2010, 2011, and 2012, and need to extrapolate
	 * to 2015. Linear or cubic polynomial extrapolation could be used. (2) The MSA delineations
	 * are from 2013. It is possible some WBANs might have changed MSA membership between then
	 * and 2015. But we wouldn't know about it unless a county name was new or modified. (3) The 
	 * precip. data also refers to Micropolitan Statistical Areas, which all have fewer people
	 * than the Metropolitan Statistical Areas. We are choosing to ignore that data.
	 * 
	 * @param wbanFile name of file with wban-to-county relationship
	 * @param CSAFile name of file with MSA-to-county relationship
	 * @param popFile name of file with MSA-to-population relationship
	 * @param precipFile name of file with WBAN hourly precipitation data
	 */
	public Wet(String wbanFile, String CSAFile, String popFile, String precipFile) {
		this.wbanFile = wbanFile;
		this.CSAFile = CSAFile;
		this.popFile = popFile;
		this.precipFile = precipFile;
	}
	
	/**
	 * Returns a map of MSA to the total daytime rainfall in May 2015 multiplied by 
	 * the MSA population.
	 * @return
	 */
	public Map<String, Double> getPersonWetness(){
		BufferedReader wbanReader = getReader(wbanFile); // wban -> county relationship
		BufferedReader csaReader = getReader(CSAFile); // MSA -> county relationships
		BufferedReader popReader = getReader(popFile); // MSA -> population relationship
		BufferedReader precipReader = getReader(precipFile); // WBAN -> precipitation data
		
		// map of County|State to MSA name
		Map<String, String> msaCounties = loadMsaCountyMap(csaReader);

		// map of WBAN to <County, State>
		Map<String, Pair<String,String>> wbanCounties = loadWBANCountyMap(wbanReader);
		
		// total daytime rain per MSA
		Map<String, Double> msaRain = mergeRainWithCounties(msaCounties, precipReader, wbanCounties);
		
		return multiplyRainTimesPeople(msaRain, popReader);
	}

	private Map<String, Pair<String, String>> loadWBANCountyMap(
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
			int iCompare;
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

	private Map<String, Double> multiplyRainTimesPeople(
			Map<String, Double> msaRain, BufferedReader popReader) 
	{
		HashMap<String, Double> msaPeopleInches = new HashMap<String, Double>();

		LocalDate extrapolationDate = LocalDate.of(2015, Month.MAY, 15);
		Map<String, Integer> msaPeople = getExtrapolatedPopulations(popReader, extrapolationDate);

		for(Map.Entry<String, Double> entry : msaRain.entrySet()) {
			String msa = entry.getKey();
			Double rain = entry.getValue();
			int people = msaPeople.get(msa);
			msaPeopleInches.put(msa, rain * people);
		}
		// sort descending by wetness
		Set<Entry<String,Double>> set = msaPeopleInches.entrySet();
		List<Entry<String,Double>> list = new LinkedList<Entry<String,Double>>(set);
		Collections.sort(list, new Comparator<Entry<String,Double>>(){
			public int compare(Map.Entry<String, Double> o1, Map.Entry<String, Double> o2) {
				return (o2.getValue()).compareTo(o1.getValue());
			}
		});
		Map<String,Double> result = new LinkedHashMap<String, Double>();
		for (Entry<String,Double> entry: list) {
			result.put(entry.getKey(), entry.getValue());
		}
		return result;
	}

	/*
	 * extrapolate populations for MSAs to a given date.
	 * We have hard-coded dependency on the specific data file. 
	 */
	private Map<String, Integer> getExtrapolatedPopulations(
			BufferedReader popReader, LocalDate extrapolationDate) 
	{

		/*
		 * Population file header and sample data: (numbers as of July 1 of given year)
		 * CBSA Code,Metro Division Code,Metropolitan statistical areas,Census,Estimates base,2010,2011,2012
		 * 10180,,"Abilene, TX","165,252","165,252","165,578","166,481","166,963"
		 */
		Map<String, Integer> msaPop = new HashMap<String, Integer>();
		ArrayList<String> fieldList;
		try {
			String regex = embeddedCommaRegex();
			fieldList = getHeaderFields(popReader, ",");
			int MSA_INDEX = fieldList.indexOf("Metropolitan statistical areas");
			int INDEX_2010 = fieldList.indexOf("2010");
			int INDEX_2011 = fieldList.indexOf("2011");
			int INDEX_2012 = fieldList.indexOf("2012");
			String line;
			while ((line = popReader.readLine()) != null) {
				String[] fields = line.split(regex, -1);
				String msa = this.normalizeField(fields[MSA_INDEX]);
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
	 * Extrapolates population from three different times to a given future time
	 */
	private int extrapolate(Pair<Number, LocalDate> val2010,
			Pair<Number, LocalDate> val2011, Pair<Number, LocalDate> val2012,
			LocalDate extrapolationDate) {
		// TODO: experiment with cubic fit (parabola fit to 3 points),
		// least-square fit, or spline.
		// In the interest of time, here we simply use the latest value
		return val2012.element0.intValue();
	}

	/*
	 * creates a map of County|State to MSA
	 */
	private HashMap<String, String> loadMsaCountyMap(BufferedReader reader){
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
				fields = line.split(embeddedCommaRegex(), -1);
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
					String countyStateKey = createCountyStateKey(county, state);
		
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
	
	// Travis + TX -> Travis|TX
	private String createCountyStateKey(String county, String state) {
		return county + "|" + state;
	}

	/**
	 * 
	 * @param counties HashMap of MSA to List of counties
	 * @param precipReader BufferedReader for WBAN precip. data
	 * @param wbanReader BufferedReader for WBAN counties table
	 * @return HashMap of MSA name to inches of rain
	 */
	HashMap<String, Double> mergeRainWithCounties(
			Map<String, String> counties, 
			BufferedReader precipReader,
			Map<String, Pair<String,String>> wbanCounties)
	{
		// the precip list is huge, so instead of loading all into memory,
		// stream it. It is sorted by WBAN, and so is the WBAN master list,
		// so one can stream them both in a synchronized manner.
		//
		// 	until reach end of precip or wban
		//  	get and sum precip for 7am - midnight until wban changes
		// 		get wban until matches that of precip sum
		//  	get MSA for county,state
		//  	save rain for msa
		HashMap<String, Double> msaRain = new HashMap<String, Double>();
		
		boolean reachedEOF = false;
		/*
		 * Precip file header and sample line:
		 * Wban,YearMonthDay,Hour,Precipitation,PrecipitationFlag
		 * 00103,20150523,03,0.01,
		 * 
		 * note: state and county formats are not the same in msaTable: "ND","MOUNTRAIL"  vs "North Dakota","Mountrail County"
		 */
		try {
			ArrayList<String> fieldList = getHeaderFields(precipReader, ",");
			int WBAN_INDEX = fieldList.indexOf("Wban");
			int HOUR_INDEX = fieldList.indexOf("Hour");
			int PRECIP_INDEX = fieldList.indexOf("Precipitation");
			
			Pair<String, Double> wbanPrecip = getDaytimePrecip(precipReader, WBAN_INDEX, HOUR_INDEX, PRECIP_INDEX); //"00102", 0.01
			double totalRain = 0.0d;
			String oldWban;
			while(!reachedEOF && wbanPrecip != null) {
				oldWban = wbanPrecip.element0;
				totalRain = wbanPrecip.element1.doubleValue();
				do{
					wbanPrecip = getDaytimePrecip(precipReader, WBAN_INDEX, HOUR_INDEX, PRECIP_INDEX);
					if (wbanPrecip == null) 
						reachedEOF = true;
					else {
						totalRain += wbanPrecip.element1.doubleValue();
					}
				}while(wbanPrecip != null && wbanPrecip.element0.equals(oldWban));
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
	
	// Get the name of the MSA that a county belongs to, e.g.:
	// Travis|TX --> Austin-Round Rock, TX
	private String getMSAForCounty(Pair<String, String> countyStatePair,
			Map<String, String> counties) {
		String countyState = createCountyStateKey(countyStatePair.element0, countyStatePair.element1);
		return counties.get(countyState);
	}

	/*
	 * Look up the WBAN in the wban master index
	 * If found, return Pair<County, State>, where County is normalized to the format "BROWARD", and 
	 * State is normalized to the format "MT".
	 */
	private Pair<String, String> getNormalizedCountyState(
			Map<String,Pair<String,String>> wbanCounties, String oldWban)
	{
		return wbanCounties.get(oldWban);
	}

	// Montana --> MT
	// "MT" --> MT
	static public String normalizeState(String string) {		
		String trimmed = string.replaceAll("^\"|\"$", "").trim();
		if (trimmed.length() > 2)
			trimmed = states.get(trimmed);
		return trimmed;
	}

	// "FOO" -> FOO
	// Foo County --> FOO
	static public String normalizeCounty(String string) {
		String trimmed = string.replaceAll("^\"|\"$", "").trim().toUpperCase();
		int countyIndex = trimmed.indexOf("COUNTY");
		if (countyIndex >= 0) {
			trimmed = trimmed.substring(0, countyIndex).trim();
		}
		return trimmed;
	}

	/*
	 * Called for the first read of a BufferedReader to extract the header fields
	 */
	private ArrayList<String> getHeaderFields(BufferedReader reader, String regex) throws IOException {
		String header = reader.readLine();
		if (header == null) {
			throw new IOException("unable to read header from file");		
		}else {
			String fields[] = header.split(regex);
			return new ArrayList<String>(Arrays.asList(fields));
		}
	}
	/*
	 * Reads a data line from the wban precip file.
	 * If EOF, returns null.
	 * Otherwise,
	 * Returns a Pair<WBAN name, rainfall amount>, where the rainfall amount
	 * is only nonzero if the line was for the hours of 7AM to midnight.
	 */
	private Pair<String, Double> getDaytimePrecip(BufferedReader reader, int wbanIndex, int hourIndex, int precipIndex) throws IOException {
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
	
	// "Foo" --> Foo
	private String normalizeField(String string) {
		// remove surrounding double quotes
		return string.replaceAll("^\"|\"$", "");
	}

	/*
	 * Given a text file name on the classpath, returns a BufferedReader for it
	 */
	private BufferedReader getReader(String fileName) {
		InputStream theStream = this.getClass().getResourceAsStream("/"+fileName);
		BufferedReader br = null;
		if (theStream != null) {
			br = new BufferedReader(new InputStreamReader(theStream));
		}
		return br;
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
