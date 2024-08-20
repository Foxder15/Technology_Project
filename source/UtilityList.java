import java.util.ArrayList;
import java.util.List;

public class UtilityList {
    //the item
    public Integer item;
    // the sum of item utilities
    public long sumIutils = 0;  
    // the sum of remaining utilities
    public long sumRutils = 0;  
    // the elements
    public List<Element> elements = new ArrayList<Element>();  

    public UtilityList(Integer item) {
        this.item = item;
    }


    public void addElement(Element element) {
        sumIutils += element.iutils;
        sumRutils += element.rutils;
        elements.add(element);
    }

    public int getSupport() {
        return elements.size();
    }

    public long getUtils(){
        return this.sumIutils;
    }
}
