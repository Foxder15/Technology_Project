import java.io.Serializable;

public class ItemTHUI implements Serializable{
	long twu = 0L;
	int utility = 0;
	 
	public String toString(){
		return String.valueOf(utility);
	}
}