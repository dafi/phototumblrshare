package com.ternaryop.phototumblrshare.parsers;


import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TitleParser {
    private static Pattern titleRE = Pattern.compile("^(.*?)\\s([-\u2013|~@]|attends|arrives)");

    private static String[] months = {"", "January",
                  "February",
                  "March",
                  "April",
                  "May",
                  "June",
                  "July",
                  "August",
                  "September",
                  "October",
                  "November",
                  "December"};
    private static HashMap<String, String> monthsShort = new HashMap<String, String>();
    static {
        monthsShort.put("jan", "January");
        monthsShort.put("feb", "February");
        monthsShort.put("mar", "March");
        monthsShort.put("apr", "April");
        monthsShort.put("may", "May");
        monthsShort.put("jun", "June");
        monthsShort.put("jul", "July");
        monthsShort.put("aug", "August");
        monthsShort.put("sep", "September");
        monthsShort.put("oct", "October");
        monthsShort.put("nov", "November");
        monthsShort.put("dec", "December");
    }

    private static HashMap<String, String> cities = new HashMap<String, String>();

	private static TitleParser instance = new TitleParser();
    
    static {
        cities.put("LA", "Los Angeles");
        cities.put("L.A", "Los Angeles");
        cities.put("L.A.", "Los Angeles");
        cities.put("NY", "New York");
        cities.put("N.Y.", "New York");
        cities.put("NYC", "New York City");
    }

    /**
     * Fill parseInfo with day, month, year, matched
     */
    protected Map<String, Object> parseDate(String title) {
    	int day = 0;
    	String month = "";
    	int year = 0;
    	String yearStr = "";

        // handle dates in the form Jan 10, 2010 or January 10 2010 or Jan 15
        Matcher m = Pattern.compile("[-,]\\s+\\(?(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[^0-9]*([0-9]*)[^0-9]*([0-9]*)\\)?.*$", Pattern.CASE_INSENSITIVE).matcher(title);
        if (m.find() && m.groupCount() > 1) {
            day = m.group(2).length() != 0 ? Integer.parseInt(m.group(2)) : 0;
            month = monthsShort.get(m.group(1).toLowerCase(Locale.getDefault()));
            if (m.groupCount() == 4 && m.group(3).length() > 0) {
                year = Integer.parseInt(m.group(3));
                yearStr = m.group(3);
            } else {
                year = Calendar.getInstance().get(Calendar.YEAR);
                yearStr = "" + year;
            }
        } else {
            // handle dates in the form dd/dd/dd?? or (dd/dd/??)
            m = Pattern.compile("\\(?([0-9]{2}).([0-9]{1,2}).([0-9]{2,4})\\)?").matcher(title);
            if (m.find() && m.groupCount() > 1) {
                day = Integer.parseInt(m.group(1));
                int monthInt = Integer.parseInt(m.group(2));
                year = Integer.parseInt(m.group(3));
                yearStr = m.group(3);
                if (monthInt > 12) {
                    int tmp = monthInt;
                    monthInt = day;
                    day = tmp;
                }
                month = months[monthInt];
            } else {
            	m = null;
            }
        }
        // day can be not present for example "New York City, January 11"
        HashMap<String, Object> dateComponents = new HashMap<String, Object>();
        dateComponents.put("day",  day);
        dateComponents.put("month", month);
        dateComponents.put("year", year < 2000 ? "20" + yearStr : yearStr);
        if (m != null) {
        	dateComponents.put("matched", m);
        }
        
        return dateComponents;
    }

    private TitleParser() {
    	
    }

    public TitleData parseTitle(String title) {
        TitleData titleData = new TitleData();

        title = title.replaceAll("\u2013", "-")
                    .replaceAll("\u2018", "'")
                    .replaceAll("\u2019", "'")
                    .replaceAll("\u201C", "\"")
                    .replaceAll("\u201D", "\"")
                    .replaceAll("�", "i");

        Matcher m = titleRE.matcher(title);
        int start = 0;
        if (m.find() && m.groupCount() > 1) {
          titleData.who = m.group(1);
          start = m.regionStart() + m.group(0).length();
        }
        Map<String, Object> dateComponents = parseDate(title);
        Matcher dateMatcher = (Matcher) dateComponents.get("matched");
        String loc = dateMatcher != null ? title.substring(start, dateMatcher.start()) : title.substring(start);
        // city names can be multi words so allow whitespaces
        m = Pattern.compile("\\s*(.*?)\\s+in\\s+([a-z. ]*)", Pattern.CASE_INSENSITIVE).matcher(loc);
        if (m.find() && m.groupCount() > 1) {
            titleData.location = m.group(1);
            String city = m.group(2).trim();
            titleData.city = cities.get(city.toUpperCase(Locale.getDefault()));
            if (titleData.city == null) {
                titleData.city = city;
            }
        } else {
            titleData.location = loc;
        }
        titleData.location = titleData.location.replaceAll("[^a-z]*$", "");

        String when = "";
        if ((Integer)dateComponents.get("day") != 0) {
            when = dateComponents.get("day") + " ";
        };
        when += dateComponents.get("month") + ", " + dateComponents.get("year");

        titleData.who = titleData.who.trim();
        titleData.location = titleData.location.trim();
        titleData.city = titleData.city.trim();
        titleData.when = when.trim();
        titleData.tags = titleData.who + ", " + titleData.location.trim()
        		.replace("[0-9]*(st|nd|rd|th)?", "")
        		.replaceAll("\"|'", "");

        return titleData;
    }

    public static TitleParser instance() {
    	return instance;
    }
}
