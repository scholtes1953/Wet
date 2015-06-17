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
		
		Utilities.exportMap(msaPeopleInches);	

		//TODO: graph it; Swing, JavaFX, JFreeChart, D3.js
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
		Map<String, String> msaCounties = Data.loadMsaCountyMap(csaReader);

		// map of WBAN to <County, State>
		Map<String, Pair<String,String>> wbanCounties = Data.loadWBANCountyMap(wbanReader);
		
		// total daytime rain per MSA
		Map<String, Double> msaRain = Data.mergeRainWithCounties(msaCounties, precipReader, wbanCounties);
		
		return multiplyRainTimesPeople(msaRain, popReader);
	}


	private Map<String, Double> multiplyRainTimesPeople(
			Map<String, Double> msaRain, BufferedReader popReader) 
	{
		HashMap<String, Double> msaPeopleInches = new HashMap<String, Double>();

		LocalDate extrapolationDate = LocalDate.of(2015, Month.MAY, 15);
		Map<String, Integer> msaPeople = Data.getExtrapolatedPopulations(popReader, extrapolationDate);

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
