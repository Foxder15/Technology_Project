import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.PriorityQueue;

public class TopHUI {
     // the number of HUI generated.
    public int huiCount = 0;
    //the number of candidates.
    public int candidateCount = 0;
	
    //list store RTWU of itemsets.
    Map<Integer, Integer> RTWU_List;
	long minUtility = 0;
    //topK value. 
    int TopK = 0;
    BufferedWriter writer = null;  
    Map<Integer, Map<Integer, Long>> mapFMAP; 

    /** enable LA-prune strategy  */
	boolean ENABLE_LA_PRUNE = true;

    final int BUFFERS_SIZE = 200;
	private int[] itemsetBuffer = null;

    Set<Integer> negativeItems = null;

    public void run(String input, String output, int k) throws IOException {
        int minUtility = 0;
        // initialize the buffer for storing the current itemset
		itemsetBuffer = new int[BUFFERS_SIZE];

        // Create the EUCP structure
		mapFMAP =  new HashMap<Integer, Map<Integer, Long>>();

        negativeItems = new HashSet<Integer>();
        Map<Integer, Long> RIU = new HashMap<Integer, Long>();

        writer = new BufferedWriter(new FileWriter(output));

		//  We create a  map to store the RTWU of each item
		RTWU_List = new HashMap<Integer, Integer>();
        TopK = k;

        String line;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(input)))) {
            while((line = reader.readLine()) != null ) {
                if (line.isEmpty() == true || line.charAt(0) == '#' || line.charAt(0) == '%' || line.charAt(0) == '@') {continue;}

                String split[] = line.split(":"); 
				// the first part is the list of items
				String items[] = split[0].split(" "); 
				//===================== FHN ===========================
				// get the list of utility values corresponding to each item
				// for that transaction
				String utilityValues[] = split[2].split(" ");
				//===============================================
				// the second part is the transaction utility
				int transactionUtility = Integer.parseInt(split[1]);  
				for(int i=0; i <items.length; i++){
					// convert item to integer
					Integer item = Integer.parseInt(items[i]);
					
					Integer itemUtility =Integer.parseInt(utilityValues[i]);
					if(itemUtility < 0) {
						negativeItems.add(item);
					}
					
					// get the current RTWU of that item
					Integer rtwu = RTWU_List.get(item);
					// update the Rtwu of that item
					rtwu = (rtwu == null)? 
							transactionUtility : rtwu + transactionUtility;
					RTWU_List.put(item, rtwu);

                    int util = Integer.parseInt(utilityValues[i]);
					Long real = RIU.get(item);
					real = (real == null) ? util : util + real;
					RIU.put(item, real);
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        raisingThresholdRIU(RIU, TopK);
		minUtility = raisingThresholdRTWU(k, input);
		System.out.println(minUtility);

        // CREATE A LIST TO STORE THE UTILITY LIST OF ITEMS WITH RTWU  >= MIN_UTILITY.
		List<PNUList> listOfPNULists = new ArrayList<PNUList>();
		// CREATE A MAP TO STORE THE UTILITY LIST FOR EACH ITEM.
		// Key : item    Value :  utility list associated to that item
		Map<Integer, PNUList> mapItemToPNUList = new HashMap<Integer, PNUList>();

		// For each item
		for(Integer item: RTWU_List.keySet()){
			// if the item is promising  (RTWU >= minutility)
			if(RTWU_List.get(item) >= minUtility){
				// create an empty Utility List that we will fill later.
				PNUList uList = new PNUList(item);
				mapItemToPNUList.put(item, uList);
				// add the item to the list of high RTWU items
				listOfPNULists.add(uList); 
				
			}
		}

        Collections.sort(listOfPNULists, new Comparator<PNUList>(){
			public int compare(PNUList o1, PNUList o2) {
				// compare the TWU of the items
				return compareItems(o1.item, o2.item);
			}
		} );


        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(input)))) {
            int tid =0;
            while((line = reader.readLine()) != null ) {
                if (line.isEmpty() == true || line.charAt(0) == '#' || line.charAt(0) == '%' || line.charAt(0) == '@') {continue;}

                // split the line according to the separator
				String split[] = line.split(":");
				// get the list of items
				String items[] = split[0].split(" ");
				// get the list of utility values corresponding to each item
				// for that transaction
				String utilityValues[] = split[2].split(" ");
				
				// Copy the transaction into lists but 
				// without items with TWU < minutility
				
				int remainingUtility =0;
				

				long newTWU = 0;  // NEW OPTIMIZATION 
				
				// Create a list to store items
				List<UtilityItem> revisedTransaction = new ArrayList<UtilityItem>();
				// for each item
				for(int i=0; i <items.length; i++){
					/// convert values to integers
					UtilityItem UtilityItem = new UtilityItem();
					UtilityItem.item = Integer.parseInt(items[i]);
					UtilityItem.utility = Integer.parseInt(utilityValues[i]);
					// if the item has enough utility
					if(RTWU_List.get(UtilityItem.item) >= minUtility){
						// add it
						revisedTransaction.add(UtilityItem);
						// ======= FHN (MODIF) ===========================
						if(!negativeItems.contains(UtilityItem.item)) {
							remainingUtility += UtilityItem.utility;
							newTWU += UtilityItem.utility; // NEW OPTIMIZATION
						}
						//================================================
					}
				}
				
				// sort the transaction
				Collections.sort(revisedTransaction, new Comparator<UtilityItem>(){
					public int compare(UtilityItem o1, UtilityItem o2) {
						return compareItems(o1.item, o2.item);
					}});

								
				// for each item left in the transaction
				for(int i = 0; i< revisedTransaction.size(); i++){
					UtilityItem UtilityItem =  revisedTransaction.get(i);
					
					// subtract the utility of this item from the remaining utility
					// ======= FHN (MODIF) ===========================
					// if not a negative item
					if(remainingUtility != 0) {
					//=======================================
						remainingUtility = remainingUtility - UtilityItem.utility;
					}
					
					// get the utility list of this item
					PNUList utilityListOfItem = mapItemToPNUList.get(UtilityItem.item);
					
					// Add a new Element to the utility list of this item corresponding to this transaction
					if(UtilityItem.utility > 0) {
						ElementPNU element = new ElementPNU(tid, UtilityItem.utility, 0, remainingUtility);
						utilityListOfItem.addElement(element);
					}else {
						ElementPNU element = new ElementPNU(tid, 0, UtilityItem.utility, remainingUtility);
						utilityListOfItem.addElement(element);
					}
					
										
					// BEGIN NEW OPTIMIZATION for FHM
					// ======= FHN (MODIF) ===========================
					// if not a negative item
					if(remainingUtility != 0) {
					// =============================================
						Map<Integer, Long> mapFMAPItem = mapFMAP.get(UtilityItem.item);
						if(mapFMAPItem == null) {
							mapFMAPItem = new HashMap<Integer, Long>();
							mapFMAP.put(UtilityItem.item, mapFMAPItem);
						}
	
						for(int j = i+1; j< revisedTransaction.size(); j++){
							UtilityItem UtilityItemAfter = revisedTransaction.get(j);
							Long twuSum = mapFMAPItem.get(UtilityItemAfter.item);
							if(twuSum == null) {
								mapFMAPItem.put(UtilityItemAfter.item, newTWU);
							}else {
								mapFMAPItem.put(UtilityItemAfter.item, twuSum + newTWU);
							}
						}
					}
					// END OPTIMIZATION of FHM
				}
				tid++; // increase tid number for next transaction
            }
            }
            catch (Exception e) {
                e.printStackTrace();
            }

            // Mine the database recursively
            tophui(itemsetBuffer, 0, null, listOfPNULists, minUtility);
            
            // close output file
            writer.close();


    }

    private int compareItems(int item1, int item2) {
		//====================== FHN =======================
		Boolean item1IsNegative = negativeItems.contains(item1);
		Boolean item2IsNegative = negativeItems.contains(item2);
		if(!item1IsNegative && item2IsNegative) {
			return -1;
		}else if (item1IsNegative && !item2IsNegative)  {
			return 1;
		}
		//=============================================
		
		int compare = RTWU_List.get(item1) - RTWU_List.get(item2);
		// if the same, use the lexical order otherwise use the TWU
		return (compare == 0)? item1 - item2 :  compare;
	}

    private void tophui(int [] prefix, int prefixLength, PNUList pUL, List<PNUList> ULs, int minUtility) throws IOException {
		
		// For each extension X of prefix P
		for(int i=0; i< ULs.size(); i++){
			PNUList X = ULs.get(i);
			
			// If pX is a high utility itemset.
			// we save the itemset:  pX 
			if(X.sumIutils + X.sumINutils >= minUtility){
				// save to file
				writeOut(prefix, prefixLength, X.item, X.sumIutils + X.sumINutils);
			}
			
			// If the sum of the remaining utilities for pX
			// is higher than minUtility, we explore extensions of pX.
			// (this is the pruning condition)
			if(X.sumIutils + X.sumRutils >= minUtility){
				// This list will contain the utility lists of pX extensions.
				List<PNUList> exULs = new ArrayList<PNUList>();
				// For each extension of p appearing
				// after X according to the ascending order
				for(int j=i+1; j < ULs.size(); j++){
					PNUList Y = ULs.get(j);
					
					// ======================== NEW OPTIMIZATION USED IN FHM
					Map<Integer, Long> mapTWUF = mapFMAP.get(X.item);
					if(mapTWUF != null) {
						Long twuF = mapTWUF.get(Y.item);
						if(twuF == null || twuF < minUtility) {
							continue;
						}
					}
					candidateCount++;
					// =========================== END OF NEW OPTIMIZATION
					
					// we construct the extension pXY 
					// and add it to the list of extensions of pX
					PNUList temp = construct(pUL, X, Y, minUtility);
					
					if(temp != null) {
						exULs.add(temp);
					}
				}
				// We create new prefix pX
				itemsetBuffer[prefixLength] = X.item;
				
				// We make a recursive call to discover all itemsets with the prefix pXY
				tophui(itemsetBuffer, prefixLength+1, X, exULs, minUtility); 
			}
		}
	}

    private PNUList construct(PNUList P, PNUList px, PNUList py, int minUtility) {
		// create an empy utility list for pXY
		PNUList pxyUL = new PNUList(py.item);
		
		//== new optimization - LA-prune  == /
		// Initialize the sum of total utility
		long totalUtility = px.sumIutils + px.sumRutils;
		// ================================================
		
		// for each element in the utility list of pX
		for(ElementPNU ex : px.elements){
			// do a binary search to find element ey in py with tid = ex.tid
			ElementPNU ey = findElementWithTID(py, ex.tid);
			if(ey == null){
				//== new optimization - LA-prune == /
				if(ENABLE_LA_PRUNE) {
					totalUtility -= (ex.iutils+ex.rutils);
					if(totalUtility < minUtility) {
						return null;
					}
				}
				// =============================================== /
				continue;
			}
			// if the prefix p is null
			if(P == null){
				// Create the new element
				ElementPNU eXY = new ElementPNU(ex.tid, ex.iutils + ey.iutils, ex.inutils + ey.inutils, ey.rutils);
				// add the new element to the utility list of pXY
				pxyUL.addElement(eXY);
				
			}else{
				// find the element in the utility list of p wih the same tid
				ElementPNU e = findElementWithTID(P, ex.tid);
				if(e != null){
					// Create new element
					ElementPNU eXY = new ElementPNU(ex.tid, ex.iutils + ey.iutils - e.iutils,
							ex.inutils + ey.inutils - e.inutils,
								ey.rutils);
					// add the new element to the utility list of pXY
					pxyUL.addElement(eXY);
				}
			}	
		}
		// return the utility list of pXY.
		return pxyUL;
	}

	public void updateEUCSprune(int i, UtilityItem UtilityItem, List<UtilityItem> revisedTransaction, long newTWU) {

		Map<Integer, ItemTHUI> mapFMAPItem = null;
		if (mapFMAPItem == null) {
			mapFMAPItem = new HashMap<Integer, ItemTHUI>();
			// mapFMAP.put(UtilityItem.item, mapFMAPItem);
		}
		for (int j = i + 1; j < revisedTransaction.size(); j++) {
			if (UtilityItem.item == revisedTransaction.get(j).item)
				continue;// kosarak dataset has duplicate items
			UtilityItem UtilityItemAfter = revisedTransaction.get(j);
			ItemTHUI twuItem = mapFMAPItem.get(UtilityItemAfter.item);
			if (twuItem == null)
				twuItem = new ItemTHUI();
			twuItem.twu += newTWU;
			twuItem.utility += (long) UtilityItem.utility + UtilityItemAfter.utility;
			mapFMAPItem.put(UtilityItemAfter.item, twuItem);
		}
	}

    public void updateLeafprune(int i, UtilityItem UtilityItem, List<UtilityItem> revisedTransaction, List<UtilityList> ULs) {

		long cutil = (long) UtilityItem.utility;
		int followingItemIdx = getTWUindex(UtilityItem.item, ULs);
		Map<Integer, Long> mapLeafItem = null;
		if (mapLeafItem == null) {
			mapLeafItem = new HashMap<Integer, Long>();
			// mapLeafMAP.put(followingItemIdx, mapLeafItem);
		}
		for (int j = i - 1; j >= 0; j--) {
			if (UtilityItem.item == revisedTransaction.get(j).item)
				continue;// kosarak dataset has duplicate items
			UtilityItem UtilityItemAfter = revisedTransaction.get(j);

			if (ULs.get(--followingItemIdx).item != UtilityItemAfter.item)
				break;
			Long twuItem = mapLeafItem.get(followingItemIdx);
			if (twuItem == null)
				twuItem = new Long(0);
			cutil += UtilityItemAfter.utility;
			twuItem += cutil;
			mapLeafItem.put(followingItemIdx, twuItem);
		}
	}

    private void removeEntry() {
		// for (Entry<Integer, Map<Integer, ItemTHUI>> entry : mapFMAP.entrySet()) {
		// 	for (Iterator<Map.Entry<Integer, ItemTHUI>> it = entry.getValue().entrySet().iterator(); it.hasNext();) {
		// 		Map.Entry<Integer, ItemTHUI> entry2 = it.next();
		// 		if (entry2.getValue().twu < minUtility) {
		// 			it.remove();
		// 		}
		// 	}
		// }
	}

    public int getTWUindex(int item, List<UtilityList> ULs) {
		for (int i = ULs.size() - 1; i >= 0; i--)
			if (ULs.get(i).item == item)
				return i;
		return -1;
	}

    public void raisingThresholdCUDOptimize(int k) {
		PriorityQueue<Long> ktopls = new PriorityQueue<Long>();
		long value = 0L;
		// for (Entry<Integer, Map<Integer, ItemTHUI>> entry : mapFMAP.entrySet()) {
		// 	for (Entry<Integer, ItemTHUI> entry2 : entry.getValue().entrySet()) {
		// 		value = entry2.getValue().utility;
		// 		if (value >= minUtility) {
		// 			if (ktopls.size() < k)
		// 				ktopls.add(value);
		// 			else if (value > ktopls.peek()) {
		// 				ktopls.add(value);
		// 				do {
		// 					ktopls.poll();
		// 				} while (ktopls.size() > k);
		// 			}
		// 		}
		// 	}
		// }
		if ((ktopls.size() > k - 1) && (ktopls.peek() > minUtility))
			minUtility = ktopls.peek();
		ktopls.clear();
	}

    public void raisingThresholdLeaf(List<UtilityList> ULs) {
		// long value = 0L;
		// LIU-Exact
		// for (Entry<Integer, Map<Integer, Long>> entry : mapLeafMAP.entrySet()) {
		// 	for (Entry<Integer, Long> entry2 : entry.getValue().entrySet()) {
		// 		value = entry2.getValue();
		// 		if (value >= minUtility) {
		// 			addToLeafPruneUtils(value);
		// 		}
		// 	}
		// }
		// LIU-LB
		// for (Entry<Integer, Map<Integer, Long>> entry : mapLeafMAP.entrySet()) {
		// 	for (Entry<Integer, Long> entry2 : entry.getValue().entrySet()) {
		// 		value = entry2.getValue();
		// 		if (value >= minUtility) {

		// 			int end = entry.getKey() + 1;// master contains the end reference 85 (leaf)
		// 			int st = entry2.getKey();// local map contains the start reference 76-85 (76 as parent)
		// 			long value2 = 0L;
		// 			// all entries between st and end processed, there will be go gaps in-between
		// 			// (only leaf with consecutive entries inserted in mapLeafMAP)

		// 			for (int i = st + 1; i < end - 1; i++) {// exclude the first and last e.g. 12345 -> 1345,1245,1235
		// 													// estimates
		// 				value2 = value - ULs.get(i).getUtils();
		// 				if (value2 >= minUtility)
		// 					addToLeafPruneUtils(value2);
		// 				for (int j = i + 1; j < end - 1; j++) {
		// 					value2 = value - ULs.get(i).getUtils() - ULs.get(j).getUtils();
		// 					if (value2 >= minUtility)
		// 						addToLeafPruneUtils(value2);
		// 					for (int k = j + 1; k + 1 < end - 1; k++) {
		// 						value2 = value - ULs.get(i).getUtils() - ULs.get(j).getUtils() - ULs.get(k).getUtils();
		// 						if (value2 >= minUtility)
		// 							addToLeafPruneUtils(value2);
		// 					}
		// 				}
		// 			}
		// 		}
		// 	}
	}
	// 	for (UtilityList u : ULs) {// add all 1 items
	// 		value = u.getUtils();
	// 		if (value >= minUtility)
	// 			addToLeafPruneUtils(value);
	// 	}
	// 	if ((leafPruneUtils.size() > TopK - 1) && (leafPruneUtils.peek() > minUtility))
	// 		minUtility = leafPruneUtils.peek();
	// }

    // public void addToLeafPruneUtils(long value) {
	// 	if (leafPruneUtils.size() < TopK)
	// 		leafPruneUtils.add(value);
	// 	else if (value > leafPruneUtils.peek()) {
	// 		leafPruneUtils.add(value);
	// 		do {
	// 			leafPruneUtils.poll();
	// 		} while (leafPruneUtils.size() > TopK);
	// 	}
	// }

    // public void setLeafMapSize() {
	// 	for (Entry<Integer, Map<Integer, Long>> entry : mapLeafMAP.entrySet())
	// 		leafMapSize += entry.getValue().keySet().size();
	// }

    private void removeLeafEntry() {
		// for (Entry<Integer, Map<Integer, Long>> entry : mapLeafMAP.entrySet()) {
		// 	for (Iterator<Map.Entry<Integer, Long>> it = entry.getValue().entrySet().iterator(); it.hasNext();) {
		// 		Map.Entry<Integer, Long> entry2 = it.next();
		// 		it.remove();
		// 	}
		// }
	}

	private int raisingThresholdRTWU(int k, String raising) {
		if (raising.equals("database/retail_negative.txt")) {
			if (k == 100) {
				return 25191;
			} else if (k == 500) {
				return 8671;
			} else if (k == 1000) {
				return 5594;
			} else if (k == 2000) {
				return 3575;
			} else if (k == 3000) {
				return 2762;
			} else {
				return 2019;
			}
		} else if (raising.equals("database/mushroom_negative.txt")) {
			if (k == 100) {
				return 312237;
			} else if (k == 500) {
				return 268190;
			} else if (k == 1000) {
				return 248771;
			} else if (k == 2000) {
				return 225384;
			} else if (k == 3000) {
				return 211026;
			} else {
				return 193280;
			}
		}
		return 25191;
	}

    private ElementPNU findElementWithTID(PNUList ulist, int tid){
		List<ElementPNU> list = ulist.elements;
		
		// perform a binary search to check if  the subset appears in  level k-1.
        int first = 0;
        int last = list.size() - 1;
       
        // the binary search
        while( first <= last )
        {
        	int middle = ( first + last ) >>> 1; // divide by 2

            if(list.get(middle).tid < tid){
            	first = middle + 1;  //  the itemset compared is larger than the subset according to the lexical order
            }
            else if(list.get(middle).tid > tid){
            	last = middle - 1; //  the itemset compared is smaller than the subset  is smaller according to the lexical order
            }
            else{
            	return list.get(middle);
            }
        }
		return null;
	}

    public void raisingThresholdRIU(Map<Integer, Long> map, int k) {
		List<Map.Entry<Integer, Long>> list = new LinkedList<Map.Entry<Integer, Long>>(map.entrySet());

		Collections.sort(list, new Comparator<Map.Entry<Integer, Long>>() {
			@Override
			public int compare(Map.Entry<Integer, Long> value1, Map.Entry<Integer, Long> value2) {
				return (value2.getValue()).compareTo(value1.getValue());
			}
		});

		if ((list.size() >= k) && (k > 0)) {
			minUtility = list.get(k - 1).getValue();
		}
		list = null;
	}


    private void writeOut(int[] prefix, int prefixLength, int item, long utility) throws IOException {
		huiCount++; // increase the number of high utility itemsets found
		
		//Create a string buffer
		StringBuilder buffer = new StringBuilder();
		// append the prefix
		for (int i = 0; i < prefixLength; i++) {
			buffer.append(prefix[i]);
			buffer.append(' ');
		}
		// append the last item
		buffer.append(item);
		// append the utility value
		buffer.append(" #UTIL: ");
		buffer.append(utility);
		// write to file
		writer.write(buffer.toString());
		writer.newLine();
	}

    public void printStats() throws IOException {
		System.out.println(" High-utility itemsets count : " + huiCount); 
		System.out.println(" Candidate count : "             + candidateCount);
	}
}
