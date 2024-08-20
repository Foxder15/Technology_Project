public class UtilityItem {
    int item = 0;
    int utility = 0;

    UtilityItem(){}

    UtilityItem (int item, int utility) {
        this.item = item;
        this.utility = utility;
    }

    @Override
    public String toString() {
        return "[" + item + "," + utility + "]";
    }
}

