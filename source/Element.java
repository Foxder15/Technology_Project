public class Element {
    //transaction id
	public final int tid ;   
	//itemset utility
	public final int iutils;   
	//remaining utility
	public int rutils; 

    public Element(int tid, int iutils, int rutils){
		this.tid = tid;
		this.iutils = iutils;
		this.rutils = rutils;
	}
}
