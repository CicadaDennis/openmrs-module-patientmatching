package org.regenstrief.linkage.analysis;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;

import org.regenstrief.linkage.MatchResult;
import org.regenstrief.linkage.MatchVector;
import org.regenstrief.linkage.Record;
import org.regenstrief.linkage.util.MatchingConfig;
import org.regenstrief.linkage.util.MatchingConfigRow;
import org.regenstrief.linkage.util.ScorePair;

/**
 * Class was originally extending Analyzer, but EM analyzes
 * pairs of records, not a record data source, so it
 * no longer is a subclass.  It also modifies the MatchingConfig
 * object given in the analyzeRecordPairs method with new
 * agreement and non-agreement values for included columns as
 * a side effect
 */

public class EMAnalyzer { //extends Analyzer {
	final static double INIT_MEST = 0.9;
	final static double INIT_UEST = 0.1;
	final static int INIT_COMP = 0;
	
	// use approximate values close to zero and one
	// due to the math used in the record linking
	final static double EM_ONE = 0.99999;
	final static double EM_ZERO = 0.00001;
	
	final static int ITERATIONS = 3;
	
	//private Hashtable<MatchVector,Integer> vector_count;
	private List<MatchVector> vector_list;
	
	/**
	 * Constructor needs a name to use when created the temporary
	 * .vct file
	 * 
	 */
	public EMAnalyzer(){
		//vector_count = new Hashtable<MatchVector,Integer>();
		vector_list = new ArrayList<MatchVector>();
		
	}
	
	/**
	 * Analyzes the record pairs according to the given MatchingConfig object and returns
	 * a hash map of the column names to new values.  This method uses the default
	 * number of iterations.
	 * 
	 * Like the version that also has an iterations argument, the MatchingConfig object
	 * passed will be modified
	 * 
	 * @param fp	provides the stream of Record pairs to be analyzed
	 * @param mc	stores the information on how to compare the Records.  This will have
	 * 				its m and u values modified for columns included in the matching
	 * @throws IOException
	 */
	public void analyzeRecordPairs(org.regenstrief.linkage.io.FormPairs fp, MatchingConfig mc) throws IOException{
		analyzeRecordPairs(fp, mc, ITERATIONS);
	}
	
	/**
	 * Analyzes the record pairs according to the given MatchingConfig object and returns
	 * a hash map of the column names to new values
	 * 
	 * @param fp	provides the stream of Record pairs to be analyzed
	 * @param mc	stores the information on how to compare the Records.  This will have
	 * 				its m and u values modified for columns included in the matching
	 * @param iterations	the number of iterations to perform when calculating optimized
	 * 				m and u values
	 * @throws IOException
	 */
	
	public void analyzeRecordPairs(org.regenstrief.linkage.io.FormPairs fp, MatchingConfig mc, int iterations) throws IOException{
		ScorePair sp = new ScorePair(mc);
		Record[] pair;
		while((pair = fp.getNextRecordPair()) != null){
			Record r1 = pair[0];
			Record r2 = pair[1];
			MatchResult mr = sp.scorePair(r1, r2);
			MatchVector mr_vect = mr.getMatchVector();
			vector_list.add(mr_vect);
			/*
			Integer mv_count = vector_count.get(mr_vect);
			if(mv_count == null){
				vector_count.put(mr_vect, new Integer(1));
			} else {
				vector_count.put(mr_vect, new Integer(mv_count.intValue() + 1));
			}
			*/
		}
		finishAnalysis(new VectorTable(mc), mc.getIncludedColumnsNames(), mc, iterations);
		
	}
	
	private void finishAnalysis(VectorTable vt, String[] demographics, MatchingConfig mc, int iterations)  throws IOException{
		
		// values to store index by demographic name
		Hashtable<String,Double> msum = new Hashtable<String,Double>();
		Hashtable<String,Double> usum = new Hashtable<String,Double>();
		Hashtable<String,Double> mest = new Hashtable<String,Double>();
		Hashtable<String,Double> uest = new Hashtable<String,Double>();
		
		// initialize default values
		for(int i = 0; i < demographics.length; i++){
			mest.put(demographics[i], new Double(INIT_MEST));
			uest.put(demographics[i], new Double(INIT_UEST));
			msum.put(demographics[i], new Double(0));
			usum.put(demographics[i], new Double(0));
		}
		
		
		double gMsum, gUsum, gMtemp, gUtemp;
		double termM, termU;
		double p = 0.01;
		//gMsum = 0;
		//gUsum = 0;
		//gMtemp = 0;
		//gUtemp = 0;
		
		for(int i = 0; i < iterations; i++){
			gMsum = 0;
			gUsum = 0;
			gMtemp = 0;
			gUtemp = 0;
			int vct_count = 0;
			
			// zero out msum and usum arrays
			for(int k = 0; k < demographics.length; k++){
				msum.put(demographics[k], new Double(0));
				usum.put(demographics[k], new Double(0));
			}
			
			//Iterator<MatchVector> mv_it = vector_count.keySet().iterator();
			Iterator<MatchVector> mv_it = vector_list.iterator();
			while(mv_it.hasNext()){
				MatchVector mv = mv_it.next();
				//int mv_count = vector_count.get(mv).intValue();
				//vct_count += mv_count;
				vct_count++;
				//for(int j = 0; j < mv_count; j++){
					// begin the EM calculation loop for the current record pair
					termM = 1;
					termU = 1;
					gMtemp = 0;
					gUtemp = 0;
					
					for(int k = 0; k < demographics.length; k++){
						String demographic = demographics[k];
						boolean matched = mv.matchedOn(demographic);
						int comp = 0;
						if(matched){
							comp = 1;
						}
						termM = termM * Math.pow(mest.get(demographic), comp) * Math.pow(1 - mest.get(demographic), 1 - comp);
						termU = termU * Math.pow(uest.get(demographic), comp) * Math.pow(1 - uest.get(demographic), 1 - comp);
						//System.out.println(termM + "\t" + termU);
					}
					//System.out.println();
					gMtemp = (p * termM) / ((p * termM) + ((1 - p) * termU));
					gUtemp = ((1 - p) * termU) / (((1 - p) * termU) + (p * termM)); 
					//System.out.println("gMtemp: " + gMtemp);
					
					// update the running sum for msum and usum
					for(int k = 0; k < demographics.length; k++){
						String demographic = demographics[k];
						boolean matched = mv.matchedOn(demographic);
						if(matched){
							double m = msum.get(demographic);
							double u = usum.get(demographic);
							msum.put(demographic, new Double(m + gMtemp));
							usum.put(demographic, new Double(u + gUtemp));
						}
					}
					
					// update the running sum for gMsum and gUsum
					
					
					
					gMsum = gMsum + gMtemp;
					gUsum = gUsum + gUtemp;
					
				//}
				
			}
			
			// update p_est
			p = gMsum / vct_count;
			//System.out.println("Iteration " + (i + 1));
			//System.out.println("P: " + p);
			
			// update the mest and uest values after each iteration
			for(int j = 0; j < demographics.length; j++){
				String demographic = demographics[j];
				double mest_val = msum.get(demographic) / gMsum;
				double uest_val = usum.get(demographic) / gUsum;
				mest.put(demographic, mest_val);
				uest.put(demographic, uest_val);
			}
			
			/*
			for(int j = 0; j < demographics.length; j++){
				String demographic = demographics[j];
				System.out.println(demographic + ":   mest: " + mest.get(demographic) + "   uest: " + uest.get(demographic));
			}*/
			
		}
		
		for(int i = 0; i < demographics.length; i++){
			String demographic = demographics[i];
			MatchingConfigRow mcr = mc.getMatchingConfigRows().get(mc.getRowIndexforName(demographic));
			double mest_val = mest.get(demographic);
			double uest_val = uest.get(demographic);
			
			if(mest_val > EM_ONE){
				mest_val = EM_ONE;
			}
			if(mest_val < EM_ZERO){
				mest_val = EM_ZERO;
			}
			if(uest_val > EM_ONE){
				uest_val = EM_ONE;
			}
			if(uest_val < EM_ZERO){
				uest_val = EM_ZERO;
			}
			
			mcr.setAgreement(mest_val);
			mcr.setNonAgreement(uest_val);
		}
		
	}
	
}
