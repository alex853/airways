/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.worldbuilder.tools;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.ArrayList;

import net.simforge.airways.Airways;
import net.simforge.airways.model.geo.City;
import net.simforge.airways.model.geo.Country;
import net.simforge.commons.io.Csv;
import net.simforge.commons.io.IOHelper;
import net.simforge.commons.legacy.html.Html;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TageoComToCityPopulation {
    private static final Logger logger = LoggerFactory.getLogger(TageoComToCityPopulation.class.getName());

    private static final String LOCAL_ROOT = "./data/tageo.com/";
    private static final String TAGEO_ROOT = "http://www.tageo.com/";
    private static final int DEPTH = 4;

    public static void main(String[] args) throws IOException, SQLException {
        logger.info("Importing Tageo.com data");

        Csv csv = Csv.empty();
        csv.addColumn("CountryName");
        csv.addColumn("CountryCode");
        csv.addColumn("CityName");
        csv.addColumn("CityPopulation");
        csv.addColumn("CityLatitude");
        csv.addColumn("CityLongitude");

        String countriesStr = IOHelper.loadFile(new File(LOCAL_ROOT + "_countries.txt"));
        String[] countriesStrs = countriesStr.split(";");

        for (int i = 0; i < countriesStrs.length / 2; i++) {
            String countryName = countriesStrs[i * 2 + 1];
            String countryUrl = countriesStrs[i * 2];

            logger.info("Processing '{}' using {} URL", countryName, countryUrl);

//                downloadCountry(countryUrl);
            importCountry(countryName, countryUrl, csv);
        }

        IOHelper.saveFile(new File("./data/city-population.csv"), csv.getContent());
    }

    private static void importCountry(String countryName, String countryUrl, Csv csv) throws SQLException, IOException {
        countryName = mask(countryName);

        logger.info("Importing data for '{}'", countryName);

        String countryContent = getContent(countryUrl);
        String citiesUrl = getCitiesUrl(countryContent);

        int idx = citiesUrl.lastIndexOf(".htm");
        String code = citiesUrl.substring(idx - 2, idx);

        Country country = new Country();
        country.setName(countryName);
        country.setCode(code);

        logger.info("Country {} imported", countryName);

        List<String> dataUrls = new ArrayList<>();
        dataUrls.add(citiesUrl);
        for (int i = 1; i <= DEPTH; i++) {
            String stepUrl = citiesUrl.replace(".", "-step-" + i + ".");
            dataUrls.add(stepUrl);
        }

        for (String dataUrl : dataUrls) {
            String data = getContent(dataUrl);
            if (data == null) {
                continue;
            }
            String str = Html.toPlainText(data);

            int index = str.indexOf("Rank;City;Population (2000);Latitude (DD);Longitude (DD)");
            str = str.substring(index);
            String[] strs = str.split("\r\n");

            int strIndex = 1;
            while (true) {
                str = strs[strIndex];
                String[] cityStrs = str.split(";");

                if (cityStrs.length != 5) {
                    break;
                }

                strIndex++;

                String cityName = mask(cityStrs[1]);
                int population;
                double lat;
                double lon;
                try {
                    population = Integer.valueOf(cityStrs[2]);
                    lat = Double.valueOf(cityStrs[3]);
                    lon = Double.valueOf(cityStrs[4]);
                } catch (Exception e) {
                    continue;
                }

                City city = new City();
                city.setCountry(country);
                city.setName(cityName);
                city.setPopulation(population);
                city.setLatitude(lat);
                city.setLongitude(lon);
                city.setDataset(Airways.TAGEO_COM_DATASET);

                logger.info("    City {} with population {} imported", cityName, population);

                int row = csv.addRow();
                csv.set(row, "CountryName", country.getName());
                csv.set(row, "CountryCode", country.getCode());
                csv.set(row, "CityName", city.getName());
                csv.set(row, "CityPopulation", String.valueOf(city.getPopulation()));
                csv.set(row, "CityLatitude", String.valueOf(city.getLatitude()));
                csv.set(row, "CityLongitude", String.valueOf(city.getLongitude()));
            }
        }
    }

    private static void downloadCountry(String countryPage) throws IOException {
        String content = getContent(countryPage);
        String citiesUrl = getCitiesUrl(content);
        getContent(citiesUrl);
        for (int i = 1; i <= DEPTH; i++) {
            String stepUrl = citiesUrl.replace(".", "-step-" + i + ".");
            try {
                getContent(stepUrl);
            } catch (IOException e) {
                break;
            }
        }
    }

/*    private static void sleep() {
        try {
            double seconds = Math.random() * 30 + 30;
            System.out.println(new DateTime() + " Sleep for " + (int) seconds);
            Thread.sleep((long) (1000 * seconds));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }*/

    private static String getContent(String page) throws IOException {
        File file = new File(LOCAL_ROOT + page);
        if (file.exists()) {
            return IOHelper.loadFile(file);
        }

        return null;
//        sleep();
//
//        System.out.println(new DateTime() +  " Downloading " + page);
//        String url = TAGEO_ROOT + page;
//        String content = IOHelper.download(url);
//        IOHelper.saveFile(file, content);
//        return content;
    }

    private static String getCitiesUrl(String content) {
        int index = content.indexOf("City & Town Population");
        if (index != -1) {
            content = content.substring(0, index);
            index = content.lastIndexOf("index-");
            if (index != -1) {
                //noinspection RedundantStringConstructorCall
                content = new String(content.substring(index, content.lastIndexOf('\'')));
                return content;
            }
        }
        return null;
    }

    private static String mask(String name) {
        boolean changed = false;

        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                buf.append(c);
            } else if (c >= 'a' && c <= 'z') {
                buf.append(c);
            } else if (c == '-' || c == ' ' || c == '\'') {
                buf.append(c);
            } else {
                buf.append('#');
                changed = true;
            }
        }

        String newName = buf.toString();

        if (changed) {
            logger.warn("  {} -> {}", name, newName);
        }

        return newName;
    }
}
