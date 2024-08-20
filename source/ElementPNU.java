public class ElementPNU extends Element{
    //the itemset utility for negative items
    public final int inutils;  

    public ElementPNU(int tid, int iutils, int inutils, int rutils){
		super(tid, iutils, rutils);
		this.inutils = inutils;
	}

}