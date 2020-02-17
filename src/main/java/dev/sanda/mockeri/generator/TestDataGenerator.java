package dev.sanda.mockeri.generator;

import com.maximeroussy.invitrode.WordGenerator;
import org.apache.commons.lang.RandomStringUtils;
import dev.sanda.mockeri.meta.CollectionInstantiator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static dev.sanda.datafi.reflection.CachedEntityTypeInfo.genDefaultInstance;
import static dev.sanda.mockeri.StaticUtils.*;

@Component
@lombok.Getter
@SuppressWarnings("unchecked")
public class TestDataGenerator {

    private List<String> firstNames = new ArrayList<>();
    private List<String> lastNames = new ArrayList<>();
    private List<String> companies = new ArrayList<>();
    private List<String> addresses = new ArrayList<>();
    private List<String> cities = new ArrayList<>();
    private List<String> stateOrProvinces = new ArrayList<>();
    private List<String> countries = new ArrayList<>();
    private List<String> zipCodes = new ArrayList<>();
    private List<String> phone1s = new ArrayList<>();
    private List<String> phone2s = new ArrayList<>();
    private List<String> emails = new ArrayList<>();
    private List<String> websites = new ArrayList<>();
    private List<String> paymentTypes = new ArrayList<>(Arrays.asList("Credit card", "Cash", "ACH"));

    public LocalDate pastDate(){
        LocalDate start = LocalDate.of(1970, Month.JANUARY, 1);
        long days = ChronoUnit.DAYS.between(start, LocalDate.now());
        return start.plusDays(new Random().nextInt((int) days + 1));
    }
    public LocalDate futureDate(){
        LocalDate start = LocalDate.now();
        long days = ChronoUnit.DAYS.between(start, LocalDateTime.from(LocalDate.of(2026, Month.JANUARY, 1)));
        return start.plusDays(new Random().nextInt((int) days + 1));
    }
    public String dummyParagraph(){
        StringBuilder dummyParagraph = new StringBuilder();
        WordGenerator wordGenerator = new WordGenerator();
        int lengthOfWord;
        for (int i = 0; i < ThreadLocalRandom.current().nextInt(20, 30); i++) {
            lengthOfWord = ThreadLocalRandom.current().nextInt(3, 10);
            dummyParagraph.append(wordGenerator.newWord(lengthOfWord).toLowerCase()).append(" ");
        }
        return  dummyParagraph.toString();
    }
    public String dummySentence(){
        StringBuilder dummySentence = new StringBuilder();
        WordGenerator wordGenerator = new WordGenerator();
        int lengthOfWord;
        for (int i = 0; i < ThreadLocalRandom.current().nextInt(7, 20); i++) {
            lengthOfWord = ThreadLocalRandom.current().nextInt(3, 10);
            dummySentence.append(wordGenerator.newWord(lengthOfWord).toLowerCase()).append(" ");
        }
        return  dummySentence.toString();
    }
    public String password(){return randomString(16);}
    public Double aDouble(double min, double max){
        return min + (max - min) * new Random().nextDouble();
    }
    public boolean aBoolean(){
        return ThreadLocalRandom.current().nextBoolean();
    }
    @PostConstruct
    private void parseStaticData() {
        BufferedReader br = null;
        String line = "";
        try {
            br = getBufferedReaderFor("testDataSource.csv", resourceLoader);
            br.readLine();//skip first line of csv
            while ((line = br.readLine()) != null) {
                String[] currentLineData = line.split(",");
                removeDoubleQuotes(currentLineData);
                firstNames.add(currentLineData[0]);
                lastNames.add(currentLineData[1]);
                addresses.add(currentLineData[3]);
                cities.add(currentLineData[4]);
                stateOrProvinces.add(currentLineData[6]);
                zipCodes.add(currentLineData[7]);
                phone1s.add(currentLineData[8]);
                phone2s.add(currentLineData[9]);
                emails.add(currentLineData[10]);
                String websiteUrl = currentLineData[11];
                if(!websiteUrl.contains("@"))
                    websites.add(websiteUrl);
            }
            br = getBufferedReaderFor("countries.txt", resourceLoader);
            line = "";
            while ((line = br.readLine()) != null) countries.add(line);
            br = getBufferedReaderFor("companyNames.txt", resourceLoader);
            line = "";
            while ((line = br.readLine()) != null) companies.add(line);

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        int i = 9;
    }
    private void removeDoubleQuotes(String[] currentLineData) {
        for (int i = 0; i < currentLineData.length; i++) {
            currentLineData[i] = currentLineData[i].replaceAll("\"", "");
        }
    }
    public static<T> T randomFrom(List<T> aList) {
        return aList.get(ThreadLocalRandom.current().nextInt(aList.size()));
    }

    public static<T> Collection<T> randomCollectionFrom(List<T> aList){
        int resultSetSize = ThreadLocalRandom.current().nextInt(aList.size());
        Collection<T> resultSet = new HashSet<>();
        for (int i = 0; i < resultSetSize; i++) {
            T toAdd = randomFrom(aList);
            if(!resultSet.contains(toAdd))
                resultSet.add(toAdd);
        }
        return resultSet;
    }

    public Integer anInteger(int min, int max) {
        return ThreadLocalRandom.current().nextInt(min, max);
    }
    public Long aLong(long min, long max) {
        return ThreadLocalRandom.current().nextLong(min, max);
    }

    public LocalDateTime aLocalDateTime() {
        LocalDateTime now = LocalDateTime.now();
        int year = 60 * 60 * 24 * 365;
        int numYears = ThreadLocalRandom.current().nextInt(-10, 15);
        return now.plusSeconds((long) numYears * year);
    }
    private LocalDate aLocalDate(LocalDate start, LocalDate end) {
        long days = ChronoUnit.DAYS.between(start, end);
        return start
                .plusDays(ThreadLocalRandom
                        .current()
                        .nextInt((int) days + 1));
    }

    public LocalDate aLocalDate() {
        return aLocalDate(LocalDate.now().minusYears(20), LocalDate.now().plusYears(20));
    }

    public LocalDate aFutureLocalDate() {
        return aLocalDate(LocalDate.now(), LocalDate.now().plusYears(20));
    }
    public LocalDate aPastLocalDate() {
        return aLocalDate(LocalDate.now(), LocalDate.now().minusYears(20));
    }

    public String generateSecurePassword() {
        String upperCaseLetters = RandomStringUtils.random(2, 65, 90, true, true);
        String lowerCaseLetters = RandomStringUtils.random(2, 97, 122, true, true);
        String numbers = RandomStringUtils.randomNumeric(2);
        String specialChar = RandomStringUtils.random(2, 33, 47, false, false);
        String totalChars = RandomStringUtils.randomAlphanumeric(2);
        String combinedChars = upperCaseLetters.concat(lowerCaseLetters)
                .concat(numbers)
                .concat(specialChar)
                .concat(totalChars);
        List<Character> pwdChars = combinedChars.chars()
                .mapToObj(c -> (char) c)
                .collect(Collectors.toList());
        Collections.shuffle(pwdChars);
        return pwdChars.stream()
                .collect(StringBuilder::new, StringBuilder::append, StringBuilder::append)
                .toString();
    }

    public double aDouble() {
        return new Random().nextDouble();
    }

    @Autowired
    private ResourceLoader resourceLoader;
    @Autowired
    private CollectionInstantiator collectionInstantiator;

    public Collection<String> collectionOfStrings(Class<?> collectionType) {
        Collection<String> collection = collectionInstantiator.instantiateCollection(collectionType, String.class);
        for (int i = 0; i < 20; i++) collection.add(dummySentence());
        return collection;
    }

    public Collection<Double> collectionOfDoubles(Class<?> collectionType) {
        Collection<Double> collection = collectionInstantiator.instantiateCollection(collectionType, Double.class);
        for (int i = 0; i < 20; i++) collection.add(aDouble());
        return collection;
    }

    public Collection<Long> collectionOfLongs(Class<?> collectionType) {
        Collection<Long> collection = collectionInstantiator.instantiateCollection(collectionType, Long.class);
        for (int i = 0; i < 20; i++) collection.add(ThreadLocalRandom.current().nextLong());
        return collection;
    }

    public Collection<Integer> collectionOfIntegers(Class<?> collectionType) {
        Collection<Integer> collection = collectionInstantiator.instantiateCollection(collectionType, Integer.class);
        for (int i = 0; i < 20; i++) collection.add(ThreadLocalRandom.current().nextInt());
        return collection;
    }

    public Collection<Boolean> collectionOfBooleans(Class<?> collectionType) {
        Collection<Boolean> collection = collectionInstantiator.instantiateCollection(collectionType, Boolean.class);
        for (int i = 0; i < 20; i++) collection.add(ThreadLocalRandom.current().nextBoolean());
        return collection;
    }

    public Collection<LocalDateTime> collectionOfLocalDateTimes(Class<?> collectionType) {
        Collection<LocalDateTime> collection = collectionInstantiator.instantiateCollection(collectionType, LocalDateTime.class);
        for (int i = 0; i < 20; i++) collection.add(aLocalDateTime());
        return collection;
    }

    public Collection<LocalDate> collectionOfLocalDates(Class<?> collectionType) {
        Collection<LocalDate> collection = collectionInstantiator.instantiateCollection(collectionType, LocalDate.class);
        for (int i = 0; i < 20; i++) collection.add(aLocalDate());
        return collection;
    }

    public Collection<String> collectionOfUrls(Class<?> collectionType) {
        Collection<String> collection = collectionInstantiator.instantiateCollection(collectionType, String.class);
        for (int i = 0; i < 20; i++) collection.add(randomFrom(websites));
        return collection;
    }
}
