import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.PriorityQueue;

public class THUI {
    // the number of HUI generated.
    public int huiCount = 0;
    //the number of candidates.
    public int candidateCount = 0;

    //list store TWU of itemsets.
    Map<Integer, Integer> TWU_List;
    // initalize the minimum threshold.
    long minUtility = 0;
    //topK value. 
    int TopK = 0;

    BufferedWriter writer = null;
    
    // store the top K patterns. 
    PriorityQueue<PatternTHUI> kPatterns = new PriorityQueue<PatternTHUI>();
    PriorityQueue<Long> leafPruneUtils = null;

    final int BUFFERS_SIZE = 200;
	private int[] itemsetBuffer = null;

    Map<Integer, Map<Integer, ItemTHUI>> mapFMAP = null;

    Map<Integer, Map<Integer, Long>> mapLeafMAP = null;
	long riuRaiseValue = 0, leafRaiseValue = 0;
	int leafMapSize = 0;

    boolean EUCS_PRUNE = false;
	boolean LEAF_PRUNE = true;

    String inputFile;

    public void run(String input, String output, boolean eucsPrune, int k) throws IOException {
        TopK = k;
        itemsetBuffer = new int[BUFFERS_SIZE];
		this.EUCS_PRUNE = eucsPrune;
        Map<Integer, Long> RIU = new HashMap<Integer, Long>();
		inputFile = input;

        if (EUCS_PRUNE)
			mapFMAP = new HashMap<Integer, Map<Integer, ItemTHUI>>();

		if (LEAF_PRUNE) {
			mapLeafMAP = new HashMap<Integer, Map<Integer, Long>>();
			leafPruneUtils = new PriorityQueue<Long>();
		}

        writer = new BufferedWriter(new FileWriter(output));
        TWU_List = new HashMap<>();

        
        String line;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(input)))) {
            while((line = reader.readLine()) != null ) {
                if (line.isEmpty() == true || line.charAt(0) == '#' || line.charAt(0) == '%' || line.charAt(0) == '@') {continue;}

                String[] parts = line.split(":");
                String[] items = parts[0].split(" ");
                String[] utilityValues = parts[2].split(" ");
				int transactionUtility = Integer.parseInt(parts[1]);

                for (int i = 0; i < items.length; i++) {
                    Integer item = Integer.parseInt(items[i]);
                    Integer twu = TWU_List.get(item);
                    twu = (twu == null) ? transactionUtility : twu + transactionUtility;
                    TWU_List.put(item, twu);

                    int util = Integer.parseInt(utilityValues[i]);
					Long real = RIU.get(item);
					real = (real == null) ? util : util + real;
					RIU.put(item, real);
                }

            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        //raising threshold strategy method 1. 
        raisingThresholdRIU(RIU, TopK);

        riuRaiseValue = minUtility;

        List<UtilityList> listOfUtilityLists = new ArrayList<UtilityList>();
		Map<Integer, UtilityList> mapItemToUtilityList = new HashMap<Integer, UtilityList>();


        for (Integer item : TWU_List.keySet()) {
			if (TWU_List.get(item) >= minUtility) {
				UtilityList uList = new UtilityList(item);
				mapItemToUtilityList.put(item, uList);
				listOfUtilityLists.add(uList);
			}
		}

        Collections.sort(listOfUtilityLists, new UtilComparator());

        int remainingUtility = 0;
		long newTWU = 0;
		String key = null;
		Integer kTid;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(input)))) {
            int tid = 0;
            while((line = reader.readLine()) != null ) {
                if (line.isEmpty() == true || line.charAt(0) == '#' || line.charAt(0) == '%' || line.charAt(0) == '@') {continue;}

                String split[] = line.split(":");
				String items[] = split[0].split(" ");
				String utilityValues[] = split[2].split(" ");
				remainingUtility = 0;
				newTWU = 0; // NEW OPTIMIZATION

				List<UtilityItem> revisedTransaction = new ArrayList<UtilityItem>();
				for (int i = 0; i < items.length; i++) {
					UtilityItem UtilityItem = new UtilityItem(Integer.parseInt(items[i]), Integer.parseInt(utilityValues[i]));
					if (TWU_List.get(UtilityItem.item) >= minUtility) {
						revisedTransaction.add(UtilityItem);
						remainingUtility += UtilityItem.utility;
						newTWU += UtilityItem.utility; // NEW OPTIMIZATION
					}
				}
				if (revisedTransaction.size() == 0)
					continue;
				Collections.sort(revisedTransaction, new PairComparator());

				remainingUtility = 0;
				for (int i = revisedTransaction.size() - 1; i >= 0; i--) {
					UtilityItem UtilityItem = revisedTransaction.get(i);
					UtilityList utilityListOfItem = mapItemToUtilityList.get(UtilityItem.item);
					Element element = new Element(tid, UtilityItem.utility, remainingUtility);
					utilityListOfItem.addElement(element);

					if (EUCS_PRUNE)
						updateEUCSprune(i, UtilityItem, revisedTransaction, newTWU);
					if (LEAF_PRUNE)
						updateLeafprune(i, UtilityItem, revisedTransaction, listOfUtilityLists);
					remainingUtility += UtilityItem.utility;
				}
				tid++; // increase tid number for next transaction

            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (EUCS_PRUNE) {
			raisingThresholdCUDOptimize(TopK);
			removeEntry();
		}
		RIU.clear();

        if (LEAF_PRUNE) {
			raisingThresholdLeaf(listOfUtilityLists);
			setLeafMapSize();
			removeLeafEntry();
			leafPruneUtils = null;
		}
		leafRaiseValue = minUtility;
		mapItemToUtilityList = null;

        thui(itemsetBuffer, 0, null, listOfUtilityLists);
		writeResultTofile();
		writer.close();
		kPatterns.clear();

    }


    class PairComparator implements Comparator<UtilityItem> {
		@Override
		public int compare(UtilityItem o1, UtilityItem o2) {
			return compareItems(o1.item, o2.item);
		}
	}

	class UtilComparator implements Comparator<UtilityList> {
		@Override
		public int compare(UtilityList o1, UtilityList o2) {
			return compareItems(o1.item, o2.item);
		}
	}

    private int compareItems(int item1, int item2) {
		int compare = (int) (TWU_List.get(item1) - TWU_List.get(item2));
		return (compare == 0) ? item1 - item2 : compare;
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

    public void updateEUCSprune(int i, UtilityItem UtilityItem, List<UtilityItem> revisedTransaction, long newTWU) {

		Map<Integer, ItemTHUI> mapFMAPItem = mapFMAP.get(UtilityItem.item);
		if (mapFMAPItem == null) {
			mapFMAPItem = new HashMap<Integer, ItemTHUI>();
			mapFMAP.put(UtilityItem.item, mapFMAPItem);
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
		Map<Integer, Long> mapLeafItem = mapLeafMAP.get(followingItemIdx);
		if (mapLeafItem == null) {
			mapLeafItem = new HashMap<Integer, Long>();
			mapLeafMAP.put(followingItemIdx, mapLeafItem);
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
		for (Entry<Integer, Map<Integer, ItemTHUI>> entry : mapFMAP.entrySet()) {
			for (Iterator<Map.Entry<Integer, ItemTHUI>> it = entry.getValue().entrySet().iterator(); it.hasNext();) {
				Map.Entry<Integer, ItemTHUI> entry2 = it.next();
				if (entry2.getValue().twu < minUtility) {
					it.remove();
				}
			}
		}
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
		for (Entry<Integer, Map<Integer, ItemTHUI>> entry : mapFMAP.entrySet()) {
			for (Entry<Integer, ItemTHUI> entry2 : entry.getValue().entrySet()) {
				value = entry2.getValue().utility;
				if (value >= minUtility) {
					if (ktopls.size() < k)
						ktopls.add(value);
					else if (value > ktopls.peek()) {
						ktopls.add(value);
						do {
							ktopls.poll();
						} while (ktopls.size() > k);
					}
				}
			}
		}
		if ((ktopls.size() > k - 1) && (ktopls.peek() > minUtility))
			minUtility = ktopls.peek();
		ktopls.clear();
	}

    public void raisingThresholdLeaf(List<UtilityList> ULs) {
		long value = 0L;
		// LIU-Exact
		for (Entry<Integer, Map<Integer, Long>> entry : mapLeafMAP.entrySet()) {
			for (Entry<Integer, Long> entry2 : entry.getValue().entrySet()) {
				value = entry2.getValue();
				if (value >= minUtility) {
					addToLeafPruneUtils(value);
				}
			}
		}
		// LIU-LB
		for (Entry<Integer, Map<Integer, Long>> entry : mapLeafMAP.entrySet()) {
			for (Entry<Integer, Long> entry2 : entry.getValue().entrySet()) {
				value = entry2.getValue();
				if (value >= minUtility) {

					int end = entry.getKey() + 1;// master contains the end reference 85 (leaf)
					int st = entry2.getKey();// local map contains the start reference 76-85 (76 as parent)
					long value2 = 0L;
					// all entries between st and end processed, there will be go gaps in-between
					// (only leaf with consecutive entries inserted in mapLeafMAP)

					for (int i = st + 1; i < end - 1; i++) {// exclude the first and last e.g. 12345 -> 1345,1245,1235
															// estimates
						value2 = value - ULs.get(i).getUtils();
						if (value2 >= minUtility)
							addToLeafPruneUtils(value2);
						for (int j = i + 1; j < end - 1; j++) {
							value2 = value - ULs.get(i).getUtils() - ULs.get(j).getUtils();
							if (value2 >= minUtility)
								addToLeafPruneUtils(value2);
							for (int k = j + 1; k + 1 < end - 1; k++) {
								value2 = value - ULs.get(i).getUtils() - ULs.get(j).getUtils() - ULs.get(k).getUtils();
								if (value2 >= minUtility)
									addToLeafPruneUtils(value2);
							}
						}
					}
				}
			}
		}
		for (UtilityList u : ULs) {// add all 1 items
			value = u.getUtils();
			if (value >= minUtility)
				addToLeafPruneUtils(value);
		}
		if ((leafPruneUtils.size() > TopK - 1) && (leafPruneUtils.peek() > minUtility))
			minUtility = leafPruneUtils.peek();
	}

    public void addToLeafPruneUtils(long value) {
		if (leafPruneUtils.size() < TopK)
			leafPruneUtils.add(value);
		else if (value > leafPruneUtils.peek()) {
			leafPruneUtils.add(value);
			do {
				leafPruneUtils.poll();
			} while (leafPruneUtils.size() > TopK);
		}
	}

    public void setLeafMapSize() {
		for (Entry<Integer, Map<Integer, Long>> entry : mapLeafMAP.entrySet())
			leafMapSize += entry.getValue().keySet().size();
	}

    private void removeLeafEntry() {
		for (Entry<Integer, Map<Integer, Long>> entry : mapLeafMAP.entrySet()) {
			for (Iterator<Map.Entry<Integer, Long>> it = entry.getValue().entrySet().iterator(); it.hasNext();) {
				Map.Entry<Integer, Long> entry2 = it.next();
				it.remove();
			}
		}
	}

    private void thui(int[] prefix, int prefixLength, UtilityList pUL, List<UtilityList> ULs) throws IOException {

		for (int i = ULs.size() - 1; i >= 0; i--) {
			if (ULs.get(i).getUtils() >= minUtility)
				save(prefix, prefixLength, ULs.get(i));
		}

		for (int i = ULs.size() - 2; i >= 0; i--) {// last item is a single item, and hence no extension
			UtilityList X = ULs.get(i);
			if (X.sumIutils + X.sumRutils >= minUtility && X.sumIutils > 0) {// the utility value of zero cases can be
																				// safely ignored, as it is unlikely to
																				// generate a HUI; besides the lowest
																				// min utility will be 1
				if (EUCS_PRUNE) {
					Map<Integer, ItemTHUI> mapTWUF = mapFMAP.get(X.item);
					if (mapTWUF == null)
						continue;
				}

				List<UtilityList> exULs = new ArrayList<UtilityList>();
				for (int j = i + 1; j < ULs.size(); j++) {
					UtilityList Y = ULs.get(j);
					candidateCount++;
					UtilityList exul = construct(pUL, X, Y);
					if (exul != null)
						exULs.add(exul);

				}
				prefix[prefixLength] = X.item;
				thui(prefix, prefixLength + 1, X, exULs);
			}
		}
	}

    private void save(int[] prefix, int length, UtilityList X) {

		kPatterns.add(new PatternTHUI(prefix, length, X, candidateCount));
		if (kPatterns.size() > TopK) {
			if (X.getUtils() >= minUtility) {
				do {
					kPatterns.poll();
				} while (kPatterns.size() > TopK);
			}
			minUtility = kPatterns.peek().utility;
		}
	}

    private UtilityList construct(UtilityList P, UtilityList px, UtilityList py) {
		UtilityList pxyUL = new UtilityList(py.item);
		long totUtil = px.sumIutils + px.sumRutils;
		int ei = 0, ej = 0, Pi = -1;

		Element ex = null, ey = null, e = null;
		while (ei < px.elements.size() && ej < py.elements.size()) {
			if (px.elements.get(ei).tid > py.elements.get(ej).tid) {
				++ej;
				continue;
			} // px not present, py pres
			if (px.elements.get(ei).tid < py.elements.get(ej).tid) {// px present, py not present
				totUtil = totUtil - px.elements.get(ei).iutils - px.elements.get(ei).rutils;
				if (totUtil < minUtility) {
					return null;
				}
				++ei;
				++Pi;// if a parent is present, it should be as large or larger than px; besides the
						// ordering is by tid
				continue;
			}
			ex = px.elements.get(ei);
			ey = py.elements.get(ej);

			if (P == null) {
				pxyUL.addElement(new Element(ex.tid, ex.iutils + ey.iutils, ey.rutils));
			} else {
				while (Pi < P.elements.size() && P.elements.get(++Pi).tid < ex.tid)
					;
				e = P.elements.get(Pi);

				pxyUL.addElement(new Element(ex.tid, ex.iutils + ey.iutils - e.iutils, ey.rutils));
			}
			++ei;
			++ej;
		}
		while (ei < px.elements.size()) {
			totUtil = totUtil - px.elements.get(ei).iutils - px.elements.get(ei).rutils;
			if (totUtil < minUtility) {
				return null;
			}
			++ei;
		}
		return pxyUL;
	}

    public void writeResultTofile() throws IOException {

		if (kPatterns.size() == 0)
			return;
		List<PatternTHUI> lp = new ArrayList<PatternTHUI>();
		do {
			huiCount++;
			PatternTHUI pattern = kPatterns.poll();

			lp.add(pattern);
		} while (kPatterns.size() > 0);

		Collections.sort(lp, new Comparator<PatternTHUI>() {
			public int compare(PatternTHUI o1, PatternTHUI o2) {
				return comparePatterns(o1, o2);
				// return comparePatternsIdx(o1, o2);
			}
		});

		for (PatternTHUI pattern : lp) {
			StringBuilder buffer = new StringBuilder();

			buffer.append(pattern.prefix.toString());
			buffer.append(" #UTIL: ");
			// write support
			buffer.append(pattern.utility);

			writer.write(buffer.toString());
			writer.newLine();
		}
		writer.close();
	}

    private int comparePatterns(PatternTHUI item1, PatternTHUI item2) {
		// int compare = (int) (Integer.parseInt(item1.split(" ")[0]) -
		// Integer.parseInt(item2.split(" ")[0]));
		int i1 = (int) Integer.parseInt(item1.prefix.split(" ")[0]);
		int i2 = (int) Integer.parseInt(item2.prefix.split(" ")[0]);

		int compare = (int) (TWU_List.get(i1) - TWU_List.get(i2));
		return compare;
	}

    public void printStats() throws IOException {
		System.out.println(" High-utility itemsets count : " + huiCount + " Candidates " + candidateCount);
		System.out.println(" Final minimum utility : " + minUtility);
		File f = new File(inputFile);
		String tmp = f.getName();
		tmp = tmp.substring(0, tmp.lastIndexOf('.'));
		System.out.println(" Dataset : " + tmp);
	}

}
