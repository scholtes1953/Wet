package com.mjs.wet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.mjs.wet.Wet.Pair;

public class WetTest {

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
	}

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testCSAListParsing() {
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
		
		HashMap<String, List<Pair<String,String>>> map = new HashMap<String, List<Pair<String,String>>>();
		
		
		String header = "CBSA Code,Metro Division Code,CSA Code,CBSA Title,Metropolitan/Micropolitan Statistical Area,Metropolitan Division Title,CSA Title,County/County Equivalent,State Name,FIPS State Code,FIPS County Code,Central/Outlying County";
		String line =	"12420,,,\"Austin-Round Rock, TX\",Metropolitan Statistical Area,,,Bastrop County,Texas,48,021,Outlying";

		String[] fields= header.split(",");

		// parse header line for field names
		ArrayList<String> fieldList = new ArrayList<String>(Arrays.asList(fields));

		for(int pi = 0; pi < properties.length; pi++) {
				indexes[pi] = fieldList.indexOf(properties[pi]);							
		}

		// get MSA, county, state
		fields = line.split(Wet.embeddedCommaRegex(),-1);
		String MSA = fields[indexes[MSA_PROPERTY]].trim().replaceAll("^\"|\"$", "");
		boolean isMetro = (fields[indexes[METRO_MICRO_PROPERTY]].equals(MSA_STRING));
		String county = fields[indexes[COUNTY_PROPERTY]];
		String state = fields[indexes[STATE_PROPERTY]];
		assertTrue(isMetro);
		assertEquals(MSA, "Austin-Round Rock, TX");
		assertEquals(county, "Bastrop County");
		assertEquals(state, "Texas");		
	}

	@Test
	public void testRainParsing() {
		String precipHeader = "Wban,YearMonthDay,Hour,Precipitation,PrecipitationFlag";
		ArrayList<String> fieldList = new ArrayList<String>(Arrays.asList(precipHeader.split(",")));
		int WBAN_INDEX = fieldList.indexOf("Wban");
		int HOUR_INDEX = fieldList.indexOf("Hour");
		int PRECIP_INDEX = fieldList.indexOf("Precipitation");
		String testLines[] = {
		"00103,20150523,07,0.01,",
		"00103,20150523,08,0.07,",
		"00103,20150523,11,  T,",
		"00103,20150523,12, ,"
		};
		for(int i=0; i<4; i++) {
			String[] fields = testLines[i].split(",");
			String wban = fields[WBAN_INDEX];
			Double rain = new Double(0.0);
			try {
				int hour = Integer.parseUnsignedInt(fields[HOUR_INDEX]); // 1...24 are the hours ending at 1AM...midnight
				String sRain = fields[PRECIP_INDEX];	
				if (hour > 7) {
					rain = Double.parseDouble(sRain);				
				}		
			}catch(NumberFormatException ex) {
				
			}
			assertEquals(wban,"00103");
			switch(i) {
			case 0: assertEquals(0.0d, rain.doubleValue(), 0.001d); break;
			case 1: assertEquals(0.07d, rain.doubleValue(), 0.001d); break;
			case 2: assertEquals(0.0d, rain.doubleValue(), 0.001d); break;
			default: assertEquals(0.0d, rain.doubleValue(), 0.001d); break;
			}
		}


	}
	
	@Test
	public void testNormalizeCounty() {
		assertEquals("TRAVIS", Wet.normalizeCounty("Travis County"));
		assertEquals("TRAVIS", Wet.normalizeCounty("\"TRAVIS\""));
	}
	
	@Test
	public void testNormalizeState() {
		assertEquals("TX", Wet.normalizeState("Texas"));
		assertEquals("TX", Wet.normalizeState("\"TX\""));
	}
}
