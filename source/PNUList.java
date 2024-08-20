import java.util.ArrayList;
import java.util.List;

public class PNUList extends UtilityList{
    long  sumINutils = 0;
    public List<ElementPNU> elements = new ArrayList<>();

    public PNUList(Integer item ) {
		super(item);
	}

    public void addElement(ElementPNU element){
		sumIutils += element.iutils;
		sumRutils += element.rutils;
		sumINutils += element.inutils;
		elements.add(element);
	}

}
