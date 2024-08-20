import java.io.IOException;

public class TestTopHUI {
    public static void main(String[] args) {
        String input = "database/mushroom_negative.txt";
        String output = "output.txt";

        TopHUI topHUI = new TopHUI();

        try {
            topHUI.run(input, output, 100);
            topHUI.printStats();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
