import java.io.IOException;

public class TestTHUI {
    public static void main(String[] args) {
        String input = "database/mushroom_negative.txt";
        String output = "output.txt";

        THUI hui = new THUI();

        try {
            hui.run(input, output, false, 100);
            hui.printStats();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
}