package com.ternaryop.photoshelf.test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;

import com.ternaryop.photoshelf.parsers.JSONTitleParserConfig;
import com.ternaryop.photoshelf.parsers.TitleData;
import com.ternaryop.photoshelf.parsers.TitleParser;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;


// To run this test be sure the Build Variants window is open then select Unit Tests
// see more http://developer.android.com/training/testing/unit-testing/local-unit-tests.html#run
@RunWith(Parameterized.class)
public class TitleParserFileTests {
    @Parameterized.Parameter(0)
    public String inputTitle;

    @Parameterized.Parameter(1)
    public String expectedTitle;

    @Parameterized.Parameters
    public static Collection<Object[]> data() throws IOException {
        BufferedReader inputReader = null;
        BufferedReader resultReader = null;
        ArrayList<Object[]> objects = new ArrayList<Object[]>();

        try {
            inputReader = new BufferedReader(new InputStreamReader(new FileInputStream("/opt/devel/0dafiprj/git.github/post_title_downloader/photoshelf_tests/titleParser-test-input.txt"), "UTF-8"));
            resultReader = new BufferedReader(new InputStreamReader(new FileInputStream("/opt/devel/0dafiprj/git.github/post_title_downloader/photoshelf_tests/titleParser-test-results.txt"), "UTF-8"));
            String year = String.valueOf(Calendar.getInstance().get(Calendar.YEAR));

            String inputLine;
            String resultLine;
            while ((inputLine = inputReader.readLine()) != null && (resultLine = resultReader.readLine()) != null) {
                resultLine = resultLine.replace("%CURRENT_YEAR%", year);
                objects.add(new Object[] {inputLine, resultLine});
            }
        } finally {
            if (inputReader != null) try { inputReader.close(); } catch (IOException ignored) {}
            if (resultReader != null) try { resultReader.close(); } catch (IOException ignored) {}
        }
        return objects;
    }

    @Test
    public void testTitle() {
        try {
            String configPath = new File(new File(".").getAbsoluteFile(), "app/src/main/assets/titleParser.json").getAbsolutePath();
            TitleData titleData = TitleParser.instance(new JSONTitleParserConfig(configPath)).parseTitle(inputTitle, false, false);

            String formattedInput = titleData.format("<strong>", "</strong>", "<em>", "</em>");
            if (!expectedTitle.equals(formattedInput)) {
                System.out.println("\"" + inputTitle + "\",");
                System.out.println("//expected " + expectedTitle);
                System.out.println("//found    " + formattedInput);
                System.out.println();
            }
            assertEquals(expectedTitle, formattedInput);
        } catch (Exception e) {
            e.printStackTrace();
            assertFalse(e.getMessage(), true);
        }
    }
}
